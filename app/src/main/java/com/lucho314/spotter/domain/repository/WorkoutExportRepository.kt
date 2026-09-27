package com.lucho314.spotter.domain.repository

import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.domain.model.ExportFormat
import com.lucho314.spotter.domain.model.ExportedFile
import com.lucho314.spotter.domain.model.WorkoutExportData

/** Renders a workout export and writes it to disk, ready to be shared via `FileProvider`. */
interface WorkoutExportRepository {
    /**
     * Renders [data] as [format] into `cacheDir/exports/` and returns a `FileProvider` Uri.
     * Failures: [com.lucho314.spotter.core.common.AppError.Unknown] (disk/OOM/rendering issues).
     */
    suspend fun export(data: WorkoutExportData, format: ExportFormat): AppResult<ExportedFile>
}
