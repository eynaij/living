package com.daycount.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class EventRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _events = MutableStateFlow(readAll(context))
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()

    fun upsert(event: CountdownEvent) {
        val current = _events.value
        val updated = if (current.any { it.id == event.id }) {
            current.map { if (it.id == event.id) event else it }
        } else {
            current + event
        }
        persist(updated)
    }

    fun delete(id: Long) {
        persist(_events.value.filterNot { it.id == id })
    }

    private fun persist(events: List<CountdownEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
        _events.value = events
    }

    companion object {
        private const val PREFS_NAME = "daycount_events"
        private const val KEY_EVENTS = "events"

        /** Synchronous read used by the home screen widget. */
        fun readAll(context: Context): List<CountdownEvent> {
            val raw = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_EVENTS, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        add(CountdownEvent.fromJson(array.getJSONObject(i)))
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
