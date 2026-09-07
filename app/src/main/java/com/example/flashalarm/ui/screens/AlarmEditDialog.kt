package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.ui.components.IosWheelTimePicker
import com.example.flashalarm.ui.theme.*
import java.util.Calendar

@Composable
fun AlarmEditDialog(
    initialAlarm: AlarmItem?,
    profiles: List<FlashProfile>,
    onSave: (AlarmItem) -> Unit,
    onDismiss: () -> Unit,
    onPickAudio: () -> Unit,
    onOpenProfileManager: () -> Unit,
    currentSelectedAudioTitle: String = "默认闹钟铃声",
    currentSelectedAudioUri: String? = null
) {
    val now = Calendar.getInstance()
    var hour by remember { mutableStateOf(initialAlarm?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initialAlarm?.minute ?: ((now.get(Calendar.MINUTE) + 2) % 60)) }
    var label by remember { mutableStateOf(initialAlarm?.label ?: "闹钟") }
    var selectedDays by remember { mutableStateOf(initialAlarm?.repeatDays ?: emptySet()) }

    // 声音与亮屏独立开关
    var isSoundEnabled by remember { mutableStateOf(initialAlarm?.isSoundEnabled ?: true) }
    var isFlashEnabled by remember { mutableStateOf(initialAlarm?.isFlashEnabled ?: true) }

    // 音频设置
    var ringtoneTitle by remember {
        mutableStateOf(currentSelectedAudioTitle.ifBlank { initialAlarm?.ringtoneTitle ?: "默认闹钟铃声" })
    }
    var ringtoneUri by remember {
        mutableStateOf(currentSelectedAudioUri ?: initialAlarm?.ringtoneUri)
    }

    LaunchedEffect(currentSelectedAudioTitle, currentSelectedAudioUri) {
        if (currentSelectedAudioTitle.isNotBlank()) {
            ringtoneTitle = currentSelectedAudioTitle
        }
        if (currentSelectedAudioUri != null) {
            ringtoneUri = currentSelectedAudioUri
        }
    }

    // 自由设置自动停止时长
    val initialTotalSec = initialAlarm?.autoDismissSec ?: 60
    var autoDismissMinutes by remember { mutableStateOf((initialTotalSec / 60).toString()) }
    var autoDismissSeconds by remember { mutableStateOf((initialTotalSec % 60).toString()) }

    // 特性 3：间隔重响功能 (如间隔30分钟，重复2次)
    var isIntervalRepeatEnabled by remember { mutableStateOf(initialAlarm?.isIntervalRepeatEnabled ?: false) }
    var intervalRepeatMinutes by remember { mutableStateOf((initialAlarm?.intervalRepeatMinutes ?: 30).toString()) }
    var intervalRepeatTimes by remember { mutableStateOf((initialAlarm?.intervalRepeatTimes ?: 2).toString()) }

    // 选中的模板 ID
    var selectedProfileId by remember {
        mutableStateOf(initialAlarm?.flashProfileId ?: profiles.firstOrNull()?.id ?: FlashProfile.PRESET_APPLE_WATCH_RED.id)
    }

    val daysMap = listOf(
        1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
        5 to "周五", 6 to "周六", 7 to "周日"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = IosBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // 顶部导航条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "取消",
                        color = IosOrange,
                        fontSize = 17.sp,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                    Text(
                        text = if (initialAlarm == null) "添加闹钟" else "编辑闹钟",
                        color = IosTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "存储",
                        color = IosOrange,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val mins = autoDismissMinutes.toIntOrNull() ?: 1
                            val secs = autoDismissSeconds.toIntOrNull() ?: 0
                            val totalSec = (mins * 60 + secs).coerceAtLeast(5)

                            val repeatIntervalMins = intervalRepeatMinutes.toIntOrNull() ?: 30
                            val repeatTimes = intervalRepeatTimes.toIntOrNull() ?: 2

                            val finalAlarm = AlarmItem(
                                id = initialAlarm?.id ?: System.currentTimeMillis(),
                                hour = hour,
                                minute = minute,
                                label = label.ifBlank { "闹钟" },
                                isEnabled = true,
                                repeatDays = selectedDays,
                                isSoundEnabled = isSoundEnabled,
                                isFlashEnabled = isFlashEnabled,
                                ringtoneUri = ringtoneUri,
                                ringtoneTitle = ringtoneTitle,
                                autoDismissSec = totalSec,
                                flashProfileId = selectedProfileId,
                                isIntervalRepeatEnabled = isIntervalRepeatEnabled,
                                intervalRepeatMinutes = repeatIntervalMins.coerceAtLeast(1),
                                intervalRepeatTimes = repeatTimes.coerceAtLeast(1),
                                currentIntervalIndex = 0
                            )
                            onSave(finalAlarm)
                        }
                    )
                }

                // 内容滚动区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. 仿 iOS 鼓轮时间选择器
                    IosWheelTimePicker(
                        initialHour = hour,
                        initialMinute = minute,
                        onTimeChanged = { h, m ->
                            hour = h
                            minute = m
                        }
                    )

                    // 2. 标签与周期
                    IosGroupCard {
                        // 标签行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("标签", color = IosTextPrimary, fontSize = 17.sp)
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = IosTextPrimary,
                                    unfocusedTextColor = IosTextPrimary,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                placeholder = { Text("闹钟", color = IosTextSecondary) },
                                modifier = Modifier.width(180.dp),
                                singleLine = true
                            )
                        }

                        IosDivider()

                        // 重复周期
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("重复", color = IosTextPrimary, fontSize = 17.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                daysMap.forEach { (index, name) ->
                                    val isSelected = selectedDays.contains(index)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) IosOrange else IosCardSurfaceVariant)
                                            .clickable {
                                                selectedDays = if (isSelected) selectedDays - index else selectedDays + index
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = name.replace("周", ""),
                                            color = if (isSelected) IosBackground else IosTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 特性 3：间隔重复功能 (例如隔30分钟，重复2次)
                    IosGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, contentDescription = null, tint = IosOrange)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("间隔重响", color = IosTextPrimary, fontSize = 17.sp)
                                    Text("初次响铃后，定时再次提醒", color = IosTextSecondary, fontSize = 13.sp)
                                }
                            }
                            Switch(
                                checked = isIntervalRepeatEnabled,
                                onCheckedChange = { isIntervalRepeatEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = IosTextPrimary,
                                    checkedTrackColor = IosOrange
                                )
                            )
                        }

                        if (isIntervalRepeatEnabled) {
                            IosDivider()
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = intervalRepeatMinutes,
                                            onValueChange = { intervalRepeatMinutes = it.filter { c -> c.isDigit() } },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = IosTextPrimary,
                                                unfocusedTextColor = IosTextPrimary,
                                                focusedContainerColor = IosCardSurfaceVariant,
                                                unfocusedContainerColor = IosCardSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("分钟后", color = IosTextPrimary)
                                    }

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = intervalRepeatTimes,
                                            onValueChange = { intervalRepeatTimes = it.filter { c -> c.isDigit() } },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = IosTextPrimary,
                                                unfocusedTextColor = IosTextPrimary,
                                                focusedContainerColor = IosCardSurfaceVariant,
                                                unfocusedContainerColor = IosCardSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("次重响", color = IosTextPrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "例如：首次响铃后，每隔 ${intervalRepeatMinutes.ifBlank { "0" }} 分钟重响 1 次，共再响 ${intervalRepeatTimes.ifBlank { "0" }} 次",
                                    color = IosOrange,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // 4. 声音设置
                    IosGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = IosOrange)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("声音提醒", color = IosTextPrimary, fontSize = 17.sp)
                                    Text("响铃时播放音频", color = IosTextSecondary, fontSize = 13.sp)
                                }
                            }
                            Switch(
                                checked = isSoundEnabled,
                                onCheckedChange = { isSoundEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = IosTextPrimary,
                                    checkedTrackColor = IosOrange
                                )
                            )
                        }

                        if (isSoundEnabled) {
                            IosDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPickAudio)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("提示音 / 音乐", color = IosTextPrimary, fontSize = 17.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ringtoneTitle,
                                        color = IosOrange,
                                        fontSize = 15.sp,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = IosTextSecondary)
                                }
                            }
                        }
                    }

                    // 5. 亮屏闪烁设置 (直接选择模板列表)
                    IosGroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, tint = IosOrange)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("全屏视觉闪烁", color = IosTextPrimary, fontSize = 17.sp)
                                    Text("锁屏点亮并渐黑动态闪烁", color = IosTextSecondary, fontSize = 13.sp)
                                }
                            }
                            Switch(
                                checked = isFlashEnabled,
                                onCheckedChange = { isFlashEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = IosTextPrimary,
                                    checkedTrackColor = IosOrange
                                )
                            )
                        }

                        if (isFlashEnabled) {
                            IosDivider()
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("选择闪烁模板", color = IosTextSecondary, fontSize = 13.sp)
                                    Text(
                                        text = "管理/自定义模板 ⚙️",
                                        color = IosOrange,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable(onClick = onOpenProfileManager)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                profiles.forEach { profile ->
                                    val isSelected = selectedProfileId == profile.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) IosCardSurfaceVariant else Color.Transparent)
                                            .clickable { selectedProfileId = profile.id }
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(profile.colorInt))
                                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = profile.name,
                                                    color = if (isSelected) IosOrange else IosTextPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = "亮${profile.onDurationMs}ms / 灭${profile.offDurationMs}ms · 循环${profile.totalDurationCircle}次",
                                                    color = IosTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "已选择", tint = IosOrange)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. 自由设置自动停止时长
                    IosGroupCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("未操作自动停止时长", color = IosTextPrimary, fontSize = 17.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "响铃达到设定时间后自动静音并关闭，防止持续耗电",
                                color = IosTextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = autoDismissMinutes,
                                        onValueChange = { autoDismissMinutes = it.filter { ch -> ch.isDigit() } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = IosTextPrimary,
                                            unfocusedTextColor = IosTextPrimary,
                                            focusedContainerColor = IosCardSurfaceVariant,
                                            unfocusedContainerColor = IosCardSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("分钟", color = IosTextPrimary)
                                }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = autoDismissSeconds,
                                        onValueChange = { autoDismissSeconds = it.filter { ch -> ch.isDigit() } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = IosTextPrimary,
                                            unfocusedTextColor = IosTextPrimary,
                                            focusedContainerColor = IosCardSurfaceVariant,
                                            unfocusedContainerColor = IosCardSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("秒", color = IosTextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
fun IosGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardSurface)
    ) {
        Column(content = content)
    }
}

@Composable
fun IosDivider() {
    HorizontalDivider(
        color = IosSeparator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}
