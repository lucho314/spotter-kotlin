package com.lucho314.spotter.data.garmin.local

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.datastore.SECURE_AUTH_DATASTORE
import com.lucho314.spotter.core.security.AeadProvider
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredGarminTokens(
    val ownerUserId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String,
    val accessExpiresAtEpochSec: Long,
    val displayName: String? = null,
    val autoUpload: Boolean = true,
    val needsReconnect: Boolean = false,
    val connectedAtEpochMs: Long,
)

interface GarminTokenStore {
    val tokens: Flow<StoredGarminTokens?>
    suspend fun load(): StoredGarminTokens?
    suspend fun save(tokens: StoredGarminTokens)
    suspend fun clear()
}

private val GARMIN_TOKENS_KEY = stringPreferencesKey("encrypted_garmin_tokens")
private val GARMIN_TOKENS_AD = "spotter.garmin".toByteArray()

/** Mirrors [com.lucho314.spotter.core.security.EncryptedSessionManager]'s Tink-encrypted DataStore pattern, in the same `secure_auth` store under its own key. */
@Singleton
class EncryptedGarminTokenStore @Inject constructor(
    @Named(SECURE_AUTH_DATASTORE) private val store: DataStore<Preferences>,
    private val aeadProvider: AeadProvider,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GarminTokenStore {

    override val tokens: Flow<StoredGarminTokens?> = store.data
        .map { prefs -> prefs[GARMIN_TOKENS_KEY]?.let { decrypt(it) } }
        .distinctUntilChanged()

    override suspend fun load(): StoredGarminTokens? {
        val encoded = store.data.firstOrNull()?.get(GARMIN_TOKENS_KEY) ?: return null
        return decrypt(encoded)
    }

    override suspend fun save(tokens: StoredGarminTokens) {
        val plaintext = json.encodeToString(tokens).toByteArray()
        val ciphertext = withContext(ioDispatcher) { aeadProvider.aead().encrypt(plaintext, GARMIN_TOKENS_AD) }
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        store.edit { it[GARMIN_TOKENS_KEY] = encoded }
    }

    override suspend fun clear() {
        store.edit { it.remove(GARMIN_TOKENS_KEY) }
    }

    private suspend fun decrypt(encoded: String): StoredGarminTokens? = try {
        val plaintext = withContext(ioDispatcher) {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            aeadProvider.aead().decrypt(ciphertext, GARMIN_TOKENS_AD)
        }
        json.decodeFromString(StoredGarminTokens.serializer(), String(plaintext))
    } catch (e: GeneralSecurityException) {
        clear()
        null
    } catch (e: IllegalArgumentException) {
        clear()
        null
    } catch (e: SerializationException) {
        clear()
        null
    }
}
