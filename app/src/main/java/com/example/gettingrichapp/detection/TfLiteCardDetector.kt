package com.example.gettingrichapp.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.Suit
import org.tensorflow.lite.Interpreter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfLiteCardDetector(
    private val context: Context,
    private val modelFileName: String = "best_float16.tflite",
    private val iouThreshold: Float = 0.5f
) : CardDetector {

    private var interpreter: Interpreter? = null
    private val inferenceMutex = Mutex()

    override fun initialize() {
        val model = loadModelFile()
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(model, options)
    }

    override suspend fun detect(frame: Bitmap, confidenceThreshold: Float): DetectionResult {
        val interp = interpreter ?: return DetectionResult(emptyList())

        return inferenceMutex.withLock {
            val startTime = System.currentTimeMillis()

            // Resize to model input size
            val inputBitmap = Bitmap.createScaledBitmap(frame, INPUT_SIZE, INPUT_SIZE, true)

            // Prepare input buffer: [1, 640, 640, 3] float32
            val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            bitmapToFloatBuffer(inputBitmap, inputBuffer)

            if (inputBitmap != frame) {
                inputBitmap.recycle()
            }

            // Prepare output buffer: [1, 56, 8400] float32
            // Layout is features-first: row 0-3 = bbox (cx,cy,w,h), rows 4-55 = class scores
            val output = Array(1) { Array(NUM_OUTPUTS_PER_DETECTION) { FloatArray(NUM_DETECTIONS) } }

            // Run inference
            interp.run(inputBuffer, output)

            val rawOutput = output[0] // [56][8400]

            // Parse detections and apply NMS
            val detectedCards = parseDetections(
                rawOutput,
                confidenceThreshold,
                frame.width,
                frame.height
            )

            val processingTime = System.currentTimeMillis() - startTime
            DetectionResult(cards = detectedCards, processingTimeMs = processingTime)
        }
    }

    override fun release() {
        interpreter?.close()
        interpreter = null
    }

    private fun parseDetections(
        rawOutput: Array<FloatArray>,
        confidenceThreshold: Float,
        originalWidth: Int,
        originalHeight: Int
    ): List<DetectedCard> {
        val candidates = mutableListOf<DetectionCandidate>()

        // rawOutput layout: [56][8400] — features-first
        // Row 0 = cx, Row 1 = cy, Row 2 = w, Row 3 = h (all normalized 0-1)
        // Rows 4..55 = class scores (52 classes, already sigmoid-activated)

        for (i in 0 until NUM_DETECTIONS) {
            // Find best class score for this detection
            var maxClassScore = 0f
            var maxClassIdx = 0
            for (c in 0 until NUM_CLASSES) {
                val score = rawOutput[4 + c][i]
                if (score > maxClassScore) {
                    maxClassScore = score
                    maxClassIdx = c
                }
            }

            if (maxClassScore < confidenceThreshold) continue

            // Bbox values are normalized (0-1 range). Scale to original image dimensions.
            val cx = rawOutput[0][i]
            val cy = rawOutput[1][i]
            val w = rawOutput[2][i]
            val h = rawOutput[3][i]

            val left = (cx - w / 2f) * originalWidth
            val top = (cy - h / 2f) * originalHeight
            val right = (cx + w / 2f) * originalWidth
            val bottom = (cy + h / 2f) * originalHeight

            val box = RectF(
                left.coerceIn(0f, originalWidth.toFloat()),
                top.coerceIn(0f, originalHeight.toFloat()),
                right.coerceIn(0f, originalWidth.toFloat()),
                bottom.coerceIn(0f, originalHeight.toFloat())
            )

            val card = classIndexToCard(maxClassIdx) ?: continue
            candidates.add(DetectionCandidate(card, maxClassScore, box))
        }

        // Apply Non-Maximum Suppression
        val nmsResult = applyNms(candidates, iouThreshold)

        return nmsResult.map { DetectedCard(it.card, it.confidence, it.box) }
    }

    private fun applyNms(
        candidates: List<DetectionCandidate>,
        iouThreshold: Float
    ): List<DetectionCandidate> {
        val sorted = candidates.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectionCandidate>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)
            sorted.removeAll { other ->
                computeIoU(best.box, other.box) > iouThreshold
            }
        }

        return selected
    }

    private fun computeIoU(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        if (interArea == 0f) return 0f

        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = aArea + bArea - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // Normalize to 0..1 range, RGB order
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
            buffer.putFloat((pixel and 0xFF) / 255f)           // B
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFd = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    private data class DetectionCandidate(
        val card: Card,
        val confidence: Float,
        val box: RectF
    )

    companion object {
        private const val INPUT_SIZE = 640
        private const val NUM_DETECTIONS = 8400
        private const val NUM_CLASSES = 52
        private const val NUM_OUTPUTS_PER_DETECTION = 4 + NUM_CLASSES // 56

        // Label ordering from labels.txt (alphabetical):
        // 10C, 10D, 10H, 10S, 2C, 2D, 2H, 2S, 3C, 3D, 3H, 3S,
        // 4C, 4D, 4H, 4S, 5C, 5D, 5H, 5S, 6C, 6D, 6H, 6S,
        // 7C, 7D, 7H, 7S, 8C, 8D, 8H, 8S, 9C, 9D, 9H, 9S,
        // AC, AD, AH, AS, JC, JD, JH, JS, KC, KD, KH, KS, QC, QD, QH, QS
        private val LABELS = arrayOf(
            "10C", "10D", "10H", "10S",
            "2C", "2D", "2H", "2S",
            "3C", "3D", "3H", "3S",
            "4C", "4D", "4H", "4S",
            "5C", "5D", "5H", "5S",
            "6C", "6D", "6H", "6S",
            "7C", "7D", "7H", "7S",
            "8C", "8D", "8H", "8S",
            "9C", "9D", "9H", "9S",
            "AC", "AD", "AH", "AS",
            "JC", "JD", "JH", "JS",
            "KC", "KD", "KH", "KS",
            "QC", "QD", "QH", "QS"
        )

        private val VALUE_MAP = mapOf(
            "2" to CardValue.TWO,
            "3" to CardValue.THREE,
            "4" to CardValue.FOUR,
            "5" to CardValue.FIVE,
            "6" to CardValue.SIX,
            "7" to CardValue.SEVEN,
            "8" to CardValue.EIGHT,
            "9" to CardValue.NINE,
            "10" to CardValue.TEN,
            "J" to CardValue.JACK,
            "Q" to CardValue.QUEEN,
            "K" to CardValue.KING,
            "A" to CardValue.ACE
        )

        private val SUIT_MAP = mapOf(
            'C' to Suit.CLUBS,
            'D' to Suit.DIAMONDS,
            'H' to Suit.HEARTS,
            'S' to Suit.SPADES
        )

        fun classIndexToCard(index: Int): Card? {
            if (index < 0 || index >= LABELS.size) return null
            val label = LABELS[index]
            val suitChar = label.last()
            val valuePart = label.dropLast(1)
            val cardValue = VALUE_MAP[valuePart] ?: return null
            val suit = SUIT_MAP[suitChar] ?: return null
            return Card(cardValue, suit)
        }
    }
}
