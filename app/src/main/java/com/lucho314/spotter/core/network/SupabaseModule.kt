package com.lucho314.spotter.core.network

import com.lucho314.spotter.BuildConfig
import com.lucho314.spotter.core.config.AppConfig
import com.lucho314.spotter.core.security.EncryptedCodeVerifierCache
import com.lucho314.spotter.core.security.EncryptedSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    /** DTO JSON config, per the Supabase schema notes: unknown keys are ignored server-side changes don't break the app, nulls are omitted so DB defaults apply. */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(
        config: AppConfig,
        encryptedSessionManager: EncryptedSessionManager,
        encryptedCodeVerifierCache: EncryptedCodeVerifierCache,
        json: Json,
    ): SupabaseClient = createSupabaseClient(config.supabaseUrl, config.supabaseAnonKey) {
        defaultSerializer = KotlinXSerializer(json)
        requestTimeout = 60.seconds
        defaultLogLevel = if (BuildConfig.DEBUG) LogLevel.WARNING else LogLevel.NONE
        install(Auth) {
            flowType = FlowType.PKCE
            scheme = "spotter"
            host = "auth"
            sessionManager = encryptedSessionManager
            codeVerifierCache = encryptedCodeVerifierCache
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        install(Functions)
    }

    @Provides
    fun provideAuth(client: SupabaseClient) = client.auth

    @Provides
    fun providePostgrest(client: SupabaseClient) = client.postgrest

    @Provides
    fun provideFunctions(client: SupabaseClient) = client.functions
}
