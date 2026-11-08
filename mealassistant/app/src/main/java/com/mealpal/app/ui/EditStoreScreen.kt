package com.mealpal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealpal.app.data.Platform
import com.mealpal.app.data.Store
import com.mealpal.app.ui.theme.LocalMealPalColors

@Composable
fun EditStoreScreen(
    initial: Store?,
    onBack: () -> Unit,
    onSave: (name: String, platform: Platform, link: String, enabled: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalMealPalColors.current

    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var link by rememberSaveable { mutableStateOf(initial?.link ?: "") }
    var enabled by rememberSaveable { mutableStateOf(initial?.enabled ?: true) }
    var platformName by rememberSaveable {
        mutableStateOf((initial?.platform ?: Platform.MEITUAN).name)
    }
    val platform = Platform.fromName(platformName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.backgroundTop, colors.backgroundBottom)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 32.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(text = "← 返回", color = colors.muted)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (initial == null) "添加店铺" else "编辑店铺",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink
        )

        Spacer(modifier = Modifier.height(28.dp))

        FieldLabel(text = "店铺名称")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("比如：老王麻辣烫", color = colors.muted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors()
        )

        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(text = "平台")
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Platform.entries.forEach { p ->
                FilterChip(
                    selected = platform == p,
                    onClick = { platformName = p.name },
                    label = { Text(p.displayName) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.ink,
                        selectedLabelColor = colors.backgroundTop
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        FieldLabel(text = "店铺链接")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "粘贴店铺页链接或分享口令链接",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            },
            minLines = 2,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "在美团/淘宝闪购里打开店铺页 → 分享 → 复制链接，粘贴到这里",
            fontSize = 12.sp,
            color = colors.muted,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "出现在一键下单和通知里",
                modifier = Modifier.weight(1f),
                color = colors.ink,
                fontSize = 15.sp
            )
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = { onSave(name, platform, link, enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.ink,
                contentColor = colors.backgroundTop
            )
        ) {
            Text(text = "保存", fontWeight = FontWeight.Medium)
        }

        if (initial != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "删除店铺", color = colors.accent)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalMealPalColors.current.muted
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalMealPalColors.current.ink,
    unfocusedBorderColor = LocalMealPalColors.current.line,
    focusedTextColor = LocalMealPalColors.current.ink,
    unfocusedTextColor = LocalMealPalColors.current.ink,
    cursorColor = LocalMealPalColors.current.ink
)
