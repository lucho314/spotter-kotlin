package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExerciseNoteTest {

    @Test
    fun `clampInput caps at MAX_LENGTH without trimming`() {
        assertThat(ExerciseNote.clampInput(" la próxima ")).isEqualTo(" la próxima ")
        assertThat(ExerciseNote.clampInput("a".repeat(60))).hasLength(ExerciseNote.MAX_LENGTH)
    }

    @Test
    fun `clampInput never splits a surrogate pair at the limit`() {
        val text = "a".repeat(ExerciseNote.MAX_LENGTH - 1) + "💪"

        assertThat(ExerciseNote.clampInput(text)).isEqualTo("a".repeat(ExerciseNote.MAX_LENGTH - 1))
    }

    @Test
    fun `normalize trims, collapses to one line and maps blank to null`() {
        assertThat(ExerciseNote.normalize("  llegué\njusto  ")).isEqualTo("llegué justo")
        assertThat(ExerciseNote.normalize("   ")).isNull()
        assertThat(ExerciseNote.normalize(null)).isNull()
    }
}
