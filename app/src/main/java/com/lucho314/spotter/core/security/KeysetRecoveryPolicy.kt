package com.lucho314.spotter.core.security

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException
import kotlin.random.Random

/**
 * Recovery policy for building a wrapped keyset handle (Tink's `AndroidKeysetManager` in
 * production; any `T` in tests). Generic and side-effect-free besides the injected
 * [build]/[wipe]/[sleep] so the retry/wipe/fail-closed behavior is unit testable without touching
 * the Android Keystore or blocking a real thread:
 *
 * 1. First failure: back off for [randomBackoffMillis] (100-300 ms) - a transient issue right
 *    after boot (keystore2 busy, an OEM TEE hiccup) is more likely to have cleared by then - then
 *    retry [build] once with no destructive action.
 * 2. Second failure: if it is one of [GeneralSecurityException], [ProviderException],
 *    [KeyStoreException] or [IOException] (Tink falls back to `readKeysetInCleartext`, which
 *    throws an `IOException`, when the wrapped keyset can't be decrypted), call [wipe] and rebuild
 *    **once**. Any other exception type propagates immediately (with the first failure attached
 *    as a suppressed exception) - it doesn't look like "the wrapped keyset/master key is
 *    unusable", so wiping wouldn't help.
 * 3. If that rebuild also fails, the exception propagates (fail closed): this never falls back to
 *    an unencrypted keyset.
 * 4. Even on success, [isUsingKeystore] is checked: Tink silently disables the Android Keystore
 *    and writes the keyset **in cleartext** if it can't use it. That cleartext keyset must never
 *    be left on disk (defeating the whole point of this class) or served to a caller, so this
 *    calls [wipe] to delete it before throwing.
 *
 * **Threading:** [sleep] blocks the calling thread and [wipe]/[build] do blocking Keystore/
 * SharedPreferences I/O, so [run] must always be invoked from an IO dispatcher, never from Main.
 */
class KeysetRecoveryPolicy<T>(
    private val isUsingKeystore: (T) -> Boolean,
    private val build: () -> T,
    private val wipe: () -> Unit,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val randomBackoffMillis: () -> Long = { Random.nextLong(100L, 301L) },
) {
    fun run(): T {
        val result = runCatching(build).getOrElse { firstError ->
            sleep(randomBackoffMillis())
            runCatching(build).getOrElse { secondError ->
                secondError.addSuppressed(firstError)
                if (!isRecoverableByWipe(secondError)) throw secondError
                wipe()
                runCatching(build).getOrElse { thirdError ->
                    thirdError.addSuppressed(firstError)
                    throw thirdError
                }
            }
        }
        if (!isUsingKeystore(result)) {
            // Tink already wrote this handle's keyset to disk in the clear (see class KDoc):
            // delete it instead of ever reading it back or returning it to a caller.
            wipe()
            error("Keyset is not backed by the Android Keystore; refusing to use an unencrypted fallback")
        }
        return result
    }

    private fun isRecoverableByWipe(e: Throwable): Boolean =
        e is GeneralSecurityException || e is ProviderException || e is KeyStoreException || e is IOException
}
