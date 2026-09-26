package com.lucho314.spotter.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.testutil.FakeAead
import com.lucho314.spotter.testutil.FakeAeadProvider
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val SESSION_KEY = stringPreferencesKey("encrypted_session")
private val SESSION_AD = "spotter.session".toByteArray()
private val OTHER_AD = "spotter.pkce".toByteArray()

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedSessionManagerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val aead = FakeAead()
    private lateinit var manager: EncryptedSessionManager
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_secure_auth_${System.nanoTime()}")
        }
        manager = EncryptedSessionManager(store, FakeAeadProvider(aead), json)
    }

    private fun sampleSession() = UserSession(
        accessToken = "access-123",
        refreshToken = "refresh-456",
        expiresIn = 3600,
        tokenType = "bearer",
    )

    private suspend fun storeRawCiphertext(plaintext: String, associatedData: ByteArray) {
        val ciphertext = aead.encrypt(plaintext.toByteArray(), associatedData)
        store.edit { it[SESSION_KEY] = Base64.encodeToString(ciphertext, Base64.NO_WRAP) }
    }

    @Test
    fun `save then load returns the same session`() = runTest {
        val session = sampleSession()

        manager.saveSession(session)
        val loaded = manager.loadSessionOrNull()

        assertThat(loaded?.accessToken).isEqualTo(session.accessToken)
        assertThat(loaded?.refreshToken).isEqualTo(session.refreshToken)
    }

    @Test
    fun `delete removes the stored session`() = runTest {
        manager.saveSession(sampleSession())

        manager.deleteSession()

        assertThat(manager.loadSessionOrNull()).isNull()
    }

    @Test
    fun `garbage value returns null and clears the key`() = runTest {
        store.edit { it[SESSION_KEY] = "not-valid-base64-or-ciphertext-!!" }

        val loaded = manager.loadSessionOrNull()

        assertThat(loaded).isNull()
        assertThat(store.data.first()[SESSION_KEY]).isNull()
    }

    @Test
    fun `valid base64 encrypted with the wrong associated data returns null and clears the key`() = runTest {
        // Simulates e.g. the PKCE verifier's ciphertext ending up under the session's key: valid
        // Base64, valid ciphertext shape, but the embedded AD doesn't match "spotter.session".
        storeRawCiphertext(json.encodeToString(UserSession.serializer(), sampleSession()), OTHER_AD)

        val loaded = manager.loadSessionOrNull()

        assertThat(loaded).isNull()
        assertThat(store.data.first()[SESSION_KEY]).isNull()
    }

    @Test
    fun `successfully decrypted but non-JSON content returns null and clears the key`() = runTest {
        storeRawCiphertext("this is not json", SESSION_AD)

        val loaded = manager.loadSessionOrNull()

        assertThat(loaded).isNull()
        assertThat(store.data.first()[SESSION_KEY]).isNull()
    }

    @Test
    fun `loadSession throws NoSessionFoundException when there is no session`() = runTest {
        try {
            manager.loadSession()
            throw AssertionError("Expected NoSessionFoundException")
        } catch (e: NoSessionFoundException) {
            // supabase-kt's AuthImpl.loadFromStorage only treats this specific exception as the
            // expected "logged out" case; anything else is logged as a real failure.
        }
    }
}
