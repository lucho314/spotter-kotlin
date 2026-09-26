package com.lucho314.spotter.testutil

import com.lucho314.spotter.domain.repository.LocalDataRepository

class FakeLocalDataRepository : LocalDataRepository {

    var clearError: Throwable? = null
    var clearCallCount = 0
        private set

    /** Hook to verify ordering against other fakes (e.g. that the alarm/sync were already cancelled by the time this runs). */
    var onClearAll: () -> Unit = {}

    override suspend fun clearAll() {
        clearCallCount++
        onClearAll()
        clearError?.let { throw it }
    }
}
