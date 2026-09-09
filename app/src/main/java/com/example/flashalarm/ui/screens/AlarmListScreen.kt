package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onOpenBedsideMode: () -> Unit
) {
    Scaffold(
        containerColor = IosBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "闹钟",
                    color = IosTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 床头微光模式快捷入口
                    IconButton(onClick = onOpenBedsideMode) {
                        Icon(Icons.Default.Bedtime, contentDescription = "床头微光常亮模式", tint = Color(0xFF5E5CE6))
                    }
                    // 模板设置入口
                    IconButton(onClick = onOpenProfileManager) {
                        Icon(Icons.Default.Palette, contentDescription = "闪烁模板设置", tint = IosOrange)
                    }
                    // 新建闹钟按钮
                    IconButton(onClick = onAddNewAlarm) {
                        Icon(Icons.Default.Add, contentDescription = "添加闹钟", tint = IosOrange, modifier = Modifier.size(32.dp))
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
            // 床头微光常亮模式突出快捷卡片 (与 iPhone 端完全一致)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable(onClick = onOpenBedsideMode),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5E5CE6).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF5E5CE6).copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = Color(0xFF5E5CE6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "床头微光常亮模式",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "进入",
                                color = Color(0xFF5E5CE6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF5E5CE6).copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            "免点击保持常亮，闲置自动超微光防眩目",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 权限引导提示横幅 (关键: 引导用户开启悬浮窗权限，实现在其他 App 界面直接全屏闪烁)
            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable(onClick = onRequestOverlayPermission),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IosOrange.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IosOrange.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = IosOrange, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("点击开启【在其他应用上层显示/悬浮窗】", color = IosOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("开启后，在使用微信、玩游戏时闹钟才能直接在最顶层全屏闪烁", color = IosTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

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
                            color = IosTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右上角 ＋ 添加新闹钟",
                            fontSize = 15.sp,
                            color = IosTextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        val profile = profiles.find { it.id == alarm.flashProfileId }
                            ?: FlashProfile.PRESET_APPLE_WATCH_RED

                        IosAlarmItemRow(
                            alarm = alarm,
                            profile = profile,
                            onToggle = { onToggleAlarm(alarm, it) },
                            onClick = { onEditAlarm(alarm) }
                        )
                        HorizontalDivider(color = IosSeparator.copy(alpha = 0.5f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun IosAlarmItemRow(
    alarm: AlarmItem,
    profile: FlashProfile,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // iOS 风格超大时间显示
            Text(
                text = alarm.formattedTime,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = if (alarm.isEnabled) IosTextPrimary else IosTextSecondary
            )

            // 标签与重复
            Text(
                text = "${alarm.label}, ${alarm.repeatDaysSummary}",
                fontSize = 15.sp,
                color = if (alarm.isEnabled) IosTextPrimary else IosTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 第一行：声音状态与自动停止时长
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alarm.isSoundEnabled) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (alarm.isEnabled) IosOrange else IosTextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "声音：${alarm.ringtoneTitle}",
                        fontSize = 12.sp,
                        color = if (alarm.isEnabled) IosTextSecondary else IosTextTertiary,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        Icons.Default.MusicOff,
                        contentDescription = null,
                        tint = IosTextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "声音：已静音",
                        fontSize = 12.sp,
                        color = IosTextTertiary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "· ${alarm.autoDismissSummary}",
                    fontSize = 12.sp,
                    color = IosTextTertiary
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // 第二行：亮屏与手环/手机震动状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alarm.isFlashEnabled) {
                    Icon(
                        Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (alarm.isEnabled) IosOrange else IosTextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "亮屏：${profile.name}",
                        fontSize = 12.sp,
                        color = if (alarm.isEnabled) IosTextSecondary else IosTextTertiary
                    )
                } else {
                    Icon(
                        Icons.Default.FlashOff,
                        contentDescription = null,
                        tint = IosTextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "亮屏：未开启",
                        fontSize = 12.sp,
                        color = IosTextTertiary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "· 📳 ${alarm.vibrationSummary}",
                    fontSize = 12.sp,
                    color = if (alarm.isEnabled && alarm.isVibrationEnabled) IosTextSecondary else IosTextTertiary
                )
            }

            if (alarm.isIntervalRepeatEnabled) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔁 间隔${alarm.intervalRepeatMinutes}分重响${alarm.intervalRepeatTimes}次",
                        fontSize = 12.sp,
                        color = IosOrange
                    )
                }
            }
        }

        // iOS 经典平滑开关
        Switch(
            checked = alarm.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = IosTextPrimary,
                checkedTrackColor = IosGreen,
                uncheckedThumbColor = IosTextSecondary,
                uncheckedTrackColor = IosCardSurfaceVariant
            )
        )
    }
}
