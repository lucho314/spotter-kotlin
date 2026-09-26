package com.lucho314.spotter.data.remote.datasource

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindAuthDataSource(impl: SupabaseAuthDataSource): AuthDataSource

    @Binds
    @Singleton
    abstract fun bindExerciseDataSource(impl: SupabaseExerciseRemoteDataSource): ExerciseRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindRoutineDataSource(impl: SupabaseRoutineRemoteDataSource): RoutineRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindTemplateDataSource(impl: SupabaseTemplateRemoteDataSource): TemplateRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindWorkoutDataSource(impl: SupabaseWorkoutRemoteDataSource): WorkoutRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindProgressDataSource(impl: SupabaseProgressRemoteDataSource): ProgressRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindProfileDataSource(impl: SupabaseProfileRemoteDataSource): ProfileRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindSharingDataSource(impl: SupabaseSharingRemoteDataSource): SharingRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAiImportDataSource(impl: SupabaseAiImportRemoteDataSource): AiImportRemoteDataSource
}
