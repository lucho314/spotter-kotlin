package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.work.SyncScheduler

class FakeSyncScheduler : SyncScheduler {
    var scheduleCallCount = 0
        private set
    var cancelCallCount = 0
        private set

    override fun schedule() {
        scheduleCallCount++
    }

    override fun cancel() {
        cancelCallCount++
    }
}
