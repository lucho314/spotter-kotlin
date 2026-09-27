package com.lucho314.spotter.data.repository

import com.lucho314.spotter.data.export.WorkoutExportRepositoryImpl
import com.lucho314.spotter.data.image.ImageRepositoryImpl
import com.lucho314.spotter.domain.repository.ActiveWorkoutRepository
import com.lucho314.spotter.domain.repository.AiImportRepository
import com.lucho314.spotter.domain.repository.AuthRepository
import com.lucho314.spotter.domain.repository.ExerciseRepository
import com.lucho314.spotter.domain.repository.ImageRepository
import com.lucho314.spotter.domain.repository.LocalDataRepository
import com.lucho314.spotter.domain.repository.PendingWorkoutRepository
import com.lucho314.spotter.domain.repository.PreferencesRepository
import com.lucho314.spotter.domain.repository.ProfileRepository
import com.lucho314.spotter.domain.repository.ProgressRepository
import com.lucho314.spotter.domain.repository.RoutineRepository
import com.lucho314.spotter.domain.repository.SharingRepository
import com.lucho314.spotter.domain.repository.TemplateRepository
import com.lucho314.spotter.domain.repository.WorkoutExportRepository
import com.lucho314.spotter.domain.repository.WorkoutHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: TemplateRepositoryImpl): TemplateRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutHistoryRepository(impl: WorkoutHistoryRepositoryImpl): WorkoutHistoryRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindSharingRepository(impl: SharingRepositoryImpl): SharingRepository

    @Binds
    @Singleton
    abstract fun bindAiImportRepository(impl: AiImportRepositoryImpl): AiImportRepository

    @Binds
    @Singleton
    abstract fun bindActiveWorkoutRepository(impl: ActiveWorkoutRepositoryImpl): ActiveWorkoutRepository

    @Binds
    @Singleton
    abstract fun bindPendingWorkoutRepository(impl: PendingWorkoutRepositoryImpl): PendingWorkoutRepository

    @Binds
    @Singleton
    abstract fun bindLocalDataRepository(impl: LocalDataRepositoryImpl): LocalDataRepository

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutExportRepository(impl: WorkoutExportRepositoryImpl): WorkoutExportRepository
}
