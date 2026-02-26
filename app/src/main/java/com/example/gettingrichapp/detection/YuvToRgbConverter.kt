package com.example.gettingrichapp.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object YuvToRgbConverter {

    private const val TAG = "YuvToRgbConverter"

    /**
     * Convert an I420 (YUV 4:2:0 planar) ByteBuffer to an ARGB_8888 Bitmap.
     *
     * I420 layout: [Y plane: w*h bytes] [U plane: w*h/4 bytes] [V plane: w*h/4 bytes]
     * NV21 layout: [Y plane: w*h bytes] [VU interleaved: w*h/2 bytes]
     *
     * Falls back to treating the buffer as NV21 directly if the buffer size
     * doesn't match I420 expectations or if I420 conversion fails.
     */
    fun i420ToBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
        val expectedI420Size = width * height * 3 / 2
        val bufferSize = buffer.remaining().coerceAtLeast(buffer.capacity())

        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val nv21 = if (bufferSize >= expectedI420Size) {
            try {
                i420BytesToNv21(bytes, width, height)
            } catch (e: Exception) {
                Log.w(TAG, "I420→NV21 conversion failed (${width}x${height}, buf=$bufferSize), trying direct NV21", e)
                // The buffer might already be in NV21 format — use as-is
                bytes.copyOf(expectedI420Size)
            }
        } else {
            Log.w(TAG, "Buffer too small for I420: expected=$expectedI420Size, got=$bufferSize")
            // Pad with zeros if needed
            bytes.copyOf(expectedI420Size)
        }

        return nv21ToBitmap(nv21, width, height)
    }

    internal fun i420ToNv21(buffer: ByteBuffer, width: Int, height: Int): ByteArray {
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return i420BytesToNv21(bytes, width, height)
    }

    private fun i420BytesToNv21(bytes: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4
        val nv21 = ByteArray(ySize + ySize / 2)

        // Copy Y plane as-is
        System.arraycopy(bytes, 0, nv21, 0, ySize)

        // Read U and V planes and interleave into NV21 (V first, then U)
        val uOffset = ySize
        val vOffset = ySize + uvSize
        var offset = ySize
        for (i in 0 until uvSize) {
            nv21[offset++] = bytes[vOffset + i]
            nv21[offset++] = bytes[uOffset + i]
        }

        return nv21
    }

    private fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: throw IllegalStateException("Failed to decode JPEG frame (${width}x${height})")
    }
}
