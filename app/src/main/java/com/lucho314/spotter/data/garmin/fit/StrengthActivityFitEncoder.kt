package com.lucho314.spotter.data.garmin.fit

import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminSetKind
import com.lucho314.spotter.domain.model.WeightUnit
import java.time.Duration
import java.util.zip.CRC32
import javax.inject.Inject

/**
 * FIT field/message constants used by [StrengthActivityFitEncoder], verified against FIT SDK
 * 21.217.0 (`FileIdMesg`, `EventMesg`, `SetMesg`, `LapMesg`, `SessionMesg`, `ActivityMesg`,
 * `Sport`/`SubSport`, `ExerciseCategory`).
 *
 * [MANUFACTURER] is `development(255)`, not a real Garmin device id: this encoder does not
 * impersonate any Garmin hardware (see the plan's open question 1).
 */
private object FitProfile {
    const val MANUFACTURER_DEVELOPMENT = 255L
    const val PRODUCT = 0L
    const val FILE_TYPE_ACTIVITY = 4L
    const val SPORT_TRAINING = 10L
    const val SUB_SPORT_STRENGTH_TRAINING = 20L
    const val MIN_ELAPSED_MS = 1000L
    const val WEIGHT_SCALE = 16.0
}

/**
 * Encodes a [GarminActivityPlan] into a FIT activity file, hand-written instead of using the
 * official (restrictively licensed) FIT Java SDK at runtime - see the plan's section 0, finding 4.
 * The same plan always produces the same bytes (see [com.lucho314.spotter.domain.calc.GarminActivityPlanner]),
 * which is what lets Garmin's own duplicate-activity detection work as a second idempotency layer.
 */
class StrengthActivityFitEncoder @Inject constructor() {

    fun encode(plan: GarminActivityPlan): ByteArray {
        val writer = FitWriter()
        val start = plan.startTime.toFitTime()
        val end = plan.endTime.toFitTime()
        val elapsedMs = maxOf(Duration.between(plan.startTime, plan.endTime).toMillis(), FitProfile.MIN_ELAPSED_MS)

        writeFileId(writer, start, serialNumber(plan.workoutId))
        writeEvent(writer, timestamp = start, eventType = EVENT_TYPE_START)
        writeSets(writer, plan)
        writer.write(LOCAL_EVENT, listOf(end, EVENT_TIMER, EVENT_TYPE_STOP_ALL))
        writeLap(writer, start, end, elapsedMs)
        writeSession(writer, start, end, elapsedMs)
        writeActivity(writer, end, elapsedMs, plan.utcOffsetSeconds)

        return writer.finish()
    }

    private fun writeFileId(writer: FitWriter, start: Long, serial: Long) {
        writer.define(
            LOCAL_FILE_ID,
            GLOBAL_FILE_ID,
            listOf(
                FitFieldDef(0, FitBaseType.ENUM),
                FitFieldDef(1, FitBaseType.UINT16),
                FitFieldDef(2, FitBaseType.UINT16),
                FitFieldDef(3, FitBaseType.UINT32Z),
                FitFieldDef(4, FitBaseType.UINT32),
            ),
        )
        writer.write(
            LOCAL_FILE_ID,
            listOf(FitProfile.FILE_TYPE_ACTIVITY, FitProfile.MANUFACTURER_DEVELOPMENT, FitProfile.PRODUCT, serial, start),
        )
    }

    private fun writeEvent(writer: FitWriter, timestamp: Long, eventType: Long) {
        writer.define(
            LOCAL_EVENT,
            GLOBAL_EVENT,
            listOf(FitFieldDef(253, FitBaseType.UINT32), FitFieldDef(0, FitBaseType.ENUM), FitFieldDef(1, FitBaseType.ENUM)),
        )
        writer.write(LOCAL_EVENT, listOf(timestamp, EVENT_TIMER, eventType))
    }

    private fun writeSets(writer: FitWriter, plan: GarminActivityPlan) {
        writer.define(
            LOCAL_SET,
            GLOBAL_SET,
            listOf(
                FitFieldDef(254, FitBaseType.UINT32),
                FitFieldDef(0, FitBaseType.UINT32),
                FitFieldDef(3, FitBaseType.UINT16),
                FitFieldDef(4, FitBaseType.UINT16),
                FitFieldDef(5, FitBaseType.UINT8),
                FitFieldDef(6, FitBaseType.UINT32),
                FitFieldDef(7, FitBaseType.UINT16),
                FitFieldDef(8, FitBaseType.UINT16),
                FitFieldDef(9, FitBaseType.UINT16),
                FitFieldDef(10, FitBaseType.UINT16),
            ),
        )
        plan.sets.forEachIndexed { index, set ->
            val startI = set.startTime.toFitTime()
            val endI = set.endTime.toFitTime()
            val durMs = Duration.between(set.startTime, set.endTime).toMillis()
            writer.write(
                LOCAL_SET,
                listOf(
                    endI,
                    durMs,
                    set.repetitions?.toLong(),
                    set.weightKg?.let { Math.round(it * FitProfile.WEIGHT_SCALE) },
                    if (set.kind == GarminSetKind.ACTIVE) SET_TYPE_ACTIVE else SET_TYPE_REST,
                    startI,
                    set.exercise?.category?.toLong(),
                    set.exercise?.subtype?.toLong(),
                    set.weightUnit?.let { if (it == WeightUnit.KG) WEIGHT_DISPLAY_KG else WEIGHT_DISPLAY_LB },
                    index.toLong(),
                ),
            )
        }
    }

