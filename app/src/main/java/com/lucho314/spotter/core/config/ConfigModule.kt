package com.lucho314.spotter.core.config

import com.lucho314.spotter.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() },
    )
}
