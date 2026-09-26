package com.lucho314.spotter.core.common

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock so use cases and view models are deterministic in tests. */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
