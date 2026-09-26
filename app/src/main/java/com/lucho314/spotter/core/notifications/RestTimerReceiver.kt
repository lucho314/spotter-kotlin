package com.lucho314.spotter.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.lucho314.spotter.MainActivity
import com.lucho314.spotter.R

/** Set on the [MainActivity]-bound intent this receiver launches when the user taps the notification; [com.lucho314.spotter.feature.root.RootViewModel] turns it into a navigation to `WorkoutRoute`. */
const val EXTRA_OPEN_WORKOUT = "open_workout"

private const val REST_FINISHED_NOTIFICATION_ID = 4002

/**
 * Fires when a scheduled rest period ends ([RestTimerAlarmScheduler]). Only actually shows a
 * notification if the app is backgrounded: while it's in the foreground,
 * `feature.workout.WorkoutViewModel`'s own ticker already reacts to the rest timer reaching zero
 * (beep + haptic, no notification needed) - showing one too would be redundant and, since it POSTs
 * on every set with no dedup, noisy.
 *
 * Not exported (`AndroidManifest.xml`): only the system (via the `PendingIntent` this app itself
 * schedules) can deliver this broadcast.
 */
class RestTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (!hasNotificationPermission(context)) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_WORKOUT, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REST_FINISHED_NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REST_TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rest_timer)
            .setContentTitle(context.getString(R.string.notification_rest_timer_title))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(REST_FINISHED_NOTIFICATION_ID, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        // Below API 33 posting a notification never required a runtime permission (the user could
        // only disable the channel, which NotificationManagerCompat.notify already respects).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
