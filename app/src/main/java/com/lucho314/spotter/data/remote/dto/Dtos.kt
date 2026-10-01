package com.lucho314.spotter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the live Supabase schema (see `docs/MIGRATION_PLAN.md` section 6, verified
 * against the live REST API, not the outdated `.sql` files in the source repo). All `@Serializable`
 * with `@SerialName` in snake_case matching the DB columns.
 *
 * Conventions:
 * - `numeric` -> [Double]; `smallint`/`integer` -> [Int].
 * - `timestamptz` -> [String] here; mappers parse with `OffsetDateTime.parse(s).toInstant()`.
 * - `date` -> `String "yyyy-MM-dd"`; mappers parse to `LocalDate`.
 * - Insert DTOs never give a Kotlin default to a field that must always be sent. Nullable fields
 *   are omitted on encode (`explicitNulls = false`, see `SupabaseModule.provideJson`) so the DB's
 *   own default/nullability applies.
 * - `routine_exercises.day_number` is `integer NOT NULL default 1` live (verified in
 *   `docs/backend/live_schema_2026-09-26.md`) - it is **never** nullable on the wire, in either
 *   direction. A `RoutineExercise`/`NewRoutineExercise` with no day assigned is represented by a
 *   `day_number` that has no matching row in `routine_days` (see
 *   `domain.model.UNASSIGNED_DAY_NUMBER`), never by a JSON `null` - sending `null` for this column
 *   fails with `23502` (not-null violation).
 */

@Serializable
data class MuscleGroupDto(
    val id: Int,
    val name: String,
    @SerialName("name_en") val nameEn: String,
)

@Serializable
data class ExerciseDto(
    val id: Int,
    val name: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("muscle_group_id") val muscleGroupId: Int,
    val equipment: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("gif_url") val gifUrl: String? = null,
    @SerialName("secondary_muscles") val secondaryMuscles: List<String>? = null,
    val instructions: List<String>? = null,
    val difficulty: String? = null,
    val category: String? = null,
    @SerialName("exercisedb_id") val exercisedbId: String? = null,
    @SerialName("muscle_groups") val muscleGroup: MuscleGroupDto? = null,
)

@Serializable
data class IdDto(val id: String)

@Serializable
data class RoutineDayDto(
    val id: String,
    @SerialName("routine_id") val routineId: String,
    @SerialName("day_number") val dayNumber: Int,
    val name: String,
)

@Serializable
data class RoutineExerciseDto(
    val id: String,
    @SerialName("routine_id") val routineId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("day_number") val dayNumber: Int,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps") val targetReps: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
    @SerialName("exercises") val exercise: ExerciseDto? = null,
)

@Serializable
data class RoutineSummaryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("days_per_week") val daysPerWeek: Int? = null,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("source_template_id") val sourceTemplateId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("routine_exercises") val routineExercises: List<IdDto> = emptyList(),
    @SerialName("routine_days") val routineDays: List<RoutineDayDto> = emptyList(),
)

@Serializable
data class RoutineDetailDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("days_per_week") val daysPerWeek: Int? = null,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("source_template_id") val sourceTemplateId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("routine_exercises") val routineExercises: List<RoutineExerciseDto> = emptyList(),
    @SerialName("routine_days") val routineDays: List<RoutineDayDto> = emptyList(),
)

@Serializable
data class RoutineRefDto(
    val id: String,
    val name: String,
)

@Serializable
data class WorkoutSetDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("set_number") val setNumber: Int,
    @SerialName("weight_kg") val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    @SerialName("is_warmup") val isWarmup: Boolean,
    @SerialName("completed_at") val completedAt: String,
    @SerialName("exercises") val exercise: ExerciseDto? = null,
)

@Serializable
data class WorkoutSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("routine_id") val routineId: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    val notes: String? = null,
    val status: String,
    @SerialName("routines") val routine: RoutineRefDto? = null,
    @SerialName("workout_sets") val sets: List<WorkoutSetDto> = emptyList(),
)

@Serializable
data class PersonalRecordDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("best_weight_kg") val bestWeightKg: Double,
    @SerialName("best_reps_at_weight") val bestRepsAtWeight: Int,
    @SerialName("estimated_1rm") val estimated1Rm: Double,
    @SerialName("achieved_at") val achievedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("exercises") val exercise: ExerciseDto? = null,
)

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("height_cm") val heightCm: Int? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("fitness_goal") val fitnessGoal: String? = null,
)

