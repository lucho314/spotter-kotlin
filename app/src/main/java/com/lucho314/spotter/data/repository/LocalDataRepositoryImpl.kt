package com.lucho314.spotter.data.repository

import androidx.room.withTransaction
import com.lucho314.spotter.core.database.SpotterDatabase
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.dao.CachedPayloadDao
import com.lucho314.spotter.core.database.dao.PendingWorkoutDao
import com.lucho314.spotter.domain.repository.LocalDataRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Sign-out cleanup only (see [LocalDataRepository]'s KDoc): wipes every Room table in one transaction. */
@Singleton
class LocalDataRepositoryImpl @Inject constructor(
    private val database: SpotterDatabase,
    private val activeWorkoutDao: ActiveWorkoutDao,
    private val pendingWorkoutDao: PendingWorkoutDao,
    private val cachedPayloadDao: CachedPayloadDao,
) : LocalDataRepository {

    override suspend fun clearAll() {
        database.withTransaction {
            // Children first, explicitly - not relying on the FK cascade pragma being on.
            activeWorkoutDao.deleteAllSets()
            activeWorkoutDao.deleteAllExercises()
            activeWorkoutDao.deleteAllSessions()
            pendingWorkoutDao.deleteAllSets()
            pendingWorkoutDao.deleteAll()
            cachedPayloadDao.deleteAll()
        }
    }
}
