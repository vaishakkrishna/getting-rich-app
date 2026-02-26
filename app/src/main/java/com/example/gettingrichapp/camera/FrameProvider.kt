package com.example.gettingrichapp.camera

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class StreamState {
    IDLE, STREAMING, ERROR
}

interface FrameProvider {
    val streamState: StateFlow<StreamState>
    val frameFlow: SharedFlow<FrameData>
    suspend fun startStream()
    suspend fun captureFrame(): FrameData?
    suspend fun stopStream()
}
