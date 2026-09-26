package com.lucho314.spotter.core.common

import javax.inject.Qualifier

/** Qualifier for the IO [kotlinx.coroutines.CoroutineDispatcher], injected instead of using [kotlinx.coroutines.Dispatchers] directly so tests can substitute it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Qualifier for the default (CPU-bound) [kotlinx.coroutines.CoroutineDispatcher]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
