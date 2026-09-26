package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.data.remote.dto.MuscleGroupDto
import com.lucho314.spotter.domain.model.Difficulty
import com.lucho314.spotter.domain.model.Equipment
import org.junit.Test

class ExerciseMapperTest {

    @Test
    fun `an unrecognized equipment value maps to OTHER`() {
        val dto = ExerciseDto(
            id = 1,
            name = "Máquina rara",
            nameEn = "Weird machine",
            muscleGroupId = 1,
            equipment = "some_future_equipment",
        )

        assertThat(dto.toDomain().equipment).isEqualTo(Equipment.OTHER)
    }

    @Test
    fun `a null difficulty maps to null, not a default`() {
        val dto = ExerciseDto(id = 1, name = "X", nameEn = "X", muscleGroupId = 1, equipment = "barbell", difficulty = null)

        assertThat(dto.toDomain().difficulty).isNull()
    }

    @Test
    fun `gif_url maps to the domain mediaUrl field`() {
        val dto = ExerciseDto(
            id = 1,
            name = "X",
            nameEn = "X",
            muscleGroupId = 1,
            equipment = "barbell",
            gifUrl = "https://example.com/x.mp4",
        )

        assertThat(dto.toDomain().mediaUrl).isEqualTo("https://example.com/x.mp4")
    }

    @Test
    fun `nested muscle group is mapped`() {
        val dto = ExerciseDto(
            id = 1,
            name = "X",
            nameEn = "X",
            muscleGroupId = 1,
            equipment = "barbell",
            muscleGroup = MuscleGroupDto(id = 1, name = "Pecho", nameEn = "Chest"),
        )

        val domain = dto.toDomain()
        assertThat(domain.muscleGroup?.name).isEqualTo("Pecho")
    }

    @Test
    fun `null secondaryMuscles and instructions become empty lists`() {
        val dto = ExerciseDto(id = 1, name = "X", nameEn = "X", muscleGroupId = 1, equipment = "barbell")

        val domain = dto.toDomain()
        assertThat(domain.secondaryMuscles).isEmpty()
        assertThat(domain.instructions).isEmpty()
    }
}
