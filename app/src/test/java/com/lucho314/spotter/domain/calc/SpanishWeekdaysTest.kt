package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.RoutineDay
import com.lucho314.spotter.domain.model.RoutineSummary
import java.time.DayOfWeek
import java.time.Instant
import org.junit.Test

private fun summary(id: String, name: String, days: List<RoutineDay>) = RoutineSummary(
    id = id,
    name = name,
    description = null,
    daysPerWeek = days.size.takeIf { it > 0 },
    exerciseCount = 0,
    days = days,
    createdAt = Instant.EPOCH,
)

private fun day(dayNumber: Int, name: String) = RoutineDay("day-$dayNumber", "routine", dayNumber, name)

class SpanishWeekdaysTest {

    @Test
    fun `order returns 1 through 7, and 99 for anything else`() {
        assertThat(SpanishWeekdays.order("Lunes")).isEqualTo(1)
        assertThat(SpanishWeekdays.order("Domingo")).isEqualTo(7)
        assertThat(SpanishWeekdays.order("Sin día asignado")).isEqualTo(99)
    }

    @Test
    fun `abbr shortens known weekdays`() {
        assertThat(SpanishWeekdays.abbr("Miércoles")).isEqualTo("Mié")
        assertThat(SpanishWeekdays.abbr("Sábado")).isEqualTo("Sáb")
    }

    @Test
    fun `of maps java-time DayOfWeek to the Spanish name`() {
        assertThat(SpanishWeekdays.of(DayOfWeek.MONDAY)).isEqualTo("Lunes")
        assertThat(SpanishWeekdays.of(DayOfWeek.SUNDAY)).isEqualTo("Domingo")
    }

    @Test
    fun `sortRoutinesByFirstWeekday orders by the earliest weekday, unrecognized names last`() {
        val wednesdayRoutine = summary("w", "Full body", listOf(day(3, "Miércoles")))
        val mondayRoutine = summary("m", "Push", listOf(day(1, "Lunes"), day(4, "Jueves")))
        val noWeekdayRoutine = summary("x", "Sin día", listOf(day(1, "Día A")))

        val sorted = SpanishWeekdays.sortRoutinesByFirstWeekday(listOf(wednesdayRoutine, noWeekdayRoutine, mondayRoutine))

        assertThat(sorted.map { it.id }).containsExactly("m", "w", "x").inOrder()
    }
}
