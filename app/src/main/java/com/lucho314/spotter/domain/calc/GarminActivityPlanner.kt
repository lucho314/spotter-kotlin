package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminExerciseRef
import com.lucho314.spotter.domain.model.GarminPlannedSet
import com.lucho314.spotter.domain.model.GarminSetKind
import java.time.Instant

/**
 * Turns a [GarminActivitySnapshot] into a deterministic timeline of FIT `set` messages. Spotter
 * only stores `completedAt` per set - duration and rest are heuristic (a fixed [SECONDS_PER_REP]
 * per rep, clamped to a plausible [MIN_ACTIVE_SECONDS]..[MAX_ACTIVE_SECONDS] range) - but the same
 * input always produces the same plan (and therefore the same FIT bytes downstream), which is what
 * makes Garmin's duplicate detection reliable.
 */
object GarminActivityPlanner {
    const val SECONDS_PER_REP = 3L
    const val MIN_ACTIVE_SECONDS = 20L
    const val MAX_ACTIVE_SECONDS = 90L
    const val DEFAULT_ACTIVE_SECONDS = 30L
    const val MIN_SET_SECONDS = 1L

    private const val MAX_REPETITIONS = 65534
    private const val MAX_WEIGHT_KG = 4095.0

    /** Returns null if [GarminActivitySnapshot.sets] is empty. */
    fun plan(snapshot: GarminActivitySnapshot, refs: Map<Int, GarminExerciseRef?>, utcOffsetSeconds: Int): GarminActivityPlan? {
        if (snapshot.sets.isEmpty()) return null

        val startedAt = truncate(snapshot.startedAt)
        val completedAt = truncate(snapshot.completedAt)
        val sorted = snapshot.sets
            .map { it.copy(completedAt = truncate(it.completedAt)) }
            .sortedWith(compareBy({ it.completedAt }, { it.exerciseOrder }, { it.setNumber }))

        val first = sorted.first()
        val start = minOf(startedAt, first.completedAt.minusSeconds(activeSeconds(first.reps)))
        var cursor = start

        val plannedSets = mutableListOf<GarminPlannedSet>()
        for (s in sorted) {
            val endI = maxOf(s.completedAt, cursor.plusSeconds(MIN_SET_SECONDS))
            val startI = maxOf(cursor, endI.minusSeconds(activeSeconds(s.reps)))
            if (startI > cursor) {
                plannedSets += GarminPlannedSet(
                    kind = GarminSetKind.REST,
                    startTime = cursor,
                    endTime = startI,
                    repetitions = null,
                    weightKg = null,
                    exercise = null,
                    weightUnit = null,
                )
            }
            // Warmup sets are emitted as ACTIVE too: FIT's `set` message has no warmup flag.
            plannedSets += GarminPlannedSet(
                kind = GarminSetKind.ACTIVE,
                startTime = startI,
                endTime = endI,
                repetitions = s.reps.takeIf { it > 0 }?.coerceAtMost(MAX_REPETITIONS),
                weightKg = s.weightKg.takeIf { it > 0 }?.coerceAtMost(MAX_WEIGHT_KG),
                exercise = refs[s.exerciseId],
                weightUnit = snapshot.weightUnit,
            )
            cursor = endI
        }
        val end = maxOf(completedAt, cursor)

        return GarminActivityPlan(
            workoutId = snapshot.workoutId,
            startTime = start,
            endTime = end,
            utcOffsetSeconds = utcOffsetSeconds,
            sets = plannedSets,
        )
    }

    private fun activeSeconds(reps: Int): Long =
        if (reps > 0) (reps * SECONDS_PER_REP).coerceIn(MIN_ACTIVE_SECONDS, MAX_ACTIVE_SECONDS) else DEFAULT_ACTIVE_SECONDS

    /** All instants are truncated to whole seconds before computing anything, for a deterministic FIT output. */
    private fun truncate(instant: Instant): Instant = Instant.ofEpochSecond(instant.epochSecond)
}
