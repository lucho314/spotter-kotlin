package com.lucho314.spotter.core.common

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable id generator so tests can produce deterministic ids. */
interface IdGenerator {
    fun uuid(): String
}

@Singleton
class RandomIdGenerator @Inject constructor() : IdGenerator {
    override fun uuid(): String = UUID.randomUUID().toString()
}
