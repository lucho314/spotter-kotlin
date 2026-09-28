package com.lucho314.spotter.domain.calc

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.GarminExerciseRef
import org.junit.Test

class GarminExerciseMapperTest {

    @Test
    fun `barbell bench press maps to category 0 subtype 1`() {
        val ref = GarminExerciseMapper.map(nameEn = "Barbell Bench Press", name = null, equipment = Equipment.BARBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(0, 1))
    }

    @Test
    fun `incline dumbbell bench press in Spanish maps to category 0 subtype 9`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Press de banca inclinado", equipment = Equipment.DUMBBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(0, 9))
    }

    @Test
    fun `goblet squat maps to category 28 subtype 37`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Sentadilla goblet", equipment = null)
        assertThat(ref).isEqualTo(GarminExerciseRef(28, 37))
    }

    @Test
    fun `leg press in Spanish maps to squat category with subtype 0`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Prensa de piernas", equipment = null)
        assertThat(ref).isEqualTo(GarminExerciseRef(28, 0))
    }

    @Test
    fun `leg curl in Spanish maps to leg curl category, not the general curl rule`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Curl femoral", equipment = Equipment.MACHINE)
        assertThat(ref).isEqualTo(GarminExerciseRef(15, 0))
    }

    @Test
    fun `romanian deadlift maps to deadlift category subtype 23, before the general deadlift rule`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Peso muerto rumano", equipment = Equipment.BARBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(8, 23))
    }

    @Test
    fun `lat pulldown in Spanish maps to pull up category with default subtype 13`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Jalón al pecho", equipment = Equipment.CABLE)
        assertThat(ref).isEqualTo(GarminExerciseRef(21, 13))
    }

    @Test
    fun `dominadas maps to pull up category subtype 38`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Dominadas", equipment = Equipment.BODYWEIGHT)
        assertThat(ref).isEqualTo(GarminExerciseRef(21, 38))
    }

    @Test
    fun `lateral raises with dumbbells map to category 14 subtype 34`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Elevaciones laterales", equipment = Equipment.DUMBBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(14, 34))
    }

    @Test
    fun `leg extension in Spanish has no Garmin category`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Extensión de cuádriceps", equipment = Equipment.MACHINE)
        assertThat(ref).isNull()
    }

    @Test
    fun `is insensitive to accents and case`() {
        val lower = GarminExerciseMapper.map(nameEn = null, name = "sentadilla", equipment = Equipment.BARBELL)
        val upperAccented = GarminExerciseMapper.map(nameEn = null, name = "SENTADILLA", equipment = Equipment.BARBELL)
        assertThat(lower).isEqualTo(upperAccented)
    }

    @Test
    fun `both names null returns null`() {
        assertThat(GarminExerciseMapper.map(nameEn = null, name = null, equipment = Equipment.BARBELL)).isNull()
    }

    @Test
    fun `both names blank returns null`() {
        assertThat(GarminExerciseMapper.map(nameEn = "  ", name = "", equipment = Equipment.BARBELL)).isNull()
    }

    @Test
    fun `hammer curl matches before the generic curl rule`() {
        val ref = GarminExerciseMapper.map(nameEn = "Hammer Curl", name = null, equipment = Equipment.DUMBBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(7, 16))
    }

    @Test
    fun `generic dumbbell curl maps to subtype 37`() {
        val ref = GarminExerciseMapper.map(nameEn = "Bicep Curl", name = null, equipment = Equipment.DUMBBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(7, 37))
    }

    @Test
    fun `barbell row maps to category 23 subtype 45`() {
        val ref = GarminExerciseMapper.map(nameEn = "Barbell Row", name = null, equipment = Equipment.BARBELL)
        assertThat(ref).isEqualTo(GarminExerciseRef(23, 45))
    }

    @Test
    fun `plank maps to category 19 subtype 43`() {
        val ref = GarminExerciseMapper.map(nameEn = null, name = "Plancha", equipment = null)
        assertThat(ref).isEqualTo(GarminExerciseRef(19, 43))
    }
}