    private fun writeLap(writer: FitWriter, start: Long, end: Long, elapsedMs: Long) {
        writer.define(
            LOCAL_LAP,
            GLOBAL_LAP,
            listOf(
                FitFieldDef(254, FitBaseType.UINT16),
                FitFieldDef(253, FitBaseType.UINT32),
                FitFieldDef(0, FitBaseType.ENUM),
                FitFieldDef(1, FitBaseType.ENUM),
                FitFieldDef(2, FitBaseType.UINT32),
                FitFieldDef(7, FitBaseType.UINT32),
                FitFieldDef(8, FitBaseType.UINT32),
                FitFieldDef(24, FitBaseType.ENUM),
                FitFieldDef(25, FitBaseType.ENUM),
                FitFieldDef(39, FitBaseType.ENUM),
            ),
        )
        writer.write(
            LOCAL_LAP,
            listOf(0L, end, EVENT_LAP, EVENT_TYPE_STOP, start, elapsedMs, elapsedMs, 7L, FitProfile.SPORT_TRAINING, FitProfile.SUB_SPORT_STRENGTH_TRAINING),
        )
    }

    private fun writeSession(writer: FitWriter, start: Long, end: Long, elapsedMs: Long) {
        writer.define(
            LOCAL_SESSION,
            GLOBAL_SESSION,
            listOf(
                FitFieldDef(254, FitBaseType.UINT16),
                FitFieldDef(253, FitBaseType.UINT32),
                FitFieldDef(0, FitBaseType.ENUM),
                FitFieldDef(1, FitBaseType.ENUM),
                FitFieldDef(2, FitBaseType.UINT32),
                FitFieldDef(5, FitBaseType.ENUM),
                FitFieldDef(6, FitBaseType.ENUM),
                FitFieldDef(7, FitBaseType.UINT32),
                FitFieldDef(8, FitBaseType.UINT32),
                FitFieldDef(25, FitBaseType.UINT16),
                FitFieldDef(26, FitBaseType.UINT16),
                FitFieldDef(28, FitBaseType.ENUM),
            ),
        )
        writer.write(
            LOCAL_SESSION,
            listOf(
                0L, end, EVENT_SESSION, EVENT_TYPE_STOP, start, FitProfile.SPORT_TRAINING, FitProfile.SUB_SPORT_STRENGTH_TRAINING,
                elapsedMs, elapsedMs, 0L, 1L, SESSION_TRIGGER_ACTIVITY_END,
            ),
        )
    }

    private fun writeActivity(writer: FitWriter, end: Long, elapsedMs: Long, utcOffsetSeconds: Int) {
        writer.define(
            LOCAL_ACTIVITY,
            GLOBAL_ACTIVITY,
            listOf(
                FitFieldDef(253, FitBaseType.UINT32),
                FitFieldDef(0, FitBaseType.UINT32),
                FitFieldDef(1, FitBaseType.UINT16),
                FitFieldDef(2, FitBaseType.ENUM),
                FitFieldDef(3, FitBaseType.ENUM),
                FitFieldDef(4, FitBaseType.ENUM),
                FitFieldDef(5, FitBaseType.UINT32),
            ),
        )
        writer.write(
            LOCAL_ACTIVITY,
            listOf(end, elapsedMs, 1L, ACTIVITY_TYPE_MANUAL, EVENT_ACTIVITY, EVENT_TYPE_STOP, end + utcOffsetSeconds),
        )
    }

    /** CRC32 of the UTF-8 workout id; `uint32z` can't be 0, so that one case falls back to 1. */
    private fun serialNumber(workoutId: String): Long {
        val crc = CRC32().apply { update(workoutId.toByteArray(Charsets.UTF_8)) }
        return crc.value.takeIf { it != 0L } ?: 1L
    }

    private companion object {
        const val LOCAL_FILE_ID = 0
        const val LOCAL_EVENT = 1
        const val LOCAL_SET = 2
        const val LOCAL_LAP = 3
        const val LOCAL_SESSION = 4
        const val LOCAL_ACTIVITY = 5

        const val GLOBAL_FILE_ID = 0
        const val GLOBAL_EVENT = 21
        const val GLOBAL_SET = 225
        const val GLOBAL_LAP = 19
        const val GLOBAL_SESSION = 18
        const val GLOBAL_ACTIVITY = 34

        const val EVENT_TIMER = 0L
        const val EVENT_TYPE_START = 0L
        const val EVENT_TYPE_STOP = 1L
        const val EVENT_TYPE_STOP_ALL = 4L
        const val EVENT_LAP = 9L
        const val EVENT_SESSION = 8L
        const val EVENT_ACTIVITY = 26L
        const val SESSION_TRIGGER_ACTIVITY_END = 0L
        const val ACTIVITY_TYPE_MANUAL = 0L

        const val SET_TYPE_ACTIVE = 1L
        const val SET_TYPE_REST = 0L
        const val WEIGHT_DISPLAY_KG = 1L
        const val WEIGHT_DISPLAY_LB = 2L
    }
}
