package com.example.gettingrichapp.detection

import android.graphics.Bitmap

class StubCardDetector(
    private val stubbedCards: List<DetectedCard> = emptyList()
) : CardDetector {

    override fun initialize() { /* no-op */ }

    override suspend fun detect(frame: Bitmap, confidenceThreshold: Float): DetectionResult {
        return DetectionResult(
            cards = stubbedCards.filter { it.confidence >= confidenceThreshold }
        )
    }

    override fun release() { /* no-op */ }
}
