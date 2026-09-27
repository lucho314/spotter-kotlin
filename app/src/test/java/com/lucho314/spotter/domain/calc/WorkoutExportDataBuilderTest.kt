package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSet
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class WorkoutExportDataBuilderTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")

    private fun set(
        id: String,
        exerciseId: Int,
        exerciseName: String? = "Bench",
        setNumber: Int = 1,
        weightKg: Double = 80.0,
        reps: Int = 10,
        rpe: Double? = null,
        isWarmup: Boolean = false,
        completedAt: Instant = Instant.EPOCH,
    ) = WorkoutSet(id, "s1", exerciseId, exerciseName, setNumber, weightKg, reps, rpe, isWarmup, completedAt)

    private fun detail(sets: List<WorkoutSet>, routineName: String? = "Push", startedAt: Instant = Instant.EPOCH, completedAt: Instant? = Instant.EPOCH.plusSeconds(3600)) =
        WorkoutSessionDetail(id = "s1", routineName = routineName, startedAt = startedAt, completedAt = completedAt, notes = null, sets = sets)

    @Test
    fun `exercises are grouped and ordered, sets ordered by setNumber`() {
        val sets = listOf(
            set("a2", exerciseId = 1, setNumber = 2, completedAt = Instant.ofEpochSecond(10)),
            set("a1", exerciseId = 1, setNumber = 1, completedAt = Instant.ofEpochSecond(5)),
            set("b1", exerciseId = 2, exerciseName = "Squat", setNumber = 1, completedAt = Instant.ofEpochSecond(1)),
        )

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.KG, zone)

        assertThat(data.exercises.map { it.exerciseId }).containsExactly(2, 1).inOrder()
        assertThat(data.exercises.last().sets.map { it.setNumber }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `volume and set counts exclude warmups, total and per exercise`() {
        val sets = listOf(
            set("a1", exerciseId = 1, weightKg = 100.0, reps = 5, isWarmup = true),
            set("a2", exerciseId = 1, weightKg = 80.0, reps = 10, setNumber = 2),
        )

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.KG, zone)

        assertThat(data.totalSets).isEqualTo(1)
        val exercise = data.exercises.single()
        assertThat(exercise.workingSetCount).isEqualTo(1)
        assertThat(exercise.volumeText).isEqualTo(NumberFormatter.formatVolume(800.0, WeightUnit.KG))
        assertThat(data.totalVolumeText).isEqualTo(NumberFormatter.formatVolume(800.0, WeightUnit.KG))
    }

    @Test
    fun `LB unit converts weights, volume suffix and unit label`() {
        val sets = listOf(set("a1", exerciseId = 1, weightKg = 100.0, reps = 10))

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.LB, zone)

        assertThat(data.unitLabel).isEqualTo("lb")
        assertThat(data.totalVolumeText).endsWith("lb")
        assertThat(data.exercises.single().sets.single().weightText).isEqualTo(WeightConverter.format(100.0, WeightUnit.LB))
    }

    @Test
    fun `topSet is the heaviest working set, ties broken by more reps`() {
        val sets = listOf(
            set("a1", exerciseId = 1, weightKg = 100.0, reps = 5, setNumber = 1),
            set("a2", exerciseId = 1, weightKg = 100.0, reps = 8, setNumber = 2),
            set("a3", exerciseId = 1, weightKg = 90.0, reps = 20, setNumber = 3),
        )

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.KG, zone)

        val topSet = requireNotNull(data.exercises.single().topSet)
        assertThat(topSet.reps).isEqualTo(8)
    }

    @Test
    fun `only-warmup exercise has a null topSet and zero workingSetCount`() {
        val sets = listOf(set("a1", exerciseId = 1, isWarmup = true))

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.KG, zone)

        val exercise = data.exercises.single()
        assertThat(exercise.topSet).isNull()
        assertThat(exercise.workingSetCount).isEqualTo(0)
    }

    @Test
    fun `story exercises are capped at 4, hiddenStoryExerciseCount reflects the rest`() {
        val sets = (1..6).map { set("a$it", exerciseId = it, setNumber = 1, completedAt = Instant.ofEpochSecond(it.toLong())) }

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.KG, zone)

        assertThat(data.storyExercises).hasSize(4)
        assertThat(data.hiddenStoryExerciseCount).isEqualTo(2)

        val setsThree = (1..3).map { set("a$it", exerciseId = it, setNumber = 1, completedAt = Instant.ofEpochSecond(it.toLong())) }
        val dataThree = WorkoutExportDataBuilder.build(detail(setsThree), WeightUnit.KG, zone)
        assertThat(dataThree.storyExercises).hasSize(3)
        assertThat(dataThree.hiddenStoryExerciseCount).isEqualTo(0)
    }

    @Test
    fun `duration is a dash when not completed, formatted otherwise`() {
        val sets = listOf(set("a1", exerciseId = 1))

        val notCompleted = WorkoutExportDataBuilder.build(detail(sets, completedAt = null), WeightUnit.KG, zone)
        assertThat(notCompleted.durationText).isEqualTo("—")

        val completed = WorkoutExportDataBuilder.build(detail(sets, startedAt = Instant.EPOCH, completedAt = Instant.EPOCH.plusSeconds(65 * 60)), WeightUnit.KG, zone)
        assertThat(completed.durationText).isEqualTo("1h 5min")
    }

    @Test
    fun `dateText uses the given zone in es-AR, titlecased`() {
        val sets = listOf(set("a1", exerciseId = 1))
        val completedAt = Instant.parse("2026-03-04T01:30:00Z")

        val data = WorkoutExportDataBuilder.build(detail(sets, startedAt = completedAt, completedAt = completedAt), WeightUnit.KG, zone)

        assertThat(data.dateText).isEqualTo("Martes 3 de marzo de 2026")
    }

    @Test
    fun `blank routineName becomes null`() {
        val sets = listOf(set("a1", exerciseId = 1))

        val data = WorkoutExportDataBuilder.build(detail(sets, routineName = "  "), WeightUnit.KG, zone)

        assertThat(data.routineName).isNull()
    }

    @Test
    fun `rpeText strips trailing zeros, null stays null`() {
        val sets = listOf(
            set("a1", exerciseId = 1, setNumber = 1, rpe = 8.0),
            set("a2", exerciseId = 1, setNumber = 2, rpe = 8.5),
            set("a3", exerciseId = 1, setNumber = 3, rpe = null),
        )

        val data = WorkoutExportDataBuilder.build(detail(sets), WeightUnit.KG, zone)

        val rows = data.exercises.single().sets.associateBy { it.setNumber }
        assertThat(rows.getValue(1).rpeText).isEqualTo("8")
        assertThat(rows.getValue(2).rpeText).isEqualTo("8.5")
        assertThat(rows.getValue(3).rpeText).isNull()
    }
}
