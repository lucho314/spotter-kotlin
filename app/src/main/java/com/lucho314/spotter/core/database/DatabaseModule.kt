package com.lucho314.spotter.core.database

import android.content.Context
import androidx.room.Room
import com.lucho314.spotter.BuildConfig
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.dao.GarminUploadDao
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATABASE_NAME = "spotter.db"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpotterDatabase {
        val builder = Room.databaseBuilder(context, SpotterDatabase::class.java, DATABASE_NAME)
        if (BuildConfig.DEBUG) {
            // Debug-only: v2 exists and migrates via AutoMigration(1, 2); this destructive fallback
            // only protects against a debug build being newer than what's actually migrated (e.g.
            // local schema experiments). Release always relies on the real migration path.
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }
        return builder.build()
    }

    @Provides
    fun provideCachedPayloadDao(database: SpotterDatabase): CachedPayloadDao = database.cachedPayloadDao()

    @Provides
    fun provideActiveWorkoutDao(database: SpotterDatabase): ActiveWorkoutDao = database.activeWorkoutDao()

    @Provides
    fun providePendingWorkoutDao(database: SpotterDatabase): PendingWorkoutDao = database.pendingWorkoutDao()

    @Provides
    fun provideGarminUploadDao(database: SpotterDatabase): GarminUploadDao = database.garminUploadDao()
}
