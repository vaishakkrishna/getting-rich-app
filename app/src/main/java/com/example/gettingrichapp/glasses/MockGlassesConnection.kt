package com.example.gettingrichapp.glasses

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockGlassesConnection : GlassesConnection {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    override suspend fun initialize() {
        _connectionState.value = ConnectionState.Connected
    }

    override fun startRegistration(activity: Activity) {
        _connectionState.value = ConnectionState.Connected
    }

    override fun startUnregistration(activity: Activity) {
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun hasCameraPermission(): Boolean = true

    override fun release() {
        _connectionState.value = ConnectionState.Disconnected
    }
}
