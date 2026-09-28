package com.lucho314.spotter.domain.usecase

import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import javax.inject.Inject

/**
 * `AlarmManager` alarms don't survive a device reboot, so [ActiveWorkoutRepository]'s persisted
 * `RestTimer.endsAt` (which does, since it's in Room) can silently disagree with reality once the
 * app is opened again after one (review carry-over 13). Called once per process from
 * [com.lucho314.spotter.feature.root.RootViewModel] on the first sign-in: re-schedules the alarm if
 * the rest period is still running, or clears the stale timer if it already elapsed while the app
 * wasn't around to notify/clear it itself.
 */
class RescheduleRestAlarmUseCase @Inject constructor(
    private val activeWorkoutRepository: ActiveWorkoutRepository,
    private val restTimerAlarmScheduler: RestTimerAlarmScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(userId: String) {
        val workout = activeWorkoutRepository.getActive(userId) ?: return
        val rest = workout.rest ?: return
        if (rest.remainingSeconds(timeProvider.now()) > 0) {
            restTimerAlarmScheduler.schedule(rest.endsAt)
        } else {
            activeWorkoutRepository.setRestTimer(workout.sessionId, null)
        }
    }
}
