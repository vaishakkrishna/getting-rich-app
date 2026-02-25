package com.example.gettingrichapp.detection

import android.graphics.Bitmap

interface CardDetector {
    fun initialize()
    suspend fun detect(frame: Bitmap, confidenceThreshold: Float = 0.80f): DetectionResult
    fun release()
}
