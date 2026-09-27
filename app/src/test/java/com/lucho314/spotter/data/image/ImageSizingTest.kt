package com.lucho314.spotter.data.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageSizingTest {

    @Test
    fun `inSampleSize picks the largest power of 2 that keeps the longest side above maxDimension`() {
        assertThat(ImageSizing.inSampleSize(4000, 3000)).isEqualTo(2)
        assertThat(ImageSizing.inSampleSize(1600, 1200)).isEqualTo(1)
        assertThat(ImageSizing.inSampleSize(8000, 6000)).isEqualTo(4)
        assertThat(ImageSizing.inSampleSize(0, 0)).isEqualTo(1)
    }

    @Test
    fun `scaledSize keeps aspect ratio, never upscales, never returns a side below 1`() {
        assertThat(ImageSizing.scaledSize(4000, 3000)).isEqualTo(1600 to 1200)
        assertThat(ImageSizing.scaledSize(1000, 800)).isEqualTo(1000 to 800)
        val (width, height) = ImageSizing.scaledSize(3000, 1)
        assertThat(height).isAtLeast(1)
        assertThat(width).isEqualTo(1600)
    }

    @Test
    fun `base64Length matches the base64 expansion formula`() {
        assertThat(ImageSizing.base64Length(3)).isEqualTo(4)
        assertThat(ImageSizing.base64Length(4)).isEqualTo(8)
    }

    @Test
    fun `exifTransform maps every known orientation, defaulting to no-op for the rest`() {
        assertThat(ImageSizing.exifTransform(1)).isEqualTo(ExifTransform(0, false))
        assertThat(ImageSizing.exifTransform(2)).isEqualTo(ExifTransform(0, true))
        assertThat(ImageSizing.exifTransform(3)).isEqualTo(ExifTransform(180, false))
        assertThat(ImageSizing.exifTransform(4)).isEqualTo(ExifTransform(180, true))
        assertThat(ImageSizing.exifTransform(5)).isEqualTo(ExifTransform(90, true))
        assertThat(ImageSizing.exifTransform(6)).isEqualTo(ExifTransform(90, false))
        assertThat(ImageSizing.exifTransform(7)).isEqualTo(ExifTransform(270, true))
        assertThat(ImageSizing.exifTransform(8)).isEqualTo(ExifTransform(270, false))
        assertThat(ImageSizing.exifTransform(0)).isEqualTo(ExifTransform(0, false))
    }
}
