package com.daycount.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daycount.app.ui.EditScreen
import com.daycount.app.ui.EventsViewModel
import com.daycount.app.ui.HomeScreen
import com.daycount.app.ui.theme.DayCountTheme
import com.daycount.app.widget.WidgetUpdater
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WidgetUpdater.refreshAll(this)
        setContent {
            DayCountTheme {
                DayCountApp()
            }
        }
    }
}

@Composable
private fun DayCountApp() {
    val viewModel: EventsViewModel = viewModel()
    val events by viewModel.events.collectAsStateWithLifecycle()

    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    var todayEpoch by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                todayEpoch = LocalDate.now().toEpochDay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun closeEditor() {
        editorOpen = false
        editingId = null
    }

    BackHandler(enabled = editorOpen) { closeEditor() }

    if (editorOpen) {
        val editing = events.firstOrNull { it.id == editingId }
        EditScreen(
            initial = editing,
            onBack = { closeEditor() },
            onSave = { title, epochDay, colorIndex ->
                viewModel.save(editingId, title, epochDay, colorIndex)
                closeEditor()
            },
            onDelete = {
                editingId?.let { viewModel.delete(it) }
                closeEditor()
            }
        )
    } else {
        HomeScreen(
            events = events,
            todayEpoch = todayEpoch,
            onAddEvent = {
                editingId = null
                editorOpen = true
            },
            onEventClick = { event ->
                editingId = event.id
                editorOpen = true
            }
        )
    }
}
