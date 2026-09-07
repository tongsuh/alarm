package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditDialog(
    initialAlarm: AlarmItem?,
    profiles: List<FlashProfile>,
    onSave: (AlarmItem) -> Unit,
    onDismiss: () -> Unit,
    onOpenProfileManager: () -> Unit
) {
    val now = Calendar.getInstance()
    var hour by remember { mutableStateOf(initialAlarm?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initialAlarm?.minute ?: ((now.get(Calendar.MINUTE) + 2) % 60)) }
    var label by remember { mutableStateOf(initialAlarm?.label ?: "起床闹钟") }
    var selectedDays by remember { mutableStateOf(initialAlarm?.repeatDays ?: emptySet()) }
    var autoDismissSec by remember { mutableStateOf(initialAlarm?.autoDismissSec ?: 60) }
    var selectedProfileId by remember {
        mutableStateOf(initialAlarm?.flashProfileId ?: profiles.firstOrNull()?.id ?: FlashProfile.PRESET_SUNRISE.id)
    }

    val daysMap = listOf(
        1 to "一", 2 to "二", 3 to "三", 4 to "四",
        5 to "五", 6 to "六", 7 to "日"
    )

    val autoDismissOptions = listOf(30, 60, 120, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialAlarm == null) "新建闹钟" else "编辑闹钟")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 时间显示与快捷微调
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = String.format("%02d : %02d", hour, minute),
                            fontSize = 44.sp,
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { hour = (hour + 1) % 24 }) {
                                Text("+1 小时")
                            }
                            OutlinedButton(onClick = { minute = (minute + 5) % 60 }) {
                                Text("+5 分钟")
                            }
                        }
                    }
                }

                // 标签输入
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("闹钟备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 重复周期
                Text("重复周期", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysMap.forEach { (dayIndex, dayLabel) ->
                        val isSelected = selectedDays.contains(dayIndex)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        selectedDays - dayIndex
                                    } else {
                                        selectedDays + dayIndex
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLabel,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 特性 1：自动关闭时长
                Text("未操作自动关闭", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    autoDismissOptions.forEach { sec ->
                        FilterChip(
                            selected = autoDismissSec == sec,
                            onClick = { autoDismissSec = sec },
                            label = { Text("${sec}秒") }
                        )
                    }
                }

                // 特性 2：选择亮屏闪烁模板
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("亮屏闪烁模板", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onOpenProfileManager) {
                        Text("管理模板")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        val isChosen = selectedProfileId == profile.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProfileId = profile.id },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChosen) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isChosen) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(profile.colorInt))
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "亮${profile.onDurationMs}ms / 灭${profile.offDurationMs}ms · 循环${profile.totalDurationCircle}次",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAlarm = AlarmItem(
                        id = initialAlarm?.id ?: System.currentTimeMillis(),
                        hour = hour,
                        minute = minute,
                        label = label.ifBlank { "闹钟" },
                        isEnabled = true,
                        repeatDays = selectedDays,
                        autoDismissSec = autoDismissSec,
                        flashProfileId = selectedProfileId
                    )
                    onSave(finalAlarm)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
