package com.lucho314.spotter.data.garmin

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.garmin.fit.StrengthActivityFitEncoder
import com.lucho314.spotter.data.garmin.local.StoredGarminTokens
import com.lucho314.spotter.data.garmin.remote.DiTokens
import com.lucho314.spotter.data.garmin.remote.GarminActivityRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.UploadHttpResult
import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminExerciseRef
import com.lucho314.spotter.domain.model.GarminPlannedSet
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminSetKind
import com.lucho314.spotter.domain.model.GarminUploadOutcome
import com.lucho314.spotter.domain.model.WeightUnit
import com.lucho314.spotter.testutil.FakeGarminAuthRemoteDataSource
import com.lucho314.spotter.testutil.FakeGarminTokenStore
import com.lucho314.spotter.testutil.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val USER_ID = "user-1"

private class FakeGarminActivityRemoteDataSource : GarminActivityRemoteDataSource {
    val results = ArrayDeque<UploadHttpResult>()
    val tokensUsed = mutableListOf<String>()
    var uploadCallCount = 0
        private set

    override suspend fun upload(accessToken: String, fileName: String, bytes: ByteArray): UploadHttpResult {
        uploadCallCount++
        tokensUsed += accessToken
        return results.removeFirst()
    }
}

class GarminActivityRepositoryImplTest {

    private val store = FakeGarminTokenStore()
    private val authRemote = FakeGarminAuthRemoteDataSource()
    private val timeProvider = FakeTimeProvider(instant = Instant.ofEpochSecond(1_000_000))
    private val tokenManager = GarminTokenManager(store, authRemote, timeProvider)
    private val activityRemote = FakeGarminActivityRemoteDataSource()
    private val repository = GarminActivityRepositoryImpl(StrengthActivityFitEncoder(), tokenManager, activityRemote)

    private fun connectedTokens() = StoredGarminTokens(
        ownerUserId = USER_ID, accessToken = "access-1", refreshToken = "refresh-1", clientId = "client-1",
        accessExpiresAtEpochSec = timeProvider.instant.epochSecond + 3600, connectedAtEpochMs = 0,
    )

    private fun samplePlan(): GarminActivityPlan {
        val start = Instant.ofEpochSecond(1_768_000_000) // a realistic (post-1989 FIT epoch) timestamp
        return GarminActivityPlan(
            workoutId = "w1", startTime = start, endTime = start.plusSeconds(60), utcOffsetSeconds = 0,
            sets = listOf(
                GarminPlannedSet(
                    GarminSetKind.ACTIVE, start, start.plusSeconds(30),
                    repetitions = 10, weightKg = 80.0, exercise = GarminExerciseRef(0, 1), weightUnit = WeightUnit.KG,
                ),
            ),
        )
    }

    @Test
    fun `a 401 triggers a forced refresh and a successful retry`() = runTest {
        store.save(connectedTokens())
        authRemote.refreshResult = DiTokens("access-2", "refresh-2", "client-1", timeProvider.instant.epochSecond + 3600)
        activityRemote.results += UploadHttpResult.Unauthorized
        activityRemote.results += UploadHttpResult.Accepted(1L, 2L)

        val result = repository.upload(USER_ID, samplePlan())

        assertThat(result).isEqualTo(GarminResult.Success(GarminUploadOutcome.Uploaded(1L, 2L)))
        assertThat(activityRemote.tokensUsed).containsExactly("access-1", "access-2").inOrder()
    }

    @Test
    fun `two consecutive 401s report ReauthRequired and mark needsReconnect`() = runTest {
        store.save(connectedTokens())
        activityRemote.results += UploadHttpResult.Unauthorized
        activityRemote.results += UploadHttpResult.Unauthorized

        val result = repository.upload(USER_ID, samplePlan())

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.ReauthRequired))
        assertThat(store.load()?.needsReconnect).isTrue()
    }

    @Test
    fun `no stored tokens reports Failure without calling the remote`() = runTest {
        val result = repository.upload(USER_ID, samplePlan())

        assertThat(result).isEqualTo(GarminResult.Failure(GarminError.NotConnected))
        assertThat(activityRemote.uploadCallCount).isEqualTo(0)
    }

    @Test
    fun `a duplicate response is reported as AlreadyExists`() = runTest {
        store.save(connectedTokens())
        activityRemote.results += UploadHttpResult.Duplicate(activityId = 55L)

        val result = repository.upload(USER_ID, samplePlan())

        assertThat(result).isEqualTo(GarminResult.Success(GarminUploadOutcome.AlreadyExists(55L)))
    }
}
