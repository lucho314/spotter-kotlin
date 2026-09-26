package com.lucho314.spotter.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the single system alarm that fires [RestTimerReceiver] when a rest period ends,
 * surviving the app being backgrounded or the process dying (RN bug #10, section 7: the RN rest
 * timer was pure JS state, so it just stopped counting - and never restarted - if the screen was
 * off). There is only ever one such alarm at a time: [schedule] always replaces whatever was
 * scheduled before (same request code), which is exactly what "reinicia el descanso" (bug #10)
 * needs on every newly completed set.
 */
interface RestTimerAlarmScheduler {
    fun schedule(endsAt: Instant)
    fun cancel()
}

@Singleton
class AndroidRestTimerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestTimerAlarmScheduler {

    override fun schedule(endsAt: Instant) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent()
        // `canScheduleExactAlarms()` is only meaningful from API 31 (S) onward; below that, exact
        // alarms are always allowed. Falling back to an inexact alarm (still `AllowWhileIdle`, so
        // it isn't deferred indefinitely by Doze) is an accepted risk (migration plan section 12):
        // it may fire a little late, never early, and never not at all.
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), pendingIntent)
        }
    }

    override fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent())
    }

    /** Same request code every time: a new [schedule] call replaces the pending alarm instead of stacking a second one. */
    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REST_TIMER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REST_TIMER_ALARM_REQUEST_CODE = 4001
    }
}
