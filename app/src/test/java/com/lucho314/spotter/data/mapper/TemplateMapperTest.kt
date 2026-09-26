package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.remote.dto.RoutineTemplateDto
import com.lucho314.spotter.data.remote.dto.TemplateDayDto
import org.junit.Test

class TemplateMapperTest {

    private fun day(dayNumber: Int, name: String) = TemplateDayDto(
        id = "day-$dayNumber", templateId = "t1", dayNumber = dayNumber, name = name, nameEs = name,
    )

    @Test
    fun `toDetail sorts days by dayNumber regardless of the server's row order`() {
        val dto = RoutineTemplateDto(
            id = "t1", name = "PPL", nameEs = "PPL", goal = "hypertrophy", difficulty = "intermediate",
            daysPerWeek = 3, isActive = true, sortOrder = 0,
            days = listOf(day(3, "Legs"), day(1, "Push"), day(2, "Pull")),
        )

        val detail = dto.toDetail()

        assertThat(detail.days.map { it.dayNumber }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `toSummary prefers the Spanish columns, falling back to the English ones when blank`() {
        val dto = RoutineTemplateDto(
            id = "t1", name = "PPL", nameEs = "", description = "desc", descriptionEs = "",
            goal = "hypertrophy", difficulty = "intermediate", daysPerWeek = 3, isActive = true, sortOrder = 0,
        )

        val summary = dto.toSummary()

        assertThat(summary.name).isEqualTo("PPL")
        assertThat(summary.description).isEqualTo("desc")
    }
}
