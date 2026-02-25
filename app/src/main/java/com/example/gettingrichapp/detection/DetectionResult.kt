package com.example.gettingrichapp.detection

import android.graphics.RectF
import com.example.gettingrichapp.model.Card

data class DetectedCard(
    val card: Card,
    val confidence: Float,
    val boundingBox: RectF
)

data class DetectionResult(
    val cards: List<DetectedCard>,
    val processingTimeMs: Long = 0
)
