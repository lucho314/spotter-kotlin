package com.lucho314.spotter.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.domain.model.WorkoutSessionDetail
import com.lucho314.spotter.domain.model.WorkoutSet
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class BuildWorkoutExportUseCaseTest {

    @Test
    fun `uses the time provider's zone to format the date`() {
        val timeProvider = FakeTimeProvider(zoneId = ZoneId.of("America/Argentina/Buenos_Aires"))
        val completedAt = Instant.parse("2026-03-04T01:30:00Z")
        val set = WorkoutSet("s1", "session1", 1, "Bench", 1, 80.0, 10, null, false, completedAt)
        val detail = WorkoutSessionDetail(id = "session1", routineName = "Push", startedAt = completedAt, completedAt = completedAt, notes = null, sets = listOf(set))

        val result = BuildWorkoutExportUseCase(timeProvider)(detail, WeightUnit.KG)

        assertThat(result.dateText).isEqualTo("Martes 3 de marzo de 2026")

        // A different zone shifts the local date and thus the formatted text.
        val utcTimeProvider = FakeTimeProvider(zoneId = ZoneId.of("UTC"))
        val utcResult = BuildWorkoutExportUseCase(utcTimeProvider)(detail, WeightUnit.KG)
        assertThat(utcResult.dateText).isEqualTo("Miércoles 4 de marzo de 2026")
    }
}
