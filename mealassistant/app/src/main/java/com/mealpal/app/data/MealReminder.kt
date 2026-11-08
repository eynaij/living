package com.mealpal.app.data

import org.json.JSONObject
import java.util.Calendar

data class MealReminder(
    val id: Long,
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
) {
    val timeText: String
        get() = "%02d:%02d".format(hour, minute)

    /** Next occurrence of this reminder at or after [nowMillis]. */
    fun nextTriggerMillis(nowMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): MealReminder = MealReminder(
            id = json.getLong("id"),
            label = json.getString("label"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            enabled = json.optBoolean("enabled", true)
        )
    }
}
