package com.lucho314.spotter.data.garmin.fit

import com.garmin.fit.Decode
import com.garmin.fit.File
import com.garmin.fit.FitDecoder
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminExerciseRef
import com.lucho314.spotter.domain.model.GarminPlannedSet
import com.lucho314.spotter.domain.model.GarminSetKind
import com.lucho314.spotter.domain.model.WeightUnit
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.CRC32
import org.junit.Test

class StrengthActivityFitEncoderTest {

    private val encoder = StrengthActivityFitEncoder()

    private fun samplePlan(workoutId: String = "workout-1"): GarminActivityPlan {
        val start = Instant.ofEpochSecond(1_768_000_000)
        return GarminActivityPlan(
            workoutId = workoutId,
            startTime = start,
            endTime = start.plusSeconds(100),
            utcOffsetSeconds = -10_800,
            sets = listOf(
                GarminPlannedSet(
                    kind = GarminSetKind.ACTIVE, startTime = start, endTime = start.plusSeconds(30),
                    repetitions = 10, weightKg = 80.0, exercise = GarminExerciseRef(0, 1), weightUnit = WeightUnit.KG,
                ),
                GarminPlannedSet(
                    kind = GarminSetKind.REST, startTime = start.plusSeconds(30), endTime = start.plusSeconds(90),
                    repetitions = null, weightKg = null, exercise = null, weightUnit = null,
                ),
                GarminPlannedSet(
                    kind = GarminSetKind.ACTIVE, startTime = start.plusSeconds(90), endTime = start.plusSeconds(100),
                    repetitions = 8, weightKg = 60.0, exercise = GarminExerciseRef(28, 6), weightUnit = WeightUnit.KG,
                ),
            ),
        )
    }

    @Test
    fun `the encoded file passes the official decoder's integrity check`() {
        val bytes = encoder.encode(samplePlan())
        assertThat(Decode().checkFileIntegrity(ByteArrayInputStream(bytes))).isTrue()
    }

    @Test
    fun `file_id has activity type, development manufacturer and a CRC32 serial derived from the workout id`() {
        val bytes = encoder.encode(samplePlan("workout-1"))
        val messages = FitDecoder().decode(ByteArrayInputStream(bytes))

        val fileId = messages.fileIdMesgs.single()
        assertThat(fileId.type).isEqualTo(File.ACTIVITY)
        assertThat(fileId.manufacturer).isEqualTo(255)
        assertThat(fileId.product).isEqualTo(0)
        val expectedCrc = CRC32().apply { update("workout-1".toByteArray(Charsets.UTF_8)) }.value
        assertThat(fileId.serialNumber).isEqualTo(expectedCrc)
    }

    @Test
    fun `a workout id whose CRC32 is 0 falls back to serial 1`() {
        // An empty byte array has CRC32 = 0, exercising the uint32z-can't-be-0 fallback.
        val bytes = encoder.encode(samplePlan(""))
        val messages = FitDecoder().decode(ByteArrayInputStream(bytes))
        assertThat(messages.fileIdMesgs.single().serialNumber).isEqualTo(1L)
    }

    @Test
    fun `session is TRAINING strength_training with the right start and elapsed time, one lap and one activity message`() {
        val plan = samplePlan()
        val bytes = encoder.encode(plan)
        val messages = FitDecoder().decode(ByteArrayInputStream(bytes))

        val session = messages.sessionMesgs.single()
        assertThat(session.sport).isEqualTo(Sport.TRAINING)
        assertThat(session.subSport).isEqualTo(SubSport.STRENGTH_TRAINING)
        assertThat(session.startTime.instant.epochSecond).isEqualTo(plan.startTime.epochSecond)
        assertThat(session.totalElapsedTime).isWithin(0.01f).of(100f)

        assertThat(messages.lapMesgs).hasSize(1)
        assertThat(messages.activityMesgs).hasSize(1)
    }

    @Test
    fun `set count matches the plan, active sets carry reps weight and category, rest sets don't`() {
        val plan = samplePlan()
        val bytes = encoder.encode(plan)
        val messages = FitDecoder().decode(ByteArrayInputStream(bytes))

        assertThat(messages.setMesgs).hasSize(3)

        val active1 = messages.setMesgs[0]
        assertThat(active1.setType.toInt()).isEqualTo(1)
        assertThat(active1.repetitions).isEqualTo(10)
        assertThat(active1.weight).isWithin(1f / 16f).of(80f)
        assertThat(active1.getCategory(0)).isEqualTo(0)
        assertThat(active1.getCategorySubtype(0)).isEqualTo(1)
        assertThat(active1.duration).isGreaterThan(0f)

        val rest = messages.setMesgs[1]
        assertThat(rest.setType.toInt()).isEqualTo(0)
        assertThat(rest.repetitions).isNull()
        assertThat(rest.weight).isNull()

        val active2 = messages.setMesgs[2]
        assertThat(active2.repetitions).isEqualTo(8)
        assertThat(active2.weight).isWithin(1f / 16f).of(60f)
        assertThat(active2.getCategory(0)).isEqualTo(28)
        assertThat(active2.getCategorySubtype(0)).isEqualTo(6)
    }

    @Test
    fun `encoding the same plan twice produces identical bytes`() {
        val plan = samplePlan()
        val first = encoder.encode(plan)
        val second = encoder.encode(plan)
        assertThat(first).isEqualTo(second)
    }
}
