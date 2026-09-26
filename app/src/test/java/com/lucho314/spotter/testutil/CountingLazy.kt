package com.lucho314.spotter.testutil

import dagger.Lazy

/** [dagger.Lazy] test double that records how many times [get] was called (should stay lazy). */
class CountingLazy<T>(private val factory: () -> T) : Lazy<T> {
    var getCallCount: Int = 0
        private set

    override fun get(): T {
        getCallCount++
        return factory()
    }
}
