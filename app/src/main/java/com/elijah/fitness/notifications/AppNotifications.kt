package com.elijah.fitness.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elijah.fitness.R
import com.elijah.fitness.ui.launcher.LauncherActivity

/**
 * The app's own notifications: the daily reminder and "Goal achieved".
 * (The ongoing GPS-tracking notification lives with its service.)
 *
 * These are local notifications, posted by the phone itself. Server push
 * through Firebase Cloud Messaging would need a sender on the PHP side;
 * nothing here needs one, because both triggers are known on the phone.
 */
object AppNotifications {

    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_ACHIEVEMENTS = "achievements"
    private const val ID_DAILY_REMINDER = 2_001
    private const val ID_GOAL_BASE = 3_000

    /** Create both channels. Safe to call on every app start. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_reminders_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACHIEVEMENTS,
                context.getString(R.string.notification_channel_achievements),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_achievements_desc) }
        )
    }

    /** True if Android will show our notifications (permission + not blocked). */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** "Time to move" — [weekCount] workouts so far this week. */
    fun showDailyReminder(context: Context, weekCount: Int) {
        val body = if (weekCount == 0) {
            context.getString(R.string.reminder_body_first)
        } else {
            context.resources.getQuantityString(R.plurals.reminder_body_week, weekCount, weekCount)
        }
        post(
            context,
            ID_DAILY_REMINDER,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(body),
        )
    }

    /** "Goal achieved" for the goal titled [goalTitle]. One notification per goal. */
    fun showGoalAchieved(context: Context, goalId: String, goalTitle: String) {
        post(
            context,
            ID_GOAL_BASE + (goalId.toIntOrNull() ?: goalId.hashCode()).mod(100_000),
            NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
                .setContentTitle(context.getString(R.string.goal_achieved_title))
                .setContentText(context.getString(R.string.goal_achieved_body, goalTitle)),
        )
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        val notification = builder
            .setSmallIcon(R.drawable.ic_notification_pulse)
            .setColor(ContextCompat.getColor(context, R.color.lime_deep))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
