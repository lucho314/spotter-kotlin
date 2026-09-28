package com.lucho314.spotter.feature.workout

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.ActiveSet
import java.time.Instant
import org.junit.Test

class WorkoutFocusOrderTest {

    private fun set(id: String, completed: Boolean = false) =
        ActiveSet(id = id, setNumber = id.last().digitToInt(), weightText = "", repsText = "", isWarmup = false, completedAt = if (completed) Instant.EPOCH else null)

    @Test
    fun `weight always advances to reps of the same set`() {
        val sets = listOf(set("set-1"), set("set-2"))

        val target = WorkoutFocusOrder.next(sets, currentSetId = "set-1", currentField = SetInputField.WEIGHT)

        assertThat(target).isEqualTo(SetFocusTarget("set-1", SetInputField.REPS))
    }

    @Test
    fun `reps advances to weight of the next incomplete set`() {
        val sets = listOf(set("set-1"), set("set-2"), set("set-3"))

        val target = WorkoutFocusOrder.next(sets, currentSetId = "set-1", currentField = SetInputField.REPS)

        assertThat(target).isEqualTo(SetFocusTarget("set-2", SetInputField.WEIGHT))
    }

    @Test
    fun `reps skips already-completed sets when looking for the next one`() {
        val sets = listOf(set("set-1"), set("set-2", completed = true), set("set-3"))

        val target = WorkoutFocusOrder.next(sets, currentSetId = "set-1", currentField = SetInputField.REPS)

        assertThat(target).isEqualTo(SetFocusTarget("set-3", SetInputField.WEIGHT))
    }

    @Test
    fun `reps of the last set returns null so Done just hides the keyboard`() {
        val sets = listOf(set("set-1"), set("set-2"))

        val target = WorkoutFocusOrder.next(sets, currentSetId = "set-2", currentField = SetInputField.REPS)

        assertThat(target).isNull()
    }

    @Test
    fun `reps returns null when every following set is already completed`() {
        val sets = listOf(set("set-1"), set("set-2", completed = true), set("set-3", completed = true))

        val target = WorkoutFocusOrder.next(sets, currentSetId = "set-1", currentField = SetInputField.REPS)

        assertThat(target).isNull()
    }

    @Test
    fun `unknown set id returns null instead of throwing`() {
        val sets = listOf(set("set-1"))

        val target = WorkoutFocusOrder.next(sets, currentSetId = "missing", currentField = SetInputField.REPS)

        assertThat(target).isNull()
    }
}
