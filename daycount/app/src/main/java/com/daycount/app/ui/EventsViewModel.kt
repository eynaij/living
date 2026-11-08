package com.daycount.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.daycount.app.data.CountdownEvent
import com.daycount.app.data.EventRepository
import com.daycount.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.StateFlow

class EventsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EventRepository(application)

    val events: StateFlow<List<CountdownEvent>> = repository.events

    fun save(id: Long?, title: String, epochDay: Long, colorIndex: Int) {
        repository.upsert(
            CountdownEvent(
                id = id ?: System.currentTimeMillis(),
                title = title.trim(),
                epochDay = epochDay,
                colorIndex = colorIndex
            )
        )
        WidgetUpdater.refreshAll(getApplication())
    }

    fun delete(id: Long) {
        repository.delete(id)
        WidgetUpdater.refreshAll(getApplication())
    }
}
