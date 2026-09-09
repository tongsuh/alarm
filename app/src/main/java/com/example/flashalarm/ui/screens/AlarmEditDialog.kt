package com.example.flashalarm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.model.VibrationPatternType
import com.example.flashalarm.ui.components.IosWheelTimePicker
import com.example.flashalarm.ui.theme.*
import com.example.flashalarm.util.VibrationHelper
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
    currentSelectedAudioUri: String? = null,
    hasOverlayPermission: Boolean = true,
    onRequestOverlayPermission: () -> Unit = {}
) {
    val now = Calendar.getInstance()
    var hour by remember { mutableStateOf(initialAlarm?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initialAlarm?.minute ?: ((now.get(Calendar.MINUTE) + 2) % 60)) }
    var label by remember { mutableStateOf(initialAlarm?.label ?: "闹钟") }
    var selectedDays by remember { mutableStateOf(initialAlarm?.repeatDays ?: emptySet()) }

    // 声音与闪烁独立开关
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

    // 自动停止时长 (默认 30 秒)
    val initialTotalSec = initialAlarm?.autoDismissSec ?: 30
    var autoDismissMinutes by remember { mutableStateOf((initialTotalSec / 60).toString()) }
    var autoDismissSeconds by remember { mutableStateOf((initialTotalSec % 60).toString()) }

    val context = LocalContext.current

    // 手环与手机震动设置
    var isVibrationEnabled by remember { mutableStateOf(initialAlarm?.isVibrationEnabled ?: true) }
    var vibrationDurationSec by remember { mutableStateOf((initialAlarm?.vibrationDurationSec ?: 15).toString()) }
    var selectedVibrationPatternId by remember { mutableStateOf(initialAlarm?.vibrationPatternId ?: "strong") }
    var currentlyPreviewingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            VibrationHelper.stopPreview(context)
        }
    }

    // 间隔重响功能 (如间隔30分钟，重复2次)
    var isIntervalRepeatEnabled by remember { mutableStateOf(initialAlarm?.isIntervalRepeatEnabled ?: false) }
    var intervalRepeatMinutes by remember { mutableStateOf((initialAlarm?.intervalRepeatMinutes ?: 30).toString()) }
    var intervalRepeatTimes by remember { mutableStateOf((initialAlarm?.intervalRepeatTimes ?: 2).toString()) }

    // 选中的模板 ID
    var selectedProfileId by remember {
        mutableStateOf(initialAlarm?.flashProfileId ?: profiles.firstOrNull()?.id ?: FlashProfile.PRESET_APPLE_WATCH_RED.id)
    }

    val daysMap = listOf(
        1 to "一", 2 to "二", 3 to "三", 4 to "四",
        5 to "五", 6 to "六", 7 to "日"
    )

    // 重复周期摘要计算
    val repeatSummary = remember(selectedDays) {
        when {
            selectedDays.isEmpty() -> "从不"
            selectedDays.size == 7 -> "每天"
            selectedDays == setOf(1, 2, 3, 4, 5) -> "工作日"
            selectedDays == setOf(6, 7) -> "周末"
            else -> {
                val names = listOf(1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四", 5 to "周五", 6 to "周六", 7 to "周日")
                selectedDays.sorted().mapNotNull { d -> names.find { it.first == d }?.second }.joinToString("、")
            }
        }
    }

    Dialog(
        onDismissRequest = {
            VibrationHelper.stopPreview(context)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // ================= 顶部导航条 (极简奢华黑橙配) =================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "取消",
                        color = IosOrange,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.clickable {
                            VibrationHelper.stopPreview(context)
                            onDismiss()
                        }
                    )
                    Text(
                        text = if (initialAlarm == null) "添加闹钟" else "编辑闹钟",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "存储",
                        color = IosOrange,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            VibrationHelper.stopPreview(context)
                            val mins = autoDismissMinutes.toIntOrNull() ?: 0
                            val secs = autoDismissSeconds.toIntOrNull() ?: 30
                            val totalSec = (mins * 60 + secs).coerceAtLeast(5)

                            val repeatIntervalMins = intervalRepeatMinutes.toIntOrNull() ?: 30
                            val repeatTimes = intervalRepeatTimes.toIntOrNull() ?: 2
                            val vibSec = vibrationDurationSec.toIntOrNull()?.coerceAtLeast(1) ?: 15

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
                                currentIntervalIndex = 0,
                                isVibrationEnabled = isVibrationEnabled,
                                vibrationDurationSec = vibSec,
                                vibrationPatternId = selectedVibrationPatternId
                            )
                            onSave(finalAlarm)
                        }
                    )
                }

                // ================= 内容滚动区 =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. 鼓轮时间选择器卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IosWheelTimePicker(
                                initialHour = hour,
                                initialMinute = minute,
                                onTimeChanged = { h, m ->
                                    hour = h
                                    minute = m
                                }
                            )
                        }
                    }

                    // 2. 标签与周期
                    LuxuryEditCard {
                        // 标签输入行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("标签", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (label.isEmpty()) {
                                    Text("闹钟", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp)
                                }
                                BasicTextField(
                                    value = label,
                                    onValueChange = { label = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(IosOrange),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        LuxuryDivider()

                        // 重复周期选择
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("重复周期", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = repeatSummary,
                                    color = IosOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 7 个极简白黑圆钮排布
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                daysMap.forEach { (index, shortName) ->
                                    val isSelected = selectedDays.contains(index)
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color.White else Color.White.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                if (isSelected) BorderStroke(0.dp, Color.Transparent)
                                                else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                                CircleShape
                                            )
                                            .clickable {
                                                selectedDays = if (isSelected) selectedDays - index else selectedDays + index
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = shortName,
                                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 间隔重响功能
                    LuxuryEditCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IosOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, tint = IosOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("间隔重响", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text("初次响铃后，定时再次提醒", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = isIntervalRepeatEnabled,
                                onCheckedChange = { isIntervalRepeatEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IosOrange,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (isIntervalRepeatEnabled) {
                            LuxuryDivider()
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 间隔分钟输入
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = intervalRepeatMinutes,
                                            onValueChange = { intervalRepeatMinutes = it.filter { c -> c.isDigit() } },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            cursorBrush = SolidColor(IosOrange),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("分钟后", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                    }

                                    // 重复次数输入
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicTextField(
                                            value = intervalRepeatTimes,
                                            onValueChange = { intervalRepeatTimes = it.filter { c -> c.isDigit() } },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            cursorBrush = SolidColor(IosOrange),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("次重响", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "💡 首次响铃后，每隔 ${intervalRepeatMinutes.ifBlank { "0" }} 分钟提醒一次，共再响 ${intervalRepeatTimes.ifBlank { "0" }} 次",
                                    color = IosOrange.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // 4. 声音提醒设置
                    LuxuryEditCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IosOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = IosOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("声音提醒", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text("响铃时播放音频", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = isSoundEnabled,
                                onCheckedChange = { isSoundEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IosOrange,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (isSoundEnabled) {
                            LuxuryDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onPickAudio)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("提示音 / 音乐", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = ringtoneTitle,
                                        color = IosOrange,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 5. 全屏视觉闪烁设置
                    LuxuryEditCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IosOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = IosOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("全屏视觉闪烁", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text("锁屏点亮并平滑律动闪烁", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = isFlashEnabled,
                                onCheckedChange = { isFlashEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IosOrange,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (isFlashEnabled) {
                            if (!hasOverlayPermission) {
                                LuxuryDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onRequestOverlayPermission)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = IosOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "⚠️ 未开启【在其他应用上层显示】权限",
                                            color = IosOrange,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "使用其他应用或游戏时，需要此权限才能在最顶层规律闪烁。点击立即开启。",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            LuxuryDivider()
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("闪烁模板", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                                    Text(
                                        text = "管理/新建模板 ⚙️",
                                        color = IosOrange,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable(onClick = onOpenProfileManager)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    profiles.forEach { profile ->
                                        val isSelected = selectedProfileId == profile.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent
                                                )
                                                .border(
                                                    if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                                    else BorderStroke(0.dp, Color.Transparent),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedProfileId = profile.id }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(profile.colorInt))
                                                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = profile.name,
                                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                                        fontSize = 15.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = "亮 ${profile.onDurationSec}s / 灭 ${profile.offDurationSec}s · ${(profile.targetBrightness * 100).toInt()}% 亮度",
                                                        color = Color.White.copy(alpha = 0.45f),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "已选择",
                                                    tint = IosOrange,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. 手环与手机震动提醒 (多周期震动 + 手环联动)
                    LuxuryEditCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IosOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Vibration, contentDescription = null, tint = IosOrange, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("手环与手机震动", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text("联动手环与手机多周期律动", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = isVibrationEnabled,
                                onCheckedChange = { isVibrationEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IosOrange,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                                    uncheckedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (isVibrationEnabled) {
                            LuxuryDivider()
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("震动持续时长", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                                    Row(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        BasicTextField(
                                            value = vibrationDurationSec,
                                            onValueChange = { vibrationDurationSec = it.filter { ch -> ch.isDigit() } },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            cursorBrush = SolidColor(IosOrange),
                                            singleLine = true,
                                            modifier = Modifier.width(40.dp)
                                        )
                                        Text("秒", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // 快捷时长胶囊
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(10, 15, 30, 60).forEach { sec ->
                                        val isCurrent = vibrationDurationSec == sec.toString()
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isCurrent) IosOrange.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f)
                                                )
                                                .border(
                                                    if (isCurrent) BorderStroke(1.dp, IosOrange.copy(alpha = 0.6f))
                                                    else BorderStroke(0.dp, Color.Transparent),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { vibrationDurationSec = sec.toString() }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${sec}s",
                                                color = if (isCurrent) IosOrange else Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("震动节奏类型", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    VibrationPatternType.values().forEach { vType ->
                                        val isVibSelected = selectedVibrationPatternId == vType.id
                                        val isPreviewing = currentlyPreviewingId == vType.id

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isVibSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent
                                                )
                                                .border(
                                                    if (isVibSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                                    else BorderStroke(0.dp, Color.Transparent),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    selectedVibrationPatternId = vType.id
                                                    currentlyPreviewingId = vType.id
                                                    VibrationHelper.playPreview(context, vType) {
                                                        if (currentlyPreviewingId == vType.id) {
                                                            currentlyPreviewingId = null
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = vType.title,
                                                        color = if (isVibSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                                        fontSize = 15.sp,
                                                        fontWeight = if (isVibSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    if (isPreviewing) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "📳 手机与手环试震中...",
                                                            color = IosOrange,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = vType.description,
                                                    color = Color.White.copy(alpha = 0.45f),
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // 试震药丸按钮
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                            if (isPreviewing) IosOrange.copy(alpha = 0.2f)
                                                            else Color.White.copy(alpha = 0.08f)
                                                        )
                                                        .clickable {
                                                            selectedVibrationPatternId = vType.id
                                                            currentlyPreviewingId = vType.id
                                                            VibrationHelper.playPreview(context, vType) {
                                                                if (currentlyPreviewingId == vType.id) {
                                                                    currentlyPreviewingId = null
                                                                }
                                                            }
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (isPreviewing) "震动中" else "试震",
                                                        color = if (isPreviewing) IosOrange else Color.White.copy(alpha = 0.8f),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                if (isVibSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "已选择",
                                                        tint = IosOrange,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "💡 手环同步指引：请在【华为运动健康 / 小米运动 App ➡️ 消息通知】中开启【闪屏闹钟】通知权限，响铃时手环即可同步感应多周期律动。",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // 7. 自由设置自动停止时长
                    LuxuryEditCard {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text("自动停止时长", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "响铃达到设定时间后自动静音并关闭，防止持续耗电",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // 快捷胶囊预设: 30秒, 1分钟, 3分钟, 5分钟, 10分钟
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    30 to "30秒",
                                    60 to "1分钟",
                                    180 to "3分钟",
                                    300 to "5分钟"
                                ).forEach { (totalSec, label) ->
                                    val currentTotal = (autoDismissMinutes.toIntOrNull() ?: 0) * 60 + (autoDismissSeconds.toIntOrNull() ?: 0)
                                    val isCurrent = currentTotal == totalSec
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCurrent) IosOrange.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f)
                                            )
                                            .border(
                                                if (isCurrent) BorderStroke(1.dp, IosOrange.copy(alpha = 0.6f))
                                                else BorderStroke(0.dp, Color.Transparent),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                autoDismissMinutes = (totalSec / 60).toString()
                                                autoDismissSeconds = (totalSec % 60).toString()
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isCurrent) IosOrange else Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 自定义分和秒
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = autoDismissMinutes,
                                        onValueChange = { autoDismissMinutes = it.filter { ch -> ch.isDigit() } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        ),
                                        cursorBrush = SolidColor(IosOrange),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("分钟", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = autoDismissSeconds,
                                        onValueChange = { autoDismissSeconds = it.filter { ch -> ch.isDigit() } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        ),
                                        cursorBrush = SolidColor(IosOrange),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("秒", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
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
fun LuxuryEditCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(content = content)
    }
}

@Composable
fun LuxuryDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.06f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}
