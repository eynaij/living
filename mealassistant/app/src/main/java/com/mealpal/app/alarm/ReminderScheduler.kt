package com.mealpal.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mealpal.app.data.MealPalRepository
import com.mealpal.app.data.MealReminder

object ReminderScheduler {

    fun rescheduleAll(context: Context) {
        val reminders = MealPalRepository.readReminders(context)
        reminders.forEach { reminder ->
            if (reminder.enabled) {
                schedule(context, reminder)
            } else {
                cancel(context, reminder.id)
            }
        }
    }

    fun schedule(context: Context, reminder: MealReminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = reminder.nextTriggerMillis(System.currentTimeMillis())
        val pendingIntent = pendingIntent(context, reminder.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Exact alarm not allowed: fall back to inexact
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
            )
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context, reminderId))
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
