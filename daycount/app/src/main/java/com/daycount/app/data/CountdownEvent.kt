package com.daycount.app.data

import org.json.JSONObject

/**
 * A single countdown target. [epochDay] is a LocalDate epoch day, which keeps
 * day arithmetic exact and independent of time zones and DST.
 */
data class CountdownEvent(
    val id: Long,
    val title: String,
    val epochDay: Long,
    val colorIndex: Int
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("day", epochDay)
        .put("color", colorIndex)

    companion object {
        fun fromJson(json: JSONObject): CountdownEvent = CountdownEvent(
            id = json.optLong("id"),
            title = json.optString("title"),
            epochDay = json.optLong("day"),
            colorIndex = json.optInt("color")
        )
    }
}
