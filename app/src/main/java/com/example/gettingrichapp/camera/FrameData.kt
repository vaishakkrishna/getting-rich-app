package com.example.gettingrichapp.camera

import android.graphics.Bitmap

data class FrameData(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val width: Int,
    val height: Int
)
