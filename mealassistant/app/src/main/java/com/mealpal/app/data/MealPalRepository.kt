package com.mealpal.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class MealPalRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _stores = MutableStateFlow(readStores(context))
    val stores: StateFlow<List<Store>> = _stores.asStateFlow()

    private val _reminders = MutableStateFlow(readReminders(context))
    val reminders: StateFlow<List<MealReminder>> = _reminders.asStateFlow()

    fun upsertStore(store: Store) {
        val current = _stores.value
        val updated = if (current.any { it.id == store.id }) {
            current.map { if (it.id == store.id) store else it }
        } else {
            current + store
        }
        persistStores(updated)
    }

    fun deleteStore(id: Long) {
        persistStores(_stores.value.filterNot { it.id == id })
    }

    fun upsertReminder(reminder: MealReminder) {
        val current = _reminders.value
        val updated = if (current.any { it.id == reminder.id }) {
            current.map { if (it.id == reminder.id) reminder else it }
        } else {
            current + reminder
        }
        persistReminders(updated.sortedWith(compareBy({ it.hour }, { it.minute })))
    }

    fun deleteReminder(id: Long) {
        persistReminders(_reminders.value.filterNot { it.id == id })
    }

    private fun persistStores(stores: List<Store>) {
        val array = JSONArray()
        stores.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_STORES, array.toString()).apply()
        _stores.value = stores
    }

    private fun persistReminders(reminders: List<MealReminder>) {
        val array = JSONArray()
        reminders.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_REMINDERS, array.toString()).apply()
        _reminders.value = reminders
    }

    companion object {
        private const val PREFS_NAME = "mealpal_prefs"
        private const val KEY_STORES = "stores"
        private const val KEY_REMINDERS = "reminders"

        val DEFAULT_REMINDERS = listOf(
            MealReminder(id = 1L, label = "午餐", hour = 11, minute = 0),
            MealReminder(id = 2L, label = "晚餐", hour = 17, minute = 30)
        )

        fun readStores(context: Context): List<Store> {
            val raw = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STORES, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        add(Store.fromJson(array.getJSONObject(i)))
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun readReminders(context: Context): List<MealReminder> {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_REMINDERS, null)
                ?: return DEFAULT_REMINDERS
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        add(MealReminder.fromJson(array.getJSONObject(i)))
                    }
                }
            }.getOrDefault(DEFAULT_REMINDERS)
        }
    }
}
