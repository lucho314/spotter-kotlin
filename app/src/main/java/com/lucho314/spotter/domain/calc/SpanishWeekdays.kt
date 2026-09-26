package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.RoutineSummary
import java.time.DayOfWeek

/** Río de la Plata Spanish weekday names, used for routine day labels (`routine_days.name`). */
object SpanishWeekdays {

    val ALL = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    private val ABBR = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /** 1 (Lunes) through 7 (Domingo), or 99 if [name] isn't one of [ALL]. */
    fun order(name: String): Int {
        val index = ALL.indexOf(name)
        return if (index >= 0) index + 1 else 99
    }

    fun abbr(name: String): String {
        val index = ALL.indexOf(name)
        return if (index >= 0) ABBR[index] else name.take(3)
    }

    fun of(dayOfWeek: DayOfWeek): String = ALL[dayOfWeek.value - 1]

    /**
     * Routines ordered by the earliest weekday among their [RoutineSummary.days] (routines with no
     * recognized weekday name sort last), then by name for a stable tie-break.
     */
    fun sortRoutinesByFirstWeekday(routines: List<RoutineSummary>): List<RoutineSummary> =
        routines.sortedWith(
            compareBy(
                { routine -> routine.days.minOfOrNull { order(it.name) } ?: 99 },
                { it.name },
            ),
        )
}
