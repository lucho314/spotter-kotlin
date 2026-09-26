package com.lucho314.spotter.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExerciseMediaTest {

    @Test
    fun `mp4, webm and m3u8 urls are videos`() {
        assertThat(isVideoUrl("https://cdn.example.com/exercise.mp4")).isTrue()
        assertThat(isVideoUrl("https://cdn.example.com/exercise.webm")).isTrue()
        assertThat(isVideoUrl("https://cdn.example.com/exercise.m3u8")).isTrue()
    }

    @Test
    fun `matching is case-insensitive`() {
        assertThat(isVideoUrl("https://cdn.example.com/exercise.MP4")).isTrue()
    }

    @Test
    fun `a query string after the extension is ignored`() {
        assertThat(isVideoUrl("https://cdn.example.com/exercise.mp4?token=abc&size=large")).isTrue()
        assertThat(isVideoUrl("https://cdn.example.com/exercise.png?ext=.mp4")).isFalse()
    }

    @Test
    fun `still images and unknown extensions are not videos`() {
        assertThat(isVideoUrl("https://cdn.example.com/exercise.gif")).isFalse()
        assertThat(isVideoUrl("https://cdn.example.com/exercise.png")).isFalse()
        assertThat(isVideoUrl("https://cdn.example.com/exercise")).isFalse()
    }
}
