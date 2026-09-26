package com.lucho314.spotter.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

private const val KEYSET_NAME = "spotter_tink_keyset"
private const val PREFS_FILE = "spotter_tink_prefs"
private const val MASTER_KEY_ALIAS = "spotter_master_key"
private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/** Provides the [Aead] primitive used to encrypt data at rest (session, PKCE verifier). */
interface AeadProvider {
    fun aead(): Aead
}

/**
 * Tink-backed [AeadProvider]. The keyset is generated on first use, wrapped by an
 * Android Keystore master key, and persisted (wrapped, never in the clear) in a private
 * SharedPreferences file.
 *
 * Memoization is hand-rolled instead of `by lazy`: a `Lazy` delegate does not cache a thrown
 * exception, so a failure would otherwise be retried (or not) with no policy at all on every
 * single call. The actual retry/wipe/fail-closed policy is [KeysetRecoveryPolicy] (see its KDoc);
 * this class only wires it to real Tink/Android Keystore calls.
 */
@Singleton
class TinkAeadProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AeadProvider {

    @Volatile
    private var cached: Aead? = null
    private val lock = Any()

    override fun aead(): Aead {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            AeadConfig.register()
            val manager = KeysetRecoveryPolicy(
                isUsingKeystore = AndroidKeysetManager::isUsingKeystore,
                build = ::createKeysetManager,
                wipe = ::wipeKeystoreState,
            ).run()
            return manager.keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
                .also { cached = it }
        }
    }

    private fun createKeysetManager(): AndroidKeysetManager = AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, PREFS_FILE)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()

    /**
     * Wipes the wrapped keyset's prefs file and its Keystore master key alias so the next
     * [createKeysetManager] call generates a brand new keyset. Any previously encrypted
     * session/PKCE verifier becomes unreadable after this (already handled as "no session" by
     * callers) rather than ever being read back in the clear.
     */
    private fun wipeKeystoreState() {
        // commit(), not apply(): the caller rebuilds immediately after this returns and must see
        // the write land first, not whenever the async apply() happens to flush.
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
    }
}
