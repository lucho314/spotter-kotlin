package com.lucho314.spotter.data.garmin.mapper

import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.GarminActivitySnapshot
import com.lucho314.spotter.domain.model.GarminSetSnapshot
import com.lucho314.spotter.domain.model.WeightUnit
import java.time.Instant
import kotlinx.serialization.Serializable

/** JSON-friendly mirror of [GarminActivitySnapshot], stored in `garmin_upload.payload_json`. */
@Serializable
data class GarminSnapshotJson(
    val workoutId: String,
    val userId: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val weightUnit: String,
    val sets: List<GarminSetSnapshotJson>,
)

@Serializable
data class GarminSetSnapshotJson(
    val exerciseId: Int,
    val exerciseName: String?,
    val equipment: String?,
    val exerciseOrder: Int,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val completedAtEpochMs: Long,
)

object GarminSnapshotMapper {

    fun toJson(snapshot: GarminActivitySnapshot): GarminSnapshotJson = GarminSnapshotJson(
        workoutId = snapshot.workoutId,
        userId = snapshot.userId,
        startedAtEpochMs = snapshot.startedAt.toEpochMilli(),
        completedAtEpochMs = snapshot.completedAt.toEpochMilli(),
        weightUnit = snapshot.weightUnit.name,
        sets = snapshot.sets.map { it.toJson() },
    )

    fun fromJson(json: GarminSnapshotJson): GarminActivitySnapshot = GarminActivitySnapshot(
        workoutId = json.workoutId,
        userId = json.userId,
        startedAt = Instant.ofEpochMilli(json.startedAtEpochMs),
        completedAt = Instant.ofEpochMilli(json.completedAtEpochMs),
        weightUnit = WeightUnit.valueOf(json.weightUnit),
        sets = json.sets.map { it.fromJson() },
    )

    private fun GarminSetSnapshot.toJson() = GarminSetSnapshotJson(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        equipment = equipment?.apiValue,
        exerciseOrder = exerciseOrder,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        isWarmup = isWarmup,
        completedAtEpochMs = completedAt.toEpochMilli(),
    )

    private fun GarminSetSnapshotJson.fromJson() = GarminSetSnapshot(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        equipment = equipment?.let { Equipment.fromApi(it) },
        exerciseOrder = exerciseOrder,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        isWarmup = isWarmup,
        completedAt = Instant.ofEpochMilli(completedAtEpochMs),
    )
}
