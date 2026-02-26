package com.example.gettingrichapp.camera

import android.content.Context
import android.util.Log
import com.example.gettingrichapp.detection.YuvToRgbConverter
import com.example.gettingrichapp.glasses.GlassesConnection
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

class DatFrameProvider(
    private val context: Context,
    private val glassesConnection: GlassesConnection
) : FrameProvider {

    private val _streamState = MutableStateFlow(StreamState.IDLE)
    override val streamState: StateFlow<StreamState> = _streamState

    private val _frameFlow = MutableSharedFlow<FrameData>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val frameFlow: SharedFlow<FrameData> = _frameFlow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var streamSession: StreamSession? = null
    private var collectionJob: Job? = null
    private var stateObserverJob: Job? = null
    private val latestFrame = AtomicReference<FrameData?>(null)

    override suspend fun startStream() {
        if (_streamState.value == StreamState.STREAMING) return

        try {
            val config = StreamConfiguration(
                videoQuality = VideoQuality.MEDIUM,
                frameRate = 15
            )

            Log.d(TAG, "Creating stream session...")
            val session = Wearables.startStreamSession(
                context = context,
                deviceSelector = AutoDeviceSelector(),
                streamConfiguration = config
            )
            streamSession = session
            Log.d(TAG, "Stream session created, initial state: ${session.state.value}")

            // Observe SDK session state for diagnostics and to track when
            // the session actually reaches STREAMING
            stateObserverJob = scope.launch {
                session.state.collect { sdkState ->
                    Log.d(TAG, "SDK session state: $sdkState")
                    if (sdkState == StreamSessionState.STOPPED ||
                        sdkState == StreamSessionState.CLOSED
                    ) {
                        _streamState.value = StreamState.IDLE
                    }
                }
            }

            // Start collecting video frames. The SDK's internal SharedFlow uses
            // replay=0, so we must subscribe before frames start arriving.
            // Frames won't flow until the SDK session reaches STREAMING state.
            collectionJob = scope.launch {
                session.videoStream.collect { videoFrame ->
                    try {
                        val bitmap = YuvToRgbConverter.i420ToBitmap(
                            buffer = videoFrame.buffer,
                            width = videoFrame.width,
                            height = videoFrame.height
                        )
                        val frameData = FrameData(
                            bitmap = bitmap,
                            timestampMs = videoFrame.presentationTimeUs / 1000,
                            width = videoFrame.width,
                            height = videoFrame.height
                        )
                        latestFrame.set(frameData)
                        _frameFlow.tryEmit(frameData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame conversion failed: ${videoFrame.width}x${videoFrame.height}, " +
                                "buffer=${videoFrame.buffer.remaining()}/${videoFrame.buffer.capacity()}", e)
                    }
                }
            }

            // Wait for the SDK session to reach STREAMING state before we report
            // our own streamState as STREAMING. This ensures that the UI knows
            // the camera is truly active and frames are expected.
            Log.d(TAG, "Waiting for SDK session to reach STREAMING...")
            withTimeout(STREAM_START_TIMEOUT_MS) {
                session.state.first { it == StreamSessionState.STREAMING }
            }
            Log.d(TAG, "SDK session is STREAMING")

            _streamState.value = StreamState.STREAMING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start stream", e)
            _streamState.value = StreamState.ERROR
        }
    }

    override suspend fun captureFrame(): FrameData? {
        if (_streamState.value != StreamState.STREAMING) return null
        return latestFrame.get()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun stopStream() {
        collectionJob?.cancel()
        collectionJob = null
        stateObserverJob?.cancel()
        stateObserverJob = null

        streamSession?.close()
        streamSession = null

        latestFrame.set(null)
        _frameFlow.resetReplayCache()
        _streamState.value = StreamState.IDLE
    }

    companion object {
        private const val TAG = "DatFrameProvider"
        private const val STREAM_START_TIMEOUT_MS = 15_000L
    }
}
