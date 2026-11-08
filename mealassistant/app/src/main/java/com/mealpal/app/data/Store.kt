package com.mealpal.app.data

import org.json.JSONObject

enum class Platform(val displayName: String) {
    MEITUAN("美团外卖"),
    TAOBAO("淘宝闪购"),
    OTHER("其他");

    companion object {
        fun fromName(name: String?): Platform =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class Store(
    val id: Long,
    val name: String,
    val platform: Platform,
    val link: String,
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("platform", platform.name)
        put("link", link)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): Store = Store(
            id = json.getLong("id"),
            name = json.getString("name"),
            platform = Platform.fromName(json.optString("platform")),
            link = json.getString("link"),
            enabled = json.optBoolean("enabled", true)
        )
    }
}
