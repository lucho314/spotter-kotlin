package com.lucho314.spotter.data.garmin

import com.lucho314.spotter.data.garmin.local.EncryptedGarminTokenStore
import com.lucho314.spotter.data.garmin.local.GarminTokenStore
import com.lucho314.spotter.data.garmin.remote.GarminActivityRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.GarminAuthRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.GarminHttpClientFactory
import com.lucho314.spotter.data.garmin.remote.KtorGarminActivityRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.KtorGarminAuthRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.OkHttpGarminHttpClientFactory
import com.lucho314.spotter.domain.repository.GarminAccountRepository
import com.lucho314.spotter.domain.repository.GarminActivityRepository
import com.lucho314.spotter.domain.repository.GarminUploadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GarminModule {

    @Binds
    @Singleton
    abstract fun bindGarminHttpClientFactory(impl: OkHttpGarminHttpClientFactory): GarminHttpClientFactory

    @Binds
    @Singleton
    abstract fun bindGarminAuthRemoteDataSource(impl: KtorGarminAuthRemoteDataSource): GarminAuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindGarminActivityRemoteDataSource(impl: KtorGarminActivityRemoteDataSource): GarminActivityRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindGarminTokenStore(impl: EncryptedGarminTokenStore): GarminTokenStore

    @Binds
    @Singleton
    abstract fun bindGarminAccountRepository(impl: GarminAccountRepositoryImpl): GarminAccountRepository

    @Binds
    @Singleton
    abstract fun bindGarminUploadRepository(impl: GarminUploadRepositoryImpl): GarminUploadRepository

    @Binds
    @Singleton
    abstract fun bindGarminActivityRepository(impl: GarminActivityRepositoryImpl): GarminActivityRepository
}
