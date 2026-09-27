package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ExportFormat
import com.lucho314.spotter.domain.model.ExportedFile
import com.lucho314.spotter.domain.model.WorkoutExportData
import com.lucho314.spotter.domain.repository.WorkoutExportRepository
import kotlinx.coroutines.CompletableDeferred

/** In-memory [WorkoutExportRepository] test double. */
class FakeWorkoutExportRepository : WorkoutExportRepository {

    var result: AppResult<ExportedFile> = AppResult.Success(ExportedFile("content://exports/f.pdf", "application/pdf"))
    val exported = mutableListOf<Pair<WorkoutExportData, ExportFormat>>()

    /** If set, awaited before [export] returns - lets tests exercise concurrent double-tap calls. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun export(data: WorkoutExportData, format: ExportFormat): AppResult<ExportedFile> {
        gate?.await()
        exported += data to format
        return result
    }
}
