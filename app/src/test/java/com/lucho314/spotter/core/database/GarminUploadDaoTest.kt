package com.lucho314.spotter.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.dao.GARMIN_ENQUEUE_RESULT_ALREADY_PENDING
import com.lucho314.spotter.core.database.dao.GARMIN_ENQUEUE_RESULT_ALREADY_UPLOADED
import com.lucho314.spotter.core.database.dao.GARMIN_ENQUEUE_RESULT_ENQUEUED
import com.lucho314.spotter.core.database.dao.GarminUploadDao
import com.lucho314.spotter.core.database.entity.GarminUploadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GarminUploadDaoTest {

    private lateinit var database: SpotterDatabase
    private lateinit var dao: GarminUploadDao

    private fun entity(
        workoutId: String,
        userId: String = "user-1",
        status: String = "PENDING",
        attempts: Int = 0,
        payload: String? = "{}",
    ) = GarminUploadEntity(
        workoutId = workoutId, userId = userId, status = status, attempts = attempts, lastError = null,
        garminActivityId = null, garminUploadId = null, payloadJson = payload, createdAtEpochMs = 1000, updatedAtEpochMs = 1000,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        dao = database.garminUploadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enqueue on a new row inserts and returns ENQUEUED`() = runTest {
        val result = dao.enqueue(entity("w1"), requeueFailed = false)
        assertThat(result).isEqualTo(GARMIN_ENQUEUE_RESULT_ENQUEUED)
        assertThat(dao.get("w1")).isNotNull()
    }

    @Test
    fun `enqueue on an UPLOADED row returns ALREADY_UPLOADED without touching it`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.markUploaded("w1", activityId = 42L, uploadId = 7L, now = 2000)

        val result = dao.enqueue(entity("w1"), requeueFailed = true)

        assertThat(result).isEqualTo(GARMIN_ENQUEUE_RESULT_ALREADY_UPLOADED)
        assertThat(dao.get("w1")!!.garminActivityId).isEqualTo(42L)
    }

    @Test
    fun `enqueue on a PENDING row fills a missing payload and returns ALREADY_PENDING`() = runTest {
        dao.enqueue(entity("w1", payload = null), requeueFailed = false)

        val result = dao.enqueue(entity("w1", payload = "{\"a\":1}"), requeueFailed = false)

        assertThat(result).isEqualTo(GARMIN_ENQUEUE_RESULT_ALREADY_PENDING)
        assertThat(dao.get("w1")!!.payloadJson).isEqualTo("{\"a\":1}")
    }

    @Test
    fun `enqueue on a FAILED row with requeueFailed true resets it to PENDING and returns ENQUEUED`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.markFailed("w1", "boom", now = 2000)

        val result = dao.enqueue(entity("w1"), requeueFailed = true)

        assertThat(result).isEqualTo(GARMIN_ENQUEUE_RESULT_ENQUEUED)
        val row = dao.get("w1")!!
        assertThat(row.status).isEqualTo("PENDING")
        assertThat(row.attempts).isEqualTo(0)
        assertThat(row.lastError).isNull()
    }

    @Test
    fun `enqueue on a FAILED row with requeueFailed false leaves it untouched, returns ALREADY_PENDING`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.markFailed("w1", "boom", now = 2000)

        val result = dao.enqueue(entity("w1"), requeueFailed = false)

        assertThat(result).isEqualTo(GARMIN_ENQUEUE_RESULT_ALREADY_PENDING)
        assertThat(dao.get("w1")!!.status).isEqualTo("FAILED")
    }

    @Test
    fun `markUploaded clears the payload and last error`() = runTest {
        dao.enqueue(entity("w1", payload = "{}"), requeueFailed = false)

        dao.markUploaded("w1", activityId = 1L, uploadId = 2L, now = 2000)

        val row = dao.get("w1")!!
        assertThat(row.status).isEqualTo("UPLOADED")
        assertThat(row.payloadJson).isNull()
        assertThat(row.garminActivityId).isEqualTo(1L)
        assertThat(row.garminUploadId).isEqualTo(2L)
    }

    @Test
    fun `recordAttempt increments attempts without changing status`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)

        dao.recordAttempt("w1", "network", now = 2000)
        dao.recordAttempt("w1", "network", now = 3000)

        val row = dao.get("w1")!!
        assertThat(row.attempts).isEqualTo(2)
        assertThat(row.status).isEqualTo("PENDING")
        assertThat(row.lastError).isEqualTo("network")
    }

    @Test
    fun `markFailed sets status FAILED and increments attempts`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.recordAttempt("w1", "network", now = 2000)

        dao.markFailed("w1", "invalid_file:400", now = 3000)

        val row = dao.get("w1")!!
        assertThat(row.status).isEqualTo("FAILED")
        assertThat(row.attempts).isEqualTo(2)
        assertThat(row.lastError).isEqualTo("invalid_file:400")
    }

    @Test
    fun `resetFailedToPending only resets FAILED rows for that user and returns the count`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.markFailed("w1", "x", now = 2000)
        dao.enqueue(entity("w2"), requeueFailed = false)
        dao.markFailed("w2", "x", now = 2000)
        dao.enqueue(entity("w3", userId = "user-2"), requeueFailed = false)
        dao.markFailed("w3", "x", now = 2000)

        val count = dao.resetFailedToPending("user-1", now = 3000)

        assertThat(count).isEqualTo(2)
        assertThat(dao.get("w1")!!.status).isEqualTo("PENDING")
        assertThat(dao.get("w2")!!.status).isEqualTo("PENDING")
        assertThat(dao.get("w3")!!.status).isEqualTo("FAILED")
    }

    @Test
    fun `deleteNotUploaded keeps UPLOADED rows but removes PENDING and FAILED ones for that user`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.markUploaded("w1", null, null, now = 2000)
        dao.enqueue(entity("w2"), requeueFailed = false)
        dao.enqueue(entity("w3"), requeueFailed = false)
        dao.markFailed("w3", "x", now = 2000)
        dao.enqueue(entity("w4", userId = "user-2"), requeueFailed = false)

        dao.deleteNotUploaded("user-1")

        assertThat(dao.get("w1")).isNotNull()
        assertThat(dao.get("w2")).isNull()
        assertThat(dao.get("w3")).isNull()
        assertThat(dao.get("w4")).isNotNull()
    }

    @Test
    fun `observeFailedCount reflects only FAILED rows for that user`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.enqueue(entity("w2"), requeueFailed = false)
        dao.markFailed("w1", "x", now = 2000)

        assertThat(dao.observeFailedCount("user-1").first()).isEqualTo(1)

        dao.markFailed("w2", "x", now = 2000)
        assertThat(dao.observeFailedCount("user-1").first()).isEqualTo(2)
    }

    @Test
    fun `getPending only returns PENDING rows for that user`() = runTest {
        dao.enqueue(entity("w1"), requeueFailed = false)
        dao.enqueue(entity("w2", userId = "user-2"), requeueFailed = false)
        dao.enqueue(entity("w3"), requeueFailed = false)
        dao.markUploaded("w3", null, null, now = 2000)

        val pending = dao.getPending("user-1")

        assertThat(pending.map { it.workoutId }).containsExactly("w1")
    }
}
