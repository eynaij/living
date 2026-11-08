package com.daycount.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

object WidgetUpdater {

    const val ACTION_REFRESH = "com.daycount.app.ACTION_WIDGET_REFRESH"
    private const val ALARM_REQUEST_CODE = 1001

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, CountdownWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            CountdownWidgetProvider.updateAll(context, manager, ids)
        }
        scheduleNextRefresh(context)
    }

    /** Fires shortly after the next local midnight so the day counter rolls over. */
    fun scheduleNextRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val nextMidnight = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .plusSeconds(5)
        alarmManager.set(
            AlarmManager.RTC,
            nextMidnight.toInstant().toEpochMilli(),
            refreshPendingIntent(context)
        )
    }

    fun cancelNextRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(refreshPendingIntent(context))
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CountdownWidgetProvider::class.java)
            .setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
