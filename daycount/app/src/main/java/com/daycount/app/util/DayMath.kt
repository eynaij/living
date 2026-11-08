package com.daycount.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DayMath {

    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

    fun daysFromToday(epochDay: Long): Long =
        epochDay - LocalDate.now().toEpochDay()

    fun fullDate(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(fullDateFormatter)

    fun shortDate(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)

    fun weekday(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(weekdayFormatter)

    fun relativeText(epochDay: Long): String {
        val days = daysFromToday(epochDay)
        return when {
            days > 0 -> "还有 $days 天"
            days < 0 -> "已过去 ${-days} 天"
            else -> "就是今天"
        }
    }

    fun unitText(days: Long): String = when {
        days > 0 -> "天后"
        days < 0 -> "天前"
        else -> "今天"
    }
}
