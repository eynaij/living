@file:OptIn(ExperimentalMaterial3Api::class)

package com.daycount.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daycount.app.data.AccentPalette
import com.daycount.app.data.CountdownEvent
import com.daycount.app.ui.theme.LocalDayCountColors
import com.daycount.app.util.DayMath
import java.time.LocalDate

@Composable
fun EditScreen(
    initial: CountdownEvent?,
    onBack: () -> Unit,
    onSave: (title: String, epochDay: Long, colorIndex: Int) -> Unit,
    onDelete: () -> Unit
) {
    val ext = LocalDayCountColors.current
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var epochDay by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.epochDay ?: LocalDate.now().toEpochDay())
    }
    var colorIndex by rememberSaveable(initial?.id) { mutableStateOf(initial?.colorIndex ?: 0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val canSave = title.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ext.backgroundTop, ext.backgroundBottom)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = ext.ink
                        )
                    }
                    Text(
                        text = if (initial == null) "新建事件" else "编辑事件",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ext.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onSave(title.trim(), epochDay, colorIndex) },
                        enabled = canSave
                    ) {
                        Text("保存", fontWeight = FontWeight.Medium)
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    SectionLabel("事件名称")
                    Spacer(Modifier.height(10.dp))
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 40) title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 26.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Light,
                            color = ext.ink
                        ),
                        cursorBrush = SolidColor(ext.ink),
                        decorationBox = { innerTextField ->
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (title.isEmpty()) {
                                        Text(
                                            text = "例如：春节回家",
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Light,
                                            color = ext.muted
                                        )
                                    }
                                    innerTextField()
                                }
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = ext.line)
                            }
                        }
                    )

                    Spacer(Modifier.height(34.dp))
                    SectionLabel("日期")
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = ext.card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ext.line)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = DayMath.fullDate(epochDay),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ext.ink
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${DayMath.weekday(epochDay)} · ${DayMath.relativeText(epochDay)}",
                                    fontSize = 11.sp,
                                    color = ext.muted
                                )
                            }
                            Text(
                                text = "修改",
                                fontSize = 12.sp,
                                color = ext.muted
                            )
                        }
                    }

                    Spacer(Modifier.height(34.dp))
                    SectionLabel("颜色")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (index in 0 until AccentPalette.size) {
                            val selected = colorIndex == index
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { colorIndex = index }
                                    .border(
                                        width = 1.5.dp,
                                        color = if (selected) ext.ink else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(AccentPalette.color(index, ext.isDark)))
                            )
                        }
                    }

                    if (initial != null) {
                        Spacer(Modifier.height(52.dp))
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "删除这个事件",
                                fontSize = 13.sp,
                                color = Color(0xFFC0392B)
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        epochDay = it / 86_400_000L
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除事件？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("删除", color = Color(0xFFC0392B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalDayCountColors.current.muted
    )
}
