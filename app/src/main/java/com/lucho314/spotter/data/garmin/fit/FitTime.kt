package com.lucho314.spotter.data.garmin.fit

import java.time.Instant

/** FIT epoch: 1989-12-31T00:00:00Z. */
const val FIT_EPOCH_OFFSET_SECONDS = 631_065_600L

fun Instant.toFitTime(): Long = epochSecond - FIT_EPOCH_OFFSET_SECONDS
