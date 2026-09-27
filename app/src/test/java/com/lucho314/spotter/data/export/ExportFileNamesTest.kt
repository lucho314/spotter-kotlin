package com.lucho314.spotter.data.export

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.ExportFormat
import java.time.Instant
import org.junit.Test

class ExportFileNamesTest {

    private val fixedInstant = Instant.parse("2026-09-26T15:30:00Z")

    @Test
    fun `fileName formats a fixed instant for PDF and STORY`() {
        assertThat(ExportFileNames.fileName(ExportFormat.PDF, fixedInstant)).isEqualTo("spotter-entrenamiento-20260926-153000.pdf")
        assertThat(ExportFileNames.fileName(ExportFormat.STORY, fixedInstant)).isEqualTo("spotter-entrenamiento-20260926-153000.jpg")
    }

    @Test
    fun `isStale is false exactly at 1 hour, true 1ms past it`() {
        val lastModified = 0L
        assertThat(ExportFileNames.isStale(lastModified, ExportFileNames.MAX_AGE_MS)).isFalse()
        assertThat(ExportFileNames.isStale(lastModified, ExportFileNames.MAX_AGE_MS + 1)).isTrue()
    }
}
