package com.example.gettingrichapp.glasses

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface GlassesConnection {
    val connectionState: StateFlow<ConnectionState>
    suspend fun initialize()
    fun startRegistration(activity: Activity)
    fun startUnregistration(activity: Activity)
    suspend fun hasCameraPermission(): Boolean
    fun release()
}
