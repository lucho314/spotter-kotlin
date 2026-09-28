package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminSetKind
import com.lucho314.spotter.domain.model.GarminSetSnapshot
import com.lucho314.spotter.domain.model.WeightUnit
import java.time.Instant
import org.junit.Test

class GarminActivityPlannerTest {

    private fun snapshot(startedAt: Instant, completedAt: Instant, sets: List<GarminSetSnapshot>) = GarminActivitySnapshot(
        workoutId = "w1", userId = "u1", startedAt = startedAt, completedAt = completedAt, weightUnit = WeightUnit.KG, sets = sets,
    )

    private fun set(
        exerciseId: Int = 1,
        order: Int = 0,
        setNumber: Int = 1,
        weightKg: Double = 80.0,
        reps: Int = 10,
        completedAt: Instant,
        warmup: Boolean = false,
    ) = GarminSetSnapshot(
        exerciseId = exerciseId, exerciseName = "Exercise", equipment = null, exerciseOrder = order,
        setNumber = setNumber, weightKg = weightKg, reps = reps, isWarmup = warmup, completedAt = completedAt,
    )

    @Test
    fun `empty sets returns null`() {
        val snap = snapshot(Instant.EPOCH, Instant.EPOCH, emptyList())
        assertThat(GarminActivityPlanner.plan(snap, emptyMap(), 0)).isNull()
    }

    @Test
    fun `rest is inserted between two well-separated sets`() {
        val start = Instant.ofEpochSecond(1000)
        val s1 = set(completedAt = start.plusSeconds(30), setNumber = 1) // 10 reps -> 30s active
        val s2 = set(completedAt = start.plusSeconds(200), setNumber = 2)
        val snap = snapshot(start, start.plusSeconds(210), listOf(s1, s2))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!

        val kinds = plan.sets.map { it.kind }
        assertThat(kinds).containsExactly(GarminSetKind.ACTIVE, GarminSetKind.REST, GarminSetKind.ACTIVE).inOrder()
    }

    @Test
    fun `active duration is clamped between 20 and 90 seconds`() {
        val start = Instant.ofEpochSecond(0)
        val fewReps = set(reps = 1, completedAt = start.plusSeconds(500)) // 3s raw -> clamps to 20
        val manyReps = set(reps = 100, completedAt = start.plusSeconds(1000)) // 300s raw -> clamps to 90
        val snap = snapshot(start, start.plusSeconds(1010), listOf(fewReps, manyReps))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        val activeSets = plan.sets.filter { it.kind == GarminSetKind.ACTIVE }
        assertThat(java.time.Duration.between(activeSets[0].startTime, activeSets[0].endTime).seconds).isEqualTo(20)
        assertThat(java.time.Duration.between(activeSets[1].startTime, activeSets[1].endTime).seconds).isEqualTo(90)
    }

    @Test
    fun `zero reps defaults to 30 active seconds and null repetitions`() {
        val start = Instant.ofEpochSecond(0)
        val s = set(reps = 0, completedAt = start.plusSeconds(500))
        val snap = snapshot(start, start.plusSeconds(510), listOf(s))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        val active = plan.sets.single { it.kind == GarminSetKind.ACTIVE }
        assertThat(java.time.Duration.between(active.startTime, active.endTime).seconds).isEqualTo(30)
        assertThat(active.repetitions).isNull()
    }

    @Test
    fun `zero weight becomes null`() {
        val start = Instant.ofEpochSecond(0)
        val s = set(weightKg = 0.0, completedAt = start.plusSeconds(30))
        val snap = snapshot(start, start.plusSeconds(40), listOf(s))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        assertThat(plan.sets.single { it.kind == GarminSetKind.ACTIVE }.weightKg).isNull()
    }

    @Test
    fun `sets sharing the same completedAt stay sequential with a minimum of 1 second`() {
        val start = Instant.ofEpochSecond(0)
        val same = start.plusSeconds(100)
        val s1 = set(setNumber = 1, completedAt = same)
        val s2 = set(setNumber = 2, completedAt = same)
        val snap = snapshot(start, same.plusSeconds(1), listOf(s1, s2))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        val actives = plan.sets.filter { it.kind == GarminSetKind.ACTIVE }
        assertThat(actives).hasSize(2)
        assertThat(actives[1].endTime).isAtLeast(actives[0].endTime.plusSeconds(1))
    }

    @Test
    fun `sets are ordered by completedAt then exerciseOrder then setNumber`() {
        val start = Instant.ofEpochSecond(0)
        val t = start.plusSeconds(100)
        val sLate = set(order = 0, setNumber = 1, completedAt = start.plusSeconds(200))
        val sEarlySecondOrder = set(order = 1, setNumber = 1, completedAt = t)
        val sEarlyFirstOrder = set(order = 0, setNumber = 2, completedAt = t)
        val snap = snapshot(start, start.plusSeconds(210), listOf(sLate, sEarlySecondOrder, sEarlyFirstOrder))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        // Earliest completedAt group (t) sorted by exerciseOrder (0 before 1) then setNumber comes
        // first; the later completedAt (200s) set comes last.
        val endTimes = plan.sets.filter { it.kind == GarminSetKind.ACTIVE }.map { it.endTime }
        assertThat(endTimes).isInOrder()
    }

    @Test
    fun `a set completed before startedAt pulls the plan start earlier`() {
        val startedAt = Instant.ofEpochSecond(1000)
        val completedBeforeStart = Instant.ofEpochSecond(500)
        val s = set(completedAt = completedBeforeStart)
        val snap = snapshot(startedAt, startedAt.plusSeconds(10), listOf(s))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        assertThat(plan.startTime).isLessThan(startedAt)
    }

    @Test
    fun `end extends past completedAt if the cursor already passed it`() {
        val start = Instant.ofEpochSecond(0)
        val s = set(completedAt = start.plusSeconds(500))
        // snapshot.completedAt is earlier than the last set's own completedAt.
        val snap = snapshot(start, start.plusSeconds(10), listOf(s))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        assertThat(plan.endTime).isAtLeast(start.plusSeconds(500))
    }

    @Test
    fun `instants are truncated to whole seconds`() {
        val start = Instant.ofEpochSecond(0, 500_000_000)
        val s = set(completedAt = start.plusSeconds(30).plusNanos(999_999_999))
        val snap = snapshot(start, start.plusSeconds(40), listOf(s))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        assertThat(plan.startTime.nano).isEqualTo(0)
        assertThat(plan.sets.all { it.startTime.nano == 0 && it.endTime.nano == 0 }).isTrue()
    }

    @Test
    fun `weight unit is only set on ACTIVE sets, never on REST`() {
        val start = Instant.ofEpochSecond(1000)
        val s1 = set(completedAt = start.plusSeconds(30))
        val s2 = set(completedAt = start.plusSeconds(200))
        val snap = snapshot(start, start.plusSeconds(210), listOf(s1, s2))

        val plan = GarminActivityPlanner.plan(snap, emptyMap(), 0)!!
        plan.sets.forEach { planned ->
            when (planned.kind) {
                GarminSetKind.ACTIVE -> assertThat(planned.weightUnit).isEqualTo(WeightUnit.KG)
                GarminSetKind.REST -> assertThat(planned.weightUnit).isNull()
            }
        }
    }
}
