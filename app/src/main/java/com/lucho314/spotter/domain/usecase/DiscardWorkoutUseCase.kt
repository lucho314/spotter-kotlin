package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import javax.inject.Inject

/** Discards the active session (no outbox row is created) and cancels its rest-timer alarm, if any. */
class DiscardWorkoutUseCase @Inject constructor(
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
) {
    suspend operator fun invoke(sessionId: String) {
        activeWorkoutRepository.discard(sessionId)
        restTimerAlarmScheduler.cancel()
    }
}
