package com.daycount.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.daycount.app.MainActivity
import com.daycount.app.R
import com.daycount.app.data.AccentPalette
import com.daycount.app.data.CountdownEvent
import com.daycount.app.data.EventRepository
import com.daycount.app.util.DayMath
import java.time.LocalDate
import kotlin.math.abs

class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
        WidgetUpdater.scheduleNextRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
            WidgetUpdater.refreshAll(context)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleNextRefresh(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelNextRefresh(context)
    }

    companion object {

        fun updateAll(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val event = pickWidgetEvent(EventRepository.readAll(context))
            appWidgetIds.forEach { id ->
                appWidgetManager.updateAppWidget(id, buildViews(context, event))
            }
        }

        /** Nearest upcoming event, or the most recent past one when nothing is ahead. */
        private fun pickWidgetEvent(events: List<CountdownEvent>): CountdownEvent? {
            if (events.isEmpty()) return null
            val today = LocalDate.now().toEpochDay()
            return events.filter { it.epochDay >= today }.minByOrNull { it.epochDay }
                ?: events.maxByOrNull { it.epochDay }
        }

        private fun buildViews(context: Context, event: CountdownEvent?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            val packageName = context.packageName

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

            if (event == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_days, "—")
                views.setTextViewText(R.id.widget_unit, "")
                views.setTextViewText(R.id.widget_date, "点击添加事件")
                return views
            }

            val days = event.epochDay - LocalDate.now().toEpochDay()
            views.setTextViewText(R.id.widget_title, event.title)

            if (days == 0L) {
                views.setTextViewText(R.id.widget_days, context.getString(R.string.widget_today))
                views.setTextViewText(R.id.widget_unit, "")
            } else {
                views.setTextViewText(R.id.widget_days, abs(days).toString())
                views.setTextViewText(
                    R.id.widget_unit,
                    context.getString(
                        if (days > 0) R.string.widget_unit_after else R.string.widget_unit_before
                    )
                )
            }

            views.setTextViewText(R.id.widget_date, DayMath.shortDate(event.epochDay))
            views.setTextColor(
                R.id.widget_days,
                AccentPalette.color(event.colorIndex, isDark = false)
            )
            views.setContentDescription(
                R.id.widget_root,
                "${event.title} ${DayMath.relativeText(event.epochDay)}"
            )
            return views
        }
    }
}
