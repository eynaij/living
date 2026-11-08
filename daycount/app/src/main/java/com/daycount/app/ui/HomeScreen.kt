package com.daycount.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daycount.app.data.CountdownEvent
import com.daycount.app.ui.theme.LocalDayCountColors
import com.daycount.app.ui.theme.accentColor
import com.daycount.app.util.DayMath
import kotlin.math.abs

@Composable
fun HomeScreen(
    events: List<CountdownEvent>,
    todayEpoch: Long,
    onAddEvent: () -> Unit,
    onEventClick: (CountdownEvent) -> Unit
) {
    val ext = LocalDayCountColors.current
    val sorted = events.sortedWith(
        compareBy(
            { if (it.epochDay - todayEpoch >= 0) 0 else 1 },
            { if (it.epochDay - todayEpoch >= 0) it.epochDay else -it.epochDay }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ext.backgroundTop, ext.backgroundBottom)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddEvent,
                    modifier = Modifier.navigationBarsPadding(),
                    shape = CircleShape,
                    containerColor = ext.ink,
                    contentColor = ext.backgroundTop
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加事件")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = 110.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Header(
                        count = events.size,
                        todayEpoch = todayEpoch,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (sorted.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(sorted, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            todayEpoch = todayEpoch,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(count: Int, todayEpoch: Long, modifier: Modifier = Modifier) {
    val ext = LocalDayCountColors.current
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "倒数日",
            style = MaterialTheme.typography.headlineSmall,
            color = ext.ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$count 个事件 · ${DayMath.fullDate(todayEpoch)} ${DayMath.weekday(todayEpoch)}",
            style = MaterialTheme.typography.labelSmall,
            color = ext.muted
        )
    }
}

@Composable
private fun EmptyState() {
    val ext = LocalDayCountColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "—",
            style = MaterialTheme.typography.displayLarge,
            color = ext.line
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "还没有倒数日",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = ext.ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点击右下角 + 添加第一个事件",
            fontSize = 12.sp,
            color = ext.muted
        )
    }
}

@Composable
private fun EventCard(
    event: CountdownEvent,
    todayEpoch: Long,
    onClick: () -> Unit
) {
    val ext = LocalDayCountColors.current
    val days = event.epochDay - todayEpoch
    val accent = accentColor(event.colorIndex)
    val isPast = days < 0
    val numberSize = when {
        abs(days) >= 1000 -> 34.sp
        abs(days) >= 100 -> 42.sp
        else -> 46.sp
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ext.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, ext.line)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = event.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ext.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "${DayMath.fullDate(event.epochDay)} · ${DayMath.weekday(event.epochDay)}",
                    fontSize = 12.sp,
                    color = ext.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            if (days == 0L) {
                Text(
                    text = "今天",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = abs(days).toString(),
                        fontSize = numberSize,
                        lineHeight = (numberSize.value + 4).sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-1.5).sp,
                        color = if (isPast) accent.copy(alpha = 0.45f) else accent,
                        maxLines = 1
                    )
                    Text(
                        text = if (isPast) "天前" else "天后",
                        fontSize = 11.sp,
                        color = ext.muted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}
