package com.mealpal.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mealpal.app.MainActivity
import com.mealpal.app.R
import com.mealpal.app.data.MealPalRepository
import com.mealpal.app.data.Store

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val reminder = MealPalRepository.readReminders(context)
            .firstOrNull { it.id == reminderId } ?: return

        postNotification(context, reminder.label, reminder.timeText)

        // One-shot alarm: schedule the next day
        if (reminder.enabled) {
            ReminderScheduler.schedule(context, reminder)
        }
    }

    private fun postNotification(context: Context, label: String, timeText: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_description)
                }
            )
        }

        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("该点外卖啦")
            .setContentText("$label $timeText · 点我一键下单")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPending)

        // One action button per enabled store (max 3 shown by system)
        MealPalRepository.readStores(context)
            .filter { it.enabled && it.link.isNotBlank() }
            .take(3)
            .forEachIndexed { index, store ->
                storePendingIntent(context, store, index)?.let { pending ->
                    builder.addAction(0, store.name, pending)
                }
            }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun storePendingIntent(
        context: Context,
        store: Store,
        index: Int
    ): PendingIntent? {
        val uri = runCatching { Uri.parse(store.link.trim()) }.getOrNull() ?: return null
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_STORE_BASE + index,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val CHANNEL_ID = "meal_reminders"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_STORE_BASE = 200
    }
}
