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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val VERIFIER_KEY = stringPreferencesKey("pkce_verifier")
private val VERIFIER_AD = "spotter.pkce".toByteArray()
private val OTHER_AD = "spotter.session".toByteArray()

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedCodeVerifierCacheTest {

    private val aead = FakeAead()
    private lateinit var cache: EncryptedCodeVerifierCache
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = TestScope(UnconfinedTestDispatcher())
        store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test_pkce_${System.nanoTime()}")
        }
        cache = EncryptedCodeVerifierCache(store, FakeAeadProvider(aead))
    }

    @Test
    fun `save then load returns the same verifier`() = runTest {
        cache.saveCodeVerifier("verifier-123")

        assertThat(cache.loadCodeVerifier()).isEqualTo("verifier-123")
    }

    @Test
    fun `delete removes the stored verifier`() = runTest {
        cache.saveCodeVerifier("verifier-123")

        cache.deleteCodeVerifier()

        assertThat(cache.loadCodeVerifier()).isNull()
    }

    @Test
    fun `missing verifier returns null`() = runTest {
        assertThat(cache.loadCodeVerifier()).isNull()
    }

    @Test
    fun `garbage value returns null and clears the key`() = runTest {
        store.edit { it[VERIFIER_KEY] = "not-valid-base64-!!" }

        val loaded = cache.loadCodeVerifier()

        assertThat(loaded).isNull()
        assertThat(store.data.first()[VERIFIER_KEY]).isNull()
    }

    @Test
    fun `valid base64 encrypted with the wrong associated data returns null and clears the key`() = runTest {
        // Simulates e.g. a session ciphertext ending up under the PKCE verifier's key.
        val ciphertext = aead.encrypt("some-verifier".toByteArray(), OTHER_AD)
        store.edit { it[VERIFIER_KEY] = Base64.encodeToString(ciphertext, Base64.NO_WRAP) }

        val loaded = cache.loadCodeVerifier()

        assertThat(loaded).isNull()
        assertThat(store.data.first()[VERIFIER_KEY]).isNull()
    }
}
