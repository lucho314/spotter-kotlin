package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.notifications.RestTimerAlarmScheduler
import java.time.Instant

class FakeRestTimerAlarmScheduler : RestTimerAlarmScheduler {
    val scheduledAt = mutableListOf<Instant>()
    var cancelCallCount = 0
        private set

    override fun schedule(endsAt: Instant) {
        scheduledAt += endsAt
    }

    override fun cancel() {
        cancelCallCount++
    }
}
