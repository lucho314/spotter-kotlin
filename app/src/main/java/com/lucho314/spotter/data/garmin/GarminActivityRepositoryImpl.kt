package com.lucho314.spotter.data.garmin

import com.lucho314.spotter.data.garmin.fit.StrengthActivityFitEncoder
import com.lucho314.spotter.data.garmin.remote.GarminActivityRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.UploadHttpResult
import com.lucho314.spotter.domain.model.GarminActivityPlan
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.domain.model.GarminResult
import com.lucho314.spotter.domain.model.GarminUploadOutcome
import com.lucho314.spotter.domain.repository.GarminActivityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GarminActivityRepositoryImpl @Inject constructor(
    private val encoder: StrengthActivityFitEncoder,
    private val tokenManager: GarminTokenManager,
    private val remote: GarminActivityRemoteDataSource,
) : GarminActivityRepository {

    override suspend fun upload(userId: String, plan: GarminActivityPlan): GarminResult<GarminUploadOutcome> = garminCall {
        val bytes = encoder.encode(plan)
        val fileName = "spotter_${plan.workoutId.take(8)}.fit"

        suspend fun attempt(force: Boolean): UploadHttpResult {
            val token = tokenManager.validAccessToken(userId, force).getOrThrowGarmin()
            return remote.upload(token, fileName, bytes)
        }

        when (val first = attempt(force = false)) {
            UploadHttpResult.Unauthorized -> when (val second = attempt(force = true)) {
                UploadHttpResult.Unauthorized -> {
                    tokenManager.markNeedsReconnect(userId)
                    throw GarminApiException(GarminError.ReauthRequired)
                }
                else -> second.toOutcome()
            }
            else -> first.toOutcome()
        }
    }

    private fun UploadHttpResult.toOutcome(): GarminUploadOutcome = when (this) {
        is UploadHttpResult.Accepted -> GarminUploadOutcome.Uploaded(activityId, uploadId)
        is UploadHttpResult.Duplicate -> GarminUploadOutcome.AlreadyExists(activityId)
        UploadHttpResult.Unauthorized -> error("unreachable: Unauthorized is handled by the retry-once branch above")
    }
}
