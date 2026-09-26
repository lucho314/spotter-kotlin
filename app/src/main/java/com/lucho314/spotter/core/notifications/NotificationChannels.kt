package com.lucho314.spotter.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.lucho314.spotter.R

const val REST_TIMER_CHANNEL_ID = "rest_timer"

/** Creates the app's notification channels. Safe to call on every process start ([androidx.core.app.NotificationManagerCompat.createNotificationChannel] is idempotent). */
object NotificationChannels {
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            REST_TIMER_CHANNEL_ID,
            context.getString(R.string.notification_channel_rest_timer_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_rest_timer_description)
        }
        manager.createNotificationChannel(channel)
    }
}
