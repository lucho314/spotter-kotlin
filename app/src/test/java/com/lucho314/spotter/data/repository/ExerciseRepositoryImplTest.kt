package com.lucho314.spotter.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppResult
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.core.database.entity.CachedPayloadEntity
import com.lucho314.spotter.core.network.SupabaseModule
import com.lucho314.spotter.data.remote.dto.ExerciseDto
import com.lucho314.spotter.testutil.FakeExerciseRemoteDataSource
import com.lucho314.spotter.testutil.FakeTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ExerciseRepositoryImplTest {

    private lateinit var database: SpotterDatabase
    private lateinit var remote: FakeExerciseRemoteDataSource
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: ExerciseRepositoryImpl

    private val json = SupabaseModule.provideJson()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        remote = FakeExerciseRemoteDataSource()
        timeProvider = FakeTimeProvider()
        val dispatcher = UnconfinedTestDispatcher()
        repository = ExerciseRepositoryImpl(remote, database.cachedPayloadDao(), json, timeProvider, dispatcher, dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun exercise(id: Int) = ExerciseDto(id = id, name = "Ex $id", nameEn = "Ex $id", muscleGroupId = 1, equipment = "barbell")

    @Test
    fun `refreshCatalog without force fetches the network on a cold cache`() = runTest {
        remote.catalog = listOf(exercise(1))

        repository.refreshCatalog(force = false)

        assertThat(remote.getCatalogCallCount).isEqualTo(1)
        assertThat(repository.observeCatalog().first().map { it.id }).containsExactly(1)
    }

    @Test
    fun `refreshCatalog without force skips the network while the cache is within the 24h TTL`() = runTest {
        remote.catalog = listOf(exercise(1))
        repository.refreshCatalog(force = false)
        assertThat(remote.getCatalogCallCount).isEqualTo(1)

        timeProvider.instant = timeProvider.instant.plusSeconds(23 * 3600) // 23h later: still fresh
        remote.catalog = listOf(exercise(1), exercise(2))
        repository.refreshCatalog(force = false)

        assertThat(remote.getCatalogCallCount).isEqualTo(1) // no second network call
        assertThat(repository.observeCatalog().first().map { it.id }).containsExactly(1)
    }

    @Test
    fun `refreshCatalog without force re-fetches once the 24h TTL has elapsed`() = runTest {
        remote.catalog = listOf(exercise(1))
        repository.refreshCatalog(force = false)

        timeProvider.instant = timeProvider.instant.plusSeconds(25 * 3600) // past the TTL
        remote.catalog = listOf(exercise(1), exercise(2))
        repository.refreshCatalog(force = false)

        assertThat(remote.getCatalogCallCount).isEqualTo(2)
        assertThat(repository.observeCatalog().first().map { it.id }).containsExactly(1, 2)
    }

    @Test
    fun `force=true always re-fetches, even with a fresh cache`() = runTest {
        remote.catalog = listOf(exercise(1))
        repository.refreshCatalog(force = false)

        remote.catalog = listOf(exercise(1), exercise(2))
        repository.refreshCatalog(force = true)

        assertThat(remote.getCatalogCallCount).isEqualTo(2)
        assertThat(repository.observeCatalog().first().map { it.id }).containsExactly(1, 2)
    }

    @Test
    fun `a corrupt cached catalog is treated as empty and the row is dropped`() = runTest {
        database.cachedPayloadDao().upsert(CachedPayloadEntity("exercises:catalog", "", "{ not a valid list }", 0L))

        val cached = repository.observeCatalog().first()

        assertThat(cached).isEmpty()
        assertThat(database.cachedPayloadDao().get("exercises:catalog", "")).isNull()
    }

    @Test
    fun `getExercise falls back to the network when not in the cached catalog`() = runTest {
        remote.exercise = exercise(99)

        val result = repository.getExercise(99)

        assertThat((result as? AppResult.Success)?.value?.id).isEqualTo(99)
    }
}
