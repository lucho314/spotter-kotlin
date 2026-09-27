package com.lucho314.spotter.data.image

import kotlin.math.max

/** Pure bitmap-sizing/EXIF-orientation math for [com.lucho314.spotter.data.image.ImageRepositoryImpl]. No `android.graphics`/`android.media` imports: testable outside Robolectric/instrumentation. */
object ImageSizing {

    const val MAX_DIMENSION = 1600
    val JPEG_QUALITIES = listOf(80, 70, 60)

    /** Largest power of 2 such that `max(width, height) / sample >= maxDimension`; 1 if the dimensions are invalid or already small enough. */
    fun inSampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val longestSide = max(width, height)
        while (longestSide / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    /** Keeps the aspect ratio, caps the longest side at [maxDimension], never upscales, and never returns a side below 1. */
    fun scaledSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        val longestSide = max(width, height)
        if (longestSide <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / longestSide
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
        return scaledWidth to scaledHeight
    }

    fun base64Length(byteCount: Int): Int = ((byteCount + 2) / 3) * 4

    /**
     * Maps an EXIF `Orientation` tag value (1..8, or anything else treated as normal) to the
     * rotation + horizontal flip needed to display the image upright - the same table used by
     * Glide's `TransformationUtils`:
     * 2->(0,flip) 3->(180,noFlip) 4->(180,flip) 5->(90,flip) 6->(90,noFlip) 7->(270,flip) 8->(270,noFlip) else->(0,noFlip).
     */
    fun exifTransform(orientation: Int): ExifTransform = when (orientation) {
        2 -> ExifTransform(0, true)
        3 -> ExifTransform(180, false)
        4 -> ExifTransform(180, true)
        5 -> ExifTransform(90, true)
        6 -> ExifTransform(90, false)
        7 -> ExifTransform(270, true)
        8 -> ExifTransform(270, false)
        else -> ExifTransform(0, false)
    }
}

data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean)
