package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.SharedRoutineContent
import com.lucho314.spotter.domain.model.SharedRoutineDay
import com.lucho314.spotter.domain.model.SharedRoutineExercise
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import org.junit.Test

class SharedRoutineSanitizerTest {

    private fun content(
        name: String = "Push",
        description: String? = null,
        daysPerWeek: Int? = null,
        days: List<SharedRoutineDay> = emptyList(),
        exercises: List<SharedRoutineExercise> = emptyList(),
    ) = SharedRoutineContent(
        routineName = name,
        description = description,
        daysPerWeek = daysPerWeek,
        days = days,
        exercises = exercises,
        expiresAt = null,
    )

    private fun exercise(exerciseId: Int, dayNumber: Int?, sortOrder: Int = 0, sets: Int = 3, reps: Int = 10, rest: Int = 90) =
        SharedRoutineExercise(exerciseId, dayNumber, sortOrder, sets, reps, rest)

    @Test
    fun `importedName suffixes a short name`() {
        assertThat(SharedRoutineSanitizer.importedName("Push")).isEqualTo("Push (importada)")
    }

    @Test
    fun `importedName clamps a 50-char name to exactly 50 chars total`() {
        val name = "A".repeat(50)
        val result = SharedRoutineSanitizer.importedName(name)
        assertThat(result).hasLength(50)
        assertThat(result).endsWith(" (importada)")
    }

    @Test
    fun `importedName falls back to Rutina for a blank name`() {
        assertThat(SharedRoutineSanitizer.importedName("   ")).isEqualTo("Rutina (importada)")
        assertThat(SharedRoutineSanitizer.importedName(null)).isEqualTo("Rutina (importada)")
    }

    @Test
    fun `description over 200 chars is truncated, blank becomes null`() {
        val long = "x".repeat(250)
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(description = long)))
        assertThat(sanitized.input.description).hasLength(200)

        val blank = requireNotNull(SharedRoutineSanitizer.sanitize(content(description = "   ")))
        assertThat(blank.input.description).isNull()
    }

    @Test
    fun `daysPerWeek outside 1 to 7 becomes null`() {
        assertThat(requireNotNull(SharedRoutineSanitizer.sanitize(content(daysPerWeek = 0))).input.daysPerWeek).isNull()
        assertThat(requireNotNull(SharedRoutineSanitizer.sanitize(content(daysPerWeek = 8))).input.daysPerWeek).isNull()
        assertThat(requireNotNull(SharedRoutineSanitizer.sanitize(content(daysPerWeek = 3))).input.daysPerWeek).isEqualTo(3)
    }

    @Test
    fun `invalid or duplicate day numbers are discarded, blank names fall back to the weekday name`() {
        val days = listOf(
            SharedRoutineDay(0, "Zero"),
            SharedRoutineDay(8, "Eight"),
            SharedRoutineDay(1, "  "),
            SharedRoutineDay(1, "Duplicate"),
            SharedRoutineDay(2, "Custom"),
        )
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(days = days)))

        assertThat(sanitized.days).containsExactly(1 to "Lunes", 2 to "Custom").inOrder()
    }

    @Test
    fun `exercise numeric fields are coerced into their valid ranges`() {
        val exercises = listOf(exercise(1, dayNumber = 1, sets = 0, reps = 0, rest = 5))
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(exercises = exercises)))
        val (input, _) = sanitized.exercises.single()
        assertThat(input.targetSets).isEqualTo(1)
        assertThat(input.targetReps).isEqualTo(1)
        assertThat(input.restSeconds).isEqualTo(15)

        val exercisesHigh = listOf(exercise(1, dayNumber = 1, sets = 50, reps = 500, rest = 900))
        val sanitizedHigh = requireNotNull(SharedRoutineSanitizer.sanitize(content(exercises = exercisesHigh)))
        val (inputHigh, _) = sanitizedHigh.exercises.single()
        assertThat(inputHigh.targetSets).isEqualTo(20)
        assertThat(inputHigh.targetReps).isEqualTo(100)
        assertThat(inputHigh.restSeconds).isEqualTo(600)
    }

    @Test
    fun `exercises with a non-positive exerciseId are discarded`() {
        val exercises = listOf(exercise(0, dayNumber = 1), exercise(-1, dayNumber = 1), exercise(5, dayNumber = 1))
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(exercises = exercises)))
        assertThat(sanitized.exercises.map { it.first.exerciseId }).containsExactly(5)
    }

    @Test
    fun `null or out-of-range dayNumber becomes the unassigned sentinel, day 1 without days is kept`() {
        val exercises = listOf(exercise(1, dayNumber = null), exercise(2, dayNumber = 12), exercise(3, dayNumber = 1))
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(exercises = exercises)))
        val byId = sanitized.exercises.associate { it.first.exerciseId to it.first.dayNumber }
        assertThat(byId[1]).isEqualTo(UNASSIGNED_DAY_NUMBER)
        assertThat(byId[2]).isEqualTo(UNASSIGNED_DAY_NUMBER)
        assertThat(byId[3]).isEqualTo(1)
    }

    @Test
    fun `duplicate exerciseId+dayNumber keeps the one with the lowest sortOrder`() {
        val exercises = listOf(
            exercise(1, dayNumber = 1, sortOrder = 5),
            exercise(1, dayNumber = 1, sortOrder = 2),
        )
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(exercises = exercises)))
        assertThat(sanitized.exercises).hasSize(1)
        assertThat(sanitized.exercises.single().second).isEqualTo(2)
    }

    @Test
    fun `negative sortOrder is coerced to 0`() {
        val exercises = listOf(exercise(1, dayNumber = 1, sortOrder = -5))
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(exercises = exercises)))
        assertThat(sanitized.exercises.single().second).isEqualTo(0)
    }

    @Test
    fun `more than 100 exercises is rejected, exactly 100 is kept`() {
        val tooMany = (1..101).map { exercise(it, dayNumber = 1, sortOrder = it) }
        assertThat(SharedRoutineSanitizer.sanitize(content(exercises = tooMany))).isNull()

        val exactly = (1..100).map { exercise(it, dayNumber = 1, sortOrder = it) }
        assertThat(SharedRoutineSanitizer.sanitize(content(exercises = exactly))).isNotNull()
    }

    @Test
    fun `input name carries the imported suffix`() {
        val sanitized = requireNotNull(SharedRoutineSanitizer.sanitize(content(name = "Push")))
        assertThat(sanitized.input.name).isEqualTo("Push (importada)")
        assertThat(sanitized.originalName).isEqualTo("Push")
    }
}
