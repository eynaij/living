package com.mealpal.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealpal.app.data.MealReminder
import com.mealpal.app.data.Platform
import com.mealpal.app.data.Store
import com.mealpal.app.ui.theme.LocalMealPalColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    stores: List<Store>,
    reminders: List<MealReminder>,
    onStoreClick: (Store) -> Unit,
    onStoreEdit: (Store) -> Unit,
    onAddStore: () -> Unit,
    onToggleReminder: (MealReminder, Boolean) -> Unit,
    onReminderTimeChange: (MealReminder, Int, Int) -> Unit
) {
    val colors = LocalMealPalColors.current
    val context = LocalContext.current

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.backgroundTop, colors.backgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 32.dp)
        ) {
            Text(
                text = "干饭助手",
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date(nowMillis)),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink
            )

            Spacer(modifier = Modifier.height(28.dp))

            CountdownCard(reminders = reminders, nowMillis = nowMillis)

            Spacer(modifier = Modifier.height(32.dp))

            SectionHeader(text = "一键下单")
            Spacer(modifier = Modifier.height(12.dp))

            if (stores.isEmpty()) {
                EmptyHint(text = "还没有店铺，点下方「添加店铺」把常吃的店放进来")
            } else {
                stores.forEach { store ->
                    StoreButton(
                        store = store,
                        onClick = { onStoreClick(store) },
                        onEdit = { onStoreEdit(store) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAddStore) {
                Text(text = "+ 添加店铺", color = colors.accent)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(text = "每日提醒")
            Spacer(modifier = Modifier.height(12.dp))

            reminders.forEach { reminder ->
                ReminderRow(
                    reminder = reminder,
                    onToggle = { enabled -> onToggleReminder(reminder, enabled) },
                    onTimeClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onReminderTimeChange(reminder, hour, minute)
                            },
                            reminder.hour,
                            reminder.minute,
                            true
                        ).show()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "到点后会弹出通知，点通知里的店铺名即可直达下单页",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 13.sp,
                color = colors.muted
            )
        }
    }
}

@Composable
private fun CountdownCard(reminders: List<MealReminder>, nowMillis: Long) {
    val colors = LocalMealPalColors.current
    val next = reminders
        .filter { it.enabled }
        .map { it to it.nextTriggerMillis(nowMillis) }
        .minByOrNull { it.second }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.ink)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (next == null) {
                Text(
                    text = "提醒已关闭",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.backgroundTop
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "在下方开启每日提醒",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.backgroundTop.copy(alpha = 0.6f)
                )
            } else {
                val (reminder, triggerAt) = next
                val diff = (triggerAt - nowMillis).coerceAtLeast(0)
                val hours = diff / 3_600_000
                val minutes = (diff % 3_600_000) / 60_000
                val seconds = (diff % 60_000) / 1000
                Text(
                    text = "距 ${reminder.label} ${reminder.timeText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.backgroundTop.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "%02d:%02d:%02d".format(hours, minutes, seconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.backgroundTop
                )
            }
        }
    }
}

@Composable
private fun StoreButton(store: Store, onClick: () -> Unit, onEdit: () -> Unit) {
    val colors = LocalMealPalColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (store.enabled) colors.card else colors.card.copy(alpha = 0.5f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = store.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 20.sp,
                    color = if (store.enabled) colors.ink else colors.muted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = store.platform.displayName +
                        if (!store.enabled) " · 已停用" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 13.sp,
                    color = platformColor(store.platform, colors.accent, colors.muted)
                )
            }
            TextButton(onClick = onEdit) {
                Text(text = "编辑", color = colors.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: MealReminder,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    val colors = LocalMealPalColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink
                )
                TextButton(onClick = onTimeClick) {
                    Text(
                        text = reminder.timeText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.accent
                    )
                }
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalMealPalColors.current.muted
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 14.sp,
        color = LocalMealPalColors.current.muted
    )
}

private fun platformColor(
    platform: Platform,
    accent: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color
) = when (platform) {
    Platform.MEITUAN -> androidx.compose.ui.graphics.Color(0xFFE8A13D)
    Platform.TAOBAO -> accent
    Platform.OTHER -> muted
}
