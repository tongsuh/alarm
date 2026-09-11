package com.example.flashalarm.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.ui.theme.*

import com.example.flashalarm.ui.components.SleepTrackingCard

@Composable
fun AlarmListScreen(
    alarms: List<AlarmItem>,
    profiles: List<FlashProfile>,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleAlarm: (AlarmItem, Boolean) -> Unit,
    onDeleteAlarm: (AlarmItem) -> Unit,
    onEditAlarm: (AlarmItem) -> Unit,
    onAddNewAlarm: () -> Unit,
    onOpenProfileManager: () -> Unit,
    isSleepTrackingRunning: Boolean = false,
    sleepStatusTitle: String = "未开启",
    sleepStatusDetail: String = "睡前放置床垫边缘 · 自动捕捉入眠并温和触梦",
    isPhoneFlat: Boolean = true,
    isWhiteNoiseActive: Boolean = false,
    onStartSleepTracking: () -> Unit = {},
    onStopSleepTracking: () -> Unit = {},
    onOpenSleepConfig: () -> Unit = {},
    onOpenSleepScreen: () -> Unit = {}
) {
    var alarmPendingDelete by remember { mutableStateOf<AlarmItem?>(null) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "闹钟",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 模板设置入口 (磨砂圆钮)
                    IconButton(
                        onClick = onOpenProfileManager,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "闪烁模板设置",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // 新建闹钟按钮 (醒目橙红加号)
                    IconButton(
                        onClick = onAddNewAlarm,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加闹钟",
                            tint = IosOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 悬浮窗核心权限引导横幅
            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF241505)),
                    border = BorderStroke(1.dp, IosOrange.copy(alpha = 0.4f)),
                    onClick = onRequestOverlayPermission
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(IosOrange.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = IosOrange, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "开启【在其他应用上层显示/悬浮窗】",
                                color = IosOrange,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "开启后，使用微信或玩游戏时闹钟才能直接在最顶层规律闪烁",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 清醒梦入眠感知与 REM 触梦卡片
            SleepTrackingCard(
                isTrackingRunning = isSleepTrackingRunning,
                statusTitle = sleepStatusTitle,
                statusDetail = sleepStatusDetail,
                isPhoneFlat = isPhoneFlat,
                isWhiteNoiseActive = isWhiteNoiseActive,
                onStartTracking = onStartSleepTracking,
                onStopTracking = onStopSleepTracking,
                onOpenConfig = onOpenSleepConfig,
                onOpenSleepScreen = onOpenSleepScreen,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "无闹钟",
                            fontSize = 24.sp,
                            color = Color.White.copy(alpha = 0.35f),
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右上角 ＋ 添加新闹钟",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        val profile = profiles.find { it.id == alarm.flashProfileId }
                            ?: FlashProfile.PRESET_APPLE_WATCH_RED

                        LuxuryAlarmCard(
                            alarm = alarm,
                            profile = profile,
                            onToggle = { onToggleAlarm(alarm, it) },
                            onClick = { onEditAlarm(alarm) },
                            onLongClick = { alarmPendingDelete = alarm }
                        )
                    }
                }
            }
        }
    }

    // 长按删除确认弹窗
    if (alarmPendingDelete != null) {
        val alarm = alarmPendingDelete!!
        AlertDialog(
            onDismissRequest = { alarmPendingDelete = null },
            title = {
                Text("删除闹钟", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "确定要删除 ${alarm.formattedTime} 的「${alarm.label}」闹钟吗？",
                    color = Color.White.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAlarm(alarm)
                        alarmPendingDelete = null
                    }
                ) {
                    Text("删除", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { alarmPendingDelete = null }) {
                    Text("取消", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LuxuryAlarmCard(
    alarm: AlarmItem,
    profile: FlashProfile,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 超大轻量时间显示 + 标签
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = alarm.formattedTime,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Light,
                        color = if (alarm.isEnabled) Color.White else Color.White.copy(alpha = 0.35f)
                    )

                    if (alarm.label.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = alarm.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (alarm.isEnabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 胶囊信息徽标列 (极简高级感药丸排版)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 重复周期药丸
                    Text(
                        text = alarm.repeatDaysSummary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (alarm.isEnabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = if (alarm.isEnabled) 0.08f else 0.03f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )

                    // 闪烁模板药丸
                    if (alarm.isFlashEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = if (alarm.isEnabled) 0.08f else 0.03f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(profile.colorInt))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = String.format("%.1fs/%.1fs", profile.onDurationSec, profile.offDurationSec),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (alarm.isEnabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // 震动药丸
                    if (alarm.isVibrationEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = if (alarm.isEnabled) 0.08f else 0.03f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.Vibration,
                                contentDescription = null,
                                tint = if (alarm.isEnabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${alarm.vibrationDurationSec}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (alarm.isEnabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // 音频状态药丸
                    if (alarm.isSoundEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = if (alarm.isEnabled) 0.08f else 0.03f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (alarm.isEnabled) IosOrange else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                if (alarm.isIntervalRepeatEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🔁 间隔${alarm.intervalRepeatMinutes}分钟 · 重响${alarm.intervalRepeatTimes}次",
                        fontSize = 11.sp,
                        color = IosOrange.copy(alpha = if (alarm.isEnabled) 0.9f else 0.4f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // iOS 标志性绿色平滑开关
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF34C759), // iOS 经典绿
                    uncheckedThumbColor = Color.LightGray,
                    uncheckedTrackColor = Color(0xFF2C2C2E),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
