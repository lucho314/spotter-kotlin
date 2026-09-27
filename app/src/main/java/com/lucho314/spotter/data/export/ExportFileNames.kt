package com.lucho314.spotter.data.export

import com.lucho314.spotter.domain.model.ExportFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** File naming/staleness for exported workout files in `cacheDir/exports/`. Kept free of Android imports (pure, testable). */
object ExportFileNames {

    const val DIRECTORY = "exports"
    const val MAX_AGE_MS = 60 * 60 * 1000L

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /** "spotter-entrenamiento-<yyyyMMdd-HHmmss UTC>.<ext>" - no user names/ids in the file name. */
    fun fileName(format: ExportFormat, now: Instant): String = "spotter-entrenamiento-${TIMESTAMP.format(now)}.${format.extension}"

    fun isStale(lastModifiedMs: Long, nowMs: Long): Boolean = nowMs - lastModifiedMs > MAX_AGE_MS
}
