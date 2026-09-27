package com.lucho314.spotter.data.remote.dto

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.network.SupabaseModule
import com.lucho314.spotter.domain.model.UNASSIGNED_DAY_NUMBER
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.encodeToString
import org.junit.Test

/** The actual production config, not a copy that could silently drift from it. */
private val json = SupabaseModule.provideJson()

class DtoDecodingTest {

    @Test
    fun `decodes a live-shaped exercises row with an mp4 gif_url, empty arrays and null difficulty`() {
        val raw = """
            {
              "id": 42,
              "name": "Press de banca",
              "name_en": "Bench Press",
              "muscle_group_id": 1,
              "equipment": "barbell",
              "image_url": "https://example.com/bench.png",
              "gif_url": "https://example.com/bench.mp4",
              "secondary_muscles": [],
              "instructions": null,
              "difficulty": null,
              "category": "compound",
              "exercisedb_id": "abc123",
              "an_unknown_future_column": "should be ignored"
            }
        """.trimIndent()

        val dto = json.decodeFromString<ExerciseDto>(raw)

        assertThat(dto.id).isEqualTo(42)
        assertThat(dto.gifUrl).isEqualTo("https://example.com/bench.mp4")
        assertThat(dto.secondaryMuscles).isEmpty()
        assertThat(dto.instructions).isNull()
        assertThat(dto.difficulty).isNull()
        assertThat(dto.muscleGroup).isNull()
    }

    @Test
    fun `decodes nested muscle_groups and routine relations`() {
        val raw = """
            {
              "id": 42, "name": "Press de banca", "name_en": "Bench Press",
              "muscle_group_id": 1, "equipment": "barbell",
              "muscle_groups": { "id": 1, "name": "Pecho", "name_en": "Chest" }
            }
        """.trimIndent()

        val dto = json.decodeFromString<ExerciseDto>(raw)

        assertThat(dto.muscleGroup).isEqualTo(MuscleGroupDto(id = 1, name = "Pecho", nameEn = "Chest"))
    }

    @Test
    fun `routine_exercises day_number decodes as a plain non-null Int`() {
        // day_number is `integer NOT NULL default 1` live (docs/backend/live_schema_2026-09-26.md):
        // it is always present on the wire, in either direction.
        val raw = """
            {
              "id": "re-1", "routine_id": "r-1", "exercise_id": 42, "sort_order": 0, "day_number": 1,
              "target_sets": 3, "target_reps": 10, "rest_seconds": 90
            }
        """.trimIndent()

        val dto = json.decodeFromString<RoutineExerciseDto>(raw)

        assertThat(dto.dayNumber).isEqualTo(1)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun `decoding routine_exercises without day_number fails loudly instead of silently defaulting`() {
        val raw = """
            {
              "id": "re-1", "routine_id": "r-1", "exercise_id": 42, "sort_order": 0,
              "target_sets": 3, "target_reps": 10, "rest_seconds": 90
            }
        """.trimIndent()

        try {
            json.decodeFromString<RoutineExerciseDto>(raw)
            throw AssertionError("Expected decoding to fail: day_number has no Kotlin default")
        } catch (e: MissingFieldException) {
            // Expected: this DTO must never treat a missing day_number as "no day" (bug fixed in
            // FASE 2 review: the DB column is NOT NULL, so a JSON null/absence here is a real bug,
            // not a valid "unassigned" representation).
        }
    }

    @Test
    fun `RoutineExerciseInsertDto always encodes day_number, even the unassigned sentinel`() {
        val dto = RoutineExerciseInsertDto(
            routineId = "r1",
            exerciseId = 42,
            sortOrder = 0,
            dayNumber = UNASSIGNED_DAY_NUMBER,
            targetSets = 3,
            targetReps = 10,
            restSeconds = 90,
        )

        val encoded = json.encodeToString(dto)

        assertThat(encoded).contains("\"day_number\":0")
    }

    @Test
    fun `explicitNulls=false omits null fields when encoding an insert DTO`() {
        val dto = RoutineInsertDto(userId = "u1", name = "Push", description = null, daysPerWeek = null, sourceTemplateId = null)

        val encoded = json.encodeToString(dto)

        assertThat(encoded).doesNotContain("description")
        assertThat(encoded).doesNotContain("days_per_week")
        assertThat(encoded).doesNotContain("source_template_id")
        assertThat(encoded).contains("\"name\":\"Push\"")
    }

    @Test
    fun `decodes a routine summary with routine_days and routine_exercises id-only relations`() {
        val raw = """
            {
              "id": "r1", "user_id": "u1", "name": "Push", "is_archived": false,
              "created_at": "2026-01-15T10:00:00+00:00", "updated_at": "2026-01-15T10:00:00+00:00",
              "routine_exercises": [{ "id": "re1" }, { "id": "re2" }],
              "routine_days": [{ "id": "d1", "routine_id": "r1", "day_number": 1, "name": "Lunes" }]
            }
        """.trimIndent()

        val dto = json.decodeFromString<RoutineSummaryDto>(raw)

        assertThat(dto.routineExercises).hasSize(2)
        assertThat(dto.routineDays.single().name).isEqualTo("Lunes")
    }

    @Test
    fun `decodes the lightweight shared-routine-import select`() {
        val raw = """
            {
              "share_code": "K7MN3QXP", "is_active": true, "expires_at": null,
              "routines": {
                "name": "Push", "description": "Programa de fuerza", "days_per_week": 3,
                "routine_days": [{ "day_number": 1, "name": "Lunes" }],
                "routine_exercises": [
                  { "exercise_id": 1, "day_number": 1, "sort_order": 0, "target_sets": 3, "target_reps": 10, "rest_seconds": 90 }
                ]
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<SharedRoutineImportDto>(raw)

        assertThat(dto.isActive).isTrue()
        assertThat(dto.routine?.name).isEqualTo("Push")
        assertThat(dto.routine?.routineDays?.single()?.name).isEqualTo("Lunes")
        assertThat(dto.routine?.routineExercises?.single()?.exerciseId).isEqualTo(1)
    }
}
