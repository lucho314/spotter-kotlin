package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.TimeProvider
import java.time.Instant
import java.time.ZoneId

class FakeTimeProvider(
    var instant: Instant = Instant.parse("2026-01-15T10:00:00Z"),
    var zoneId: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}
