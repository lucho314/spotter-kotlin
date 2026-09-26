package com.lucho314.spotter.core.common

import android.util.Log
import com.lucho314.spotter.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logging facade. Implementations must never log tokens, URLs with query params or other PII.
 * [AndroidLogger] is a no-op for [e] in release builds and silent for [d]/[w] outside debug.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    override fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}
