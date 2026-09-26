package com.lucho314.spotter.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
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
class CachedPayloadDaoTest {

    private lateinit var database: SpotterDatabase
    private lateinit var dao: CachedPayloadDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        dao = database.cachedPayloadDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `get returns null when there is no matching row`() = runTest {
        assertThat(dao.get("routines:active", "user-1")).isNull()
    }

    @Test
    fun `upsert then get round-trips the payload`() = runTest {
        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[]", 1000L))

        val row = dao.get("routines:active", "user-1")

        assertThat(row?.json).isEqualTo("[]")
    }

    @Test
    fun `upsert replaces the previous row for the same key`() = runTest {
        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[]", 1000L))
        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[1,2,3]", 2000L))

        val row = dao.get("routines:active", "user-1")

        assertThat(row?.json).isEqualTo("[1,2,3]")
        assertThat(row?.updatedAtEpochMs).isEqualTo(2000L)
    }

    @Test
    fun `observe emits the current row and updates on upsert`() = runTest {
        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[]", 1000L))

        assertThat(dao.observe("routines:active", "user-1").first()?.json).isEqualTo("[]")

        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[1]", 2000L))

        assertThat(dao.observe("routines:active", "user-1").first()?.json).isEqualTo("[1]")
    }

    @Test
    fun `delete removes only the matching key`() = runTest {
        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[]", 1000L))
        dao.upsert(CachedPayloadEntity("routines:archived", "user-1", "[]", 1000L))

        dao.delete("routines:active")

        assertThat(dao.get("routines:active", "user-1")).isNull()
        assertThat(dao.get("routines:archived", "user-1")).isNotNull()
    }

    @Test
    fun `deleteByPrefix removes every key starting with the prefix`() = runTest {
        dao.upsert(CachedPayloadEntity("routine:r1", "", "{}", 1000L))
        dao.upsert(CachedPayloadEntity("routine:r2", "", "{}", 1000L))
        dao.upsert(CachedPayloadEntity("routines:active", "user-1", "[]", 1000L))

        dao.deleteByPrefix("routine:")

        assertThat(dao.get("routine:r1", "")).isNull()
        assertThat(dao.get("routine:r2", "")).isNull()
        assertThat(dao.get("routines:active", "user-1")).isNotNull()
    }
}
