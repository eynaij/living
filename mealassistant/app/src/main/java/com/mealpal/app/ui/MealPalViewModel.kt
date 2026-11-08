package com.mealpal.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mealpal.app.alarm.ReminderScheduler
import com.mealpal.app.data.MealPalRepository
import com.mealpal.app.data.MealReminder
import com.mealpal.app.data.Platform
import com.mealpal.app.data.Store
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MealPalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MealPalRepository(application)

    val stores: StateFlow<List<Store>> = repository.stores
    val reminders: StateFlow<List<MealReminder>> = repository.reminders

    fun saveStore(id: Long?, name: String, platform: Platform, link: String, enabled: Boolean) {
        val store = Store(
            id = id ?: System.currentTimeMillis(),
            name = name.trim(),
            platform = platform,
            link = link.trim(),
            enabled = enabled
        )
        repository.upsertStore(store)
    }

    fun deleteStore(id: Long) {
        repository.deleteStore(id)
    }

    fun saveReminder(id: Long?, label: String, hour: Int, minute: Int, enabled: Boolean) {
        val reminder = MealReminder(
            id = id ?: System.currentTimeMillis(),
            label = label.trim().ifEmpty { "饭点" },
            hour = hour,
            minute = minute,
            enabled = enabled
        )
        repository.upsertReminder(reminder)
        reschedule(reminder)
    }

    fun toggleReminder(reminder: MealReminder, enabled: Boolean) {
        val updated = reminder.copy(enabled = enabled)
        repository.upsertReminder(updated)
        reschedule(updated)
    }

    fun deleteReminder(id: Long) {
        repository.deleteReminder(id)
        ReminderScheduler.cancel(getApplication(), id)
    }

    private fun reschedule(reminder: MealReminder) {
        if (reminder.enabled) {
            ReminderScheduler.schedule(getApplication(), reminder)
        } else {
            ReminderScheduler.cancel(getApplication(), reminder.id)
        }
    }

    fun rescheduleAll() {
        viewModelScope.launch {
            ReminderScheduler.rescheduleAll(getApplication())
        }
    }
}
