package com.example.gettingrichapp.camera

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockFrameProvider : FrameProvider {

    private val _streamState = MutableStateFlow(StreamState.IDLE)
    override val streamState: StateFlow<StreamState> = _streamState

    override suspend fun startStream() {
        _streamState.value = StreamState.STREAMING
    }

    override suspend fun captureFrame(): FrameData? {
        if (_streamState.value != StreamState.STREAMING) return null
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return FrameData(
            bitmap = bitmap,
            timestampMs = System.currentTimeMillis(),
            width = 1,
            height = 1
        )
    }

    override suspend fun stopStream() {
        _streamState.value = StreamState.IDLE
    }
}
