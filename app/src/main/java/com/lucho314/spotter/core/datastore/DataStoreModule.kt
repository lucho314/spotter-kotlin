package com.lucho314.spotter.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/** A corrupt preferences file is replaced with an empty one instead of crashing the app. */
private val onCorruption = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

/** Encrypted session + PKCE verifier live here (see `core/security`); values are never stored in the clear. */
private val Context.secureAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_auth",
    corruptionHandler = onCorruption,
)

/** Device-scoped and per-user preferences (weight unit, onboarding flag). */
private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
    corruptionHandler = onCorruption,
)

const val SECURE_AUTH_DATASTORE = "secure_auth"
const val USER_PREFS_DATASTORE = "user_prefs"

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @Named(SECURE_AUTH_DATASTORE)
    fun provideSecureAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.secureAuthDataStore

    @Provides
    @Singleton
    @Named(USER_PREFS_DATASTORE)
    fun provideUserPrefsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPrefsDataStore
}
