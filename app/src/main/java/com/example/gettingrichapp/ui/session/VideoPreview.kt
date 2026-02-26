package com.example.gettingrichapp.ui.session

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import com.example.gettingrichapp.detection.DetectedCard
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.Suit

@Composable
fun VideoPreview(
    frame: Bitmap?,
    detections: List<DetectedCard>,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier
) {
    val aspectRatio = if (frameWidth > 0 && frameHeight > 0) {
        frameWidth.toFloat() / frameHeight.toFloat()
    } else {
        16f / 9f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        contentAlignment = Alignment.Center
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Camera preview",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )

            Canvas(modifier = Modifier.matchParentSize()) {
                val scaleX = size.width / frameWidth.toFloat()
                val scaleY = size.height / frameHeight.toFloat()

                val strokeColor = Color(0xFF4CAF50)
                val bgColor = Color(0xCC4CAF50)

                for (detection in detections) {
                    val box = detection.boundingBox
                    val left = box.left * scaleX
                    val top = box.top * scaleY
                    val right = box.right * scaleX
                    val bottom = box.bottom * scaleY

                    // Bounding box
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 3f)
                    )

                    // Label
                    val label = formatCardLabel(detection)
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 14f * density
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val textWidth = textPaint.measureText(label)
                    val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
                    val labelPadding = 4f * density

                    // Label background
                    drawRect(
                        color = bgColor,
                        topLeft = Offset(left, top - textHeight - labelPadding * 2),
                        size = Size(textWidth + labelPadding * 2, textHeight + labelPadding * 2)
                    )

                    // Label text
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        left + labelPadding,
                        top - labelPadding - textPaint.fontMetrics.descent,
                        textPaint
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Waiting for video...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun formatCardLabel(detection: DetectedCard): String {
    val card = detection.card
    val valueLabel = when (card.value) {
        CardValue.ACE -> "A"
        CardValue.KING -> "K"
        CardValue.QUEEN -> "Q"
        CardValue.JACK -> "J"
        CardValue.TEN -> "10"
        else -> card.value.numericValue.toString()
    }
    val suitSymbol = when (card.suit) {
        Suit.HEARTS -> "\u2665"
        Suit.DIAMONDS -> "\u2666"
        Suit.CLUBS -> "\u2663"
        Suit.SPADES -> "\u2660"
    }
    val confidence = (detection.confidence * 100).toInt()
    return "$valueLabel$suitSymbol $confidence%"
}
