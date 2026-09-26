package com.lucho314.spotter.core.security

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.datastore.SECURE_AUTH_DATASTORE
import io.github.jan.supabase.auth.CodeVerifierCache
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

private val VERIFIER_KEY = stringPreferencesKey("pkce_verifier")
private val VERIFIER_AD = "spotter.pkce".toByteArray()

/** Same Tink-encrypted-DataStore scheme as [EncryptedSessionManager], applied to the PKCE code verifier. */
@Singleton
class EncryptedCodeVerifierCache @Inject constructor(
    @Named(SECURE_AUTH_DATASTORE) private val store: DataStore<Preferences>,
    private val aeadProvider: AeadProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CodeVerifierCache {

    override suspend fun saveCodeVerifier(codeVerifier: String) {
        // See EncryptedSessionManager.saveSession: aead() can do blocking Keystore/Tink work.
        val ciphertext = withContext(ioDispatcher) {
            aeadProvider.aead().encrypt(codeVerifier.toByteArray(), VERIFIER_AD)
        }
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        store.edit { it[VERIFIER_KEY] = encoded }
    }

    override suspend fun loadCodeVerifier(): String? {
        val encoded = store.data.firstOrNull()?.get(VERIFIER_KEY) ?: return null
        return try {
            withContext(ioDispatcher) {
                val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
                String(aeadProvider.aead().decrypt(ciphertext, VERIFIER_AD))
            }
        } catch (e: GeneralSecurityException) {
            deleteCodeVerifier()
            null
        } catch (e: IllegalArgumentException) {
            deleteCodeVerifier()
            null
        }
    }

    override suspend fun deleteCodeVerifier() {
        store.edit { it.remove(VERIFIER_KEY) }
    }
}
