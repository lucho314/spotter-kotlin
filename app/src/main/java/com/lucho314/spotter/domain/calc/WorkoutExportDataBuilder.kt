package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.ExportExercise
import com.lucho314.spotter.domain.model.ExportSetRow
import com.lucho314.spotter.domain.model.ExportTopSet
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutExportData
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ROUTINE_NAME_MAX_LENGTH = 80
private const val EXERCISE_NAME_MAX_LENGTH = 80

/** Builds the structured, renderer-agnostic [WorkoutExportData] for a completed/in-progress session. */
object WorkoutExportDataBuilder {

    private val LOCALE = Locale.forLanguageTag("es-AR")
    private val DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu", LOCALE)

    fun build(detail: WorkoutSessionDetail, unit: WeightUnit, zone: ZoneId): WorkoutExportData {
        val effectiveDate = detail.completedAt ?: detail.startedAt
        val dateText = DATE.format(effectiveDate.atZone(zone)).replaceFirstChar { it.titlecase(LOCALE) }
        val durationMinutes = WorkoutMath.durationMinutes(detail.startedAt, detail.completedAt)
        val totalVolumeKg = WorkoutMath.volumeKg(detail.sets)
        val unitLabel = if (unit == WeightUnit.KG) "kg" else "lb"

        val exercises = ExerciseSetGrouping.group(detail.sets).map { group ->
            val working = group.sets.filterNot { it.isWarmup }
            val volumeKg = WorkoutMath.volumeKg(group.sets)
            val topSet = WorkoutMath.topSet(working)
            ExportExercise(
                exerciseId = group.exerciseId,
                name = TextSanitizer.singleLine(group.exerciseName, EXERCISE_NAME_MAX_LENGTH),
                sets = group.sets.map { set ->
                    ExportSetRow(
                        setNumber = set.setNumber,
                        weightText = WeightConverter.format(set.weightKg, unit),
                        reps = set.reps,
                        rpeText = set.rpe?.let { formatRpe(it) },
                        isWarmup = set.isWarmup,
                    )
                },
                workingSetCount = working.size,
                volumeText = NumberFormatter.formatVolume(volumeKg, unit),
                topSet = topSet?.let { ExportTopSet(WeightConverter.format(it.weightKg, unit), it.reps) },
            )
        }

        return WorkoutExportData(
            sessionId = detail.id,
            routineName = TextSanitizer.singleLine(detail.routineName, ROUTINE_NAME_MAX_LENGTH),
            dateText = dateText,
            durationText = WorkoutMath.formatDuration(durationMinutes),
            totalVolumeText = NumberFormatter.formatVolume(totalVolumeKg, unit),
            totalSets = detail.sets.count { !it.isWarmup },
            unitLabel = unitLabel,
            exercises = exercises,
        )
    }

    private fun formatRpe(rpe: Double): String = BigDecimal.valueOf(rpe).stripTrailingZeros().toPlainString()
}
