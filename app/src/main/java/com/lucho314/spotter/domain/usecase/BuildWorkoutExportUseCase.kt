package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.domain.calc.WorkoutExportDataBuilder
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutExportData
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import javax.inject.Inject

/** Thin wrapper so the feature layer injects a use case, not [WorkoutExportDataBuilder] directly. */
class BuildWorkoutExportUseCase @Inject constructor(private val timeProvider: TimeProvider) {
    operator fun invoke(detail: WorkoutSessionDetail, unit: WeightUnit): WorkoutExportData =
        WorkoutExportDataBuilder.build(detail, unit, timeProvider.zone())
}
