package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.common.Logger

/** No-op [Logger] for tests; avoids depending on `android.util.Log` (unavailable outside Robolectric). */
class FakeLogger : Logger {
    val warnings = mutableListOf<String>()
    val errors = mutableListOf<String>()

    override fun d(tag: String, message: String) = Unit

    override fun w(tag: String, message: String) {
        warnings += message
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        errors += message
    }
}
