package com.example.gettingrichapp.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MockFrameProvider : FrameProvider {

    private val _streamState = MutableStateFlow(StreamState.IDLE)
    override val streamState: StateFlow<StreamState> = _streamState

    private val _frameFlow = MutableSharedFlow<FrameData>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val frameFlow: SharedFlow<FrameData> = _frameFlow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var emitJob: Job? = null

    override suspend fun startStream() {
        _streamState.value = StreamState.STREAMING
        emitJob = scope.launch {
            while (true) {
                val bitmap = createMockBitmap()
                val frameData = FrameData(
                    bitmap = bitmap,
                    timestampMs = System.currentTimeMillis(),
                    width = MOCK_WIDTH,
                    height = MOCK_HEIGHT
                )
                _frameFlow.tryEmit(frameData)
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    override suspend fun captureFrame(): FrameData? {
        if (_streamState.value != StreamState.STREAMING) return null
        val bitmap = createMockBitmap()
        return FrameData(
            bitmap = bitmap,
            timestampMs = System.currentTimeMillis(),
            width = MOCK_WIDTH,
            height = MOCK_HEIGHT
        )
    }

    override suspend fun stopStream() {
        emitJob?.cancel()
        emitJob = null
        _streamState.value = StreamState.IDLE
    }

    private fun createMockBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(MOCK_WIDTH, MOCK_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "Mock Camera",
            MOCK_WIDTH / 2f,
            MOCK_HEIGHT / 2f,
            paint
        )
        return bitmap
    }

    companion object {
        private const val MOCK_WIDTH = 504
        private const val MOCK_HEIGHT = 896
        private const val FRAME_INTERVAL_MS = 66L // ~15 FPS
    }
}
