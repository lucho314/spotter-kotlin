package com.lucho314.spotter.data.garmin.local

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

private val GARMIN_TOKENS_KEY = stringPreferencesKey("encrypted_garmin_tokens")
private val SESSION_KEY = stringPreferencesKey("encrypted_session")
private val GARMIN_AD = "spotter.garmin".toByteArray()
private val SESSION_AD = "spotter.session".toByteArray()

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedGarminTokenStoreTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val aead = FakeAead()
    private lateinit var store: EncryptedGarminTokenStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_secure_auth_${System.nanoTime()}")
        }
        store = EncryptedGarminTokenStore(dataStore, FakeAeadProvider(aead), json)
    }

    private fun sampleTokens() = StoredGarminTokens(
        ownerUserId = "user-1",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        clientId = "client-1",
        accessExpiresAtEpochSec = 2_000_000_000,
        displayName = "Ada",
        autoUpload = true,
        needsReconnect = false,
        connectedAtEpochMs = 1_700_000_000_000,
    )

    private suspend fun storeRawCiphertext(plaintext: String, associatedData: ByteArray, key: androidx.datastore.preferences.core.Preferences.Key<String> = GARMIN_TOKENS_KEY) {
        val ciphertext = aead.encrypt(plaintext.toByteArray(), associatedData)
        dataStore.edit { it[key] = Base64.encodeToString(ciphertext, Base64.NO_WRAP) }
    }

    @Test
    fun `save then load returns the same tokens`() = runTest {
        val tokens = sampleTokens()

        store.save(tokens)
        val loaded = store.load()

        assertThat(loaded).isEqualTo(tokens)
    }

    @Test
    fun `clear removes the stored tokens`() = runTest {
        store.save(sampleTokens())

        store.clear()

        assertThat(store.load()).isNull()
    }

    @Test
    fun `garbage value returns null and clears the key`() = runTest {
        dataStore.edit { it[GARMIN_TOKENS_KEY] = "not-valid-base64-or-ciphertext-!!" }

        val loaded = store.load()

        assertThat(loaded).isNull()
        assertThat(dataStore.data.first()[GARMIN_TOKENS_KEY]).isNull()
    }

    @Test
    fun `ciphertext encrypted with the wrong associated data returns null and clears the key`() = runTest {
        // Simulates a ciphertext encrypted for a different AD (e.g. the session's) ending up under the Garmin key.
        storeRawCiphertext(json.encodeToString(StoredGarminTokens.serializer(), sampleTokens()), SESSION_AD)

        val loaded = store.load()

        assertThat(loaded).isNull()
        assertThat(dataStore.data.first()[GARMIN_TOKENS_KEY]).isNull()
    }

    @Test
    fun `successfully decrypted but non-JSON content returns null and clears the key`() = runTest {
        storeRawCiphertext("this is not json", GARMIN_AD)

        val loaded = store.load()

        assertThat(loaded).isNull()
        assertThat(dataStore.data.first()[GARMIN_TOKENS_KEY]).isNull()
    }

    @Test
    fun `does not touch the encrypted_session key`() = runTest {
        dataStore.edit { it[SESSION_KEY] = "untouched" }

        store.save(sampleTokens())
        store.clear()

        assertThat(dataStore.data.first()[SESSION_KEY]).isEqualTo("untouched")
    }
}
