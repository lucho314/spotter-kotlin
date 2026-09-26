package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.IdGenerator

/** Produces deterministic, incrementing ids instead of random UUIDs. */
class FakeIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun uuid(): String = "$prefix-${++counter}"
}