@Serializable
data class SharedRoutineDto(
    val id: String,
    @SerialName("routine_id") val routineId: String,
    @SerialName("shared_by") val sharedBy: String,
    @SerialName("share_code") val shareCode: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("routines") val routine: RoutineDetailDto? = null,
)

/**
 * Lightweight `getSharedRoutine` select: no ids of the sharer/routine owner, no exercise catalog
 * join - the previous query (`*, routines(*, routine_days(*), routine_exercises(*, exercises(*,
 * muscle_groups(*)))))`) pulled all of that for no reason (section 2, finding 4).
 */
@Serializable
data class SharedRoutineImportDto(
    @SerialName("share_code") val shareCode: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("routines") val routine: SharedRoutineBodyDto? = null,
)

@Serializable
data class SharedRoutineBodyDto(
    val name: String,
    val description: String? = null,
    @SerialName("days_per_week") val daysPerWeek: Int? = null,
    @SerialName("routine_days") val routineDays: List<SharedRoutineDayDto> = emptyList(),
    @SerialName("routine_exercises") val routineExercises: List<SharedRoutineExerciseDto> = emptyList(),
)

@Serializable
data class SharedRoutineDayDto(
    @SerialName("day_number") val dayNumber: Int,
    val name: String,
)

@Serializable
data class SharedRoutineExerciseDto(
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("day_number") val dayNumber: Int,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps") val targetReps: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
)

@Serializable
data class RoutineTemplateDto(
    val id: String,
    val name: String,
    @SerialName("name_es") val nameEs: String,
    val description: String? = null,
    @SerialName("description_es") val descriptionEs: String? = null,
    val goal: String,
    val difficulty: String,
    @SerialName("days_per_week") val daysPerWeek: Int,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("template_days") val days: List<TemplateDayDto> = emptyList(),
)

@Serializable
data class TemplateDayDto(
    val id: String,
    @SerialName("template_id") val templateId: String,
    @SerialName("day_number") val dayNumber: Int,
    val name: String,
    @SerialName("name_es") val nameEs: String,
    val description: String? = null,
    @SerialName("template_day_exercises") val exercises: List<TemplateDayExerciseDto> = emptyList(),
)

@Serializable
data class TemplateDayExerciseDto(
    val id: String,
    @SerialName("template_day_id") val templateDayId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps") val targetReps: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
    val notes: String? = null,
    @SerialName("exercises") val exercise: ExerciseDto? = null,
)

// --- Inserts: no Kotlin defaults on fields that must always be sent. ---

@Serializable
data class RoutineInsertDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String?,
    @SerialName("days_per_week") val daysPerWeek: Int?,
    @SerialName("source_template_id") val sourceTemplateId: String?,
)

@Serializable
data class RoutineExerciseInsertDto(
    @SerialName("routine_id") val routineId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("day_number") val dayNumber: Int,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps") val targetReps: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
)

@Serializable
data class RoutineDayInsertDto(
    @SerialName("routine_id") val routineId: String,
    @SerialName("day_number") val dayNumber: Int,
    val name: String,
)

@Serializable
data class WorkoutSessionInsertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("routine_id") val routineId: String?,
    val status: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("completed_at") val completedAt: String,
    val notes: String?,
)

@Serializable
data class WorkoutSetInsertDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    @SerialName("set_number") val setNumber: Int,
    @SerialName("weight_kg") val weightKg: Double,
    val reps: Int,
    @SerialName("is_warmup") val isWarmup: Boolean,
    @SerialName("completed_at") val completedAt: String,
)

@Serializable
data class WorkoutExerciseNoteInsertDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: Int,
    val note: String,
)

@Serializable
data class WorkoutExerciseNoteDto(
    val note: String,
)

@Serializable
data class WorkoutExerciseNoteRowDto(
    @SerialName("exercise_id") val exerciseId: Int,
    val note: String,
)

@Serializable
data class SharedRoutineInsertDto(
    @SerialName("routine_id") val routineId: String,
    @SerialName("shared_by") val sharedBy: String,
    @SerialName("share_code") val shareCode: String,
)

@Serializable
data class ParseRoutineImageRequest(
    @SerialName("image_base64") val imageBase64: String,
    @SerialName("image_mime_type") val mimeType: String,
)

@Serializable
data class ParseRoutineImageResponse(
    @SerialName("routine_id") val routineId: String? = null,
    @SerialName("routine_name") val routineName: String? = null,
    val error: String? = null,
    val code: String? = null,
)
