package com.lucho314.spotter.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindRestTimerAlarmScheduler(impl: AndroidRestTimerAlarmScheduler): RestTimerAlarmScheduler
}
