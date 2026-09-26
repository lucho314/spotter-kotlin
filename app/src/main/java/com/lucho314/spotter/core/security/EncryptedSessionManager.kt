package com.lucho314.spotter.core.security

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucho314.spotter.core.common.IoDispatcher
import com.lucho314.spotter.core.datastore.SECURE_AUTH_DATASTORE
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val SESSION_KEY = stringPreferencesKey("encrypted_session")
private val SESSION_AD = "spotter.session".toByteArray()

/**
 * Encrypts [UserSession] with Tink AEAD (backed by the Android Keystore) before persisting it in
 * DataStore. supabase-kt's default session manager stores the session in clear-text SharedPreferences;
 * this replaces it. Corrupt or undecryptable data is treated as "no session" rather than crashing.
 */
@Singleton
class EncryptedSessionManager @Inject constructor(
    @Named(SECURE_AUTH_DATASTORE) private val store: DataStore<Preferences>,
    private val aeadProvider: AeadProvider,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        val plaintext = json.encodeToString(session).toByteArray()
        // aeadProvider.aead() does blocking Keystore/Tink work on first use (see
        // KeysetRecoveryPolicy): never let that land on whatever dispatcher the caller is on.
        val ciphertext = withContext(ioDispatcher) { aeadProvider.aead().encrypt(plaintext, SESSION_AD) }
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        store.edit { it[SESSION_KEY] = encoded }
    }

    override suspend fun loadSession(): UserSession =
        // supabase-kt's own AuthImpl.loadFromStorage only treats NoSessionFoundException as the
        // expected "logged out" case (debug log); any other exception is logged as an actual
        // failure. Throwing anything else here would make every cold start without a saved
        // session look like an error.
        loadSessionOrNull() ?: throw NoSessionFoundException()

    override suspend fun loadSessionOrNull(): UserSession? {
        val encoded = store.data.firstOrNull()?.get(SESSION_KEY) ?: return null
        return try {
            val plaintext = withContext(ioDispatcher) {
                val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
                aeadProvider.aead().decrypt(ciphertext, SESSION_AD)
            }
            json.decodeFromString(UserSession.serializer(), String(plaintext))
        } catch (e: GeneralSecurityException) {
            deleteSession()
            null
        } catch (e: IllegalArgumentException) {
            deleteSession()
            null
        } catch (e: SerializationException) {
            deleteSession()
            null
        }
    }

    override suspend fun deleteSession() {
        store.edit { it.remove(SESSION_KEY) }
    }
}
