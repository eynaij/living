package com.mealpal.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealpal.app.ui.EditStoreScreen
import com.mealpal.app.ui.HomeScreen
import com.mealpal.app.ui.MealPalViewModel
import com.mealpal.app.ui.theme.MealPalTheme
import com.mealpal.app.util.LinkLauncher

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            MealPalTheme {
                MealPalApp()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }
}

@Composable
private fun MealPalApp() {
    val viewModel: MealPalViewModel = viewModel()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun closeEditor() {
        editorOpen = false
        editingId = null
    }

    // Reschedule alarms on cold start so they survive process death
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.rescheduleAll()
    }

    BackHandler(enabled = editorOpen) { closeEditor() }

    if (editorOpen) {
        val editing = stores.firstOrNull { it.id == editingId }
        EditStoreScreen(
            initial = editing,
            onBack = { closeEditor() },
            onSave = { name, platform, link, enabled ->
                viewModel.saveStore(editingId, name, platform, link, enabled)
                closeEditor()
            },
            onDelete = {
                editingId?.let { viewModel.deleteStore(it) }
                closeEditor()
            }
        )
    } else {
        HomeScreen(
            stores = stores,
            reminders = reminders,
            onStoreClick = { store -> LinkLauncher.launch(context, store.link) },
            onStoreEdit = { store ->
                editingId = store.id
                editorOpen = true
            },
            onAddStore = {
                editingId = null
                editorOpen = true
            },
            onToggleReminder = { reminder, enabled ->
                viewModel.toggleReminder(reminder, enabled)
            },
            onReminderTimeChange = { reminder, hour, minute ->
                viewModel.saveReminder(
                    reminder.id, reminder.label, hour, minute, reminder.enabled
                )
            }
        )
    }
}
