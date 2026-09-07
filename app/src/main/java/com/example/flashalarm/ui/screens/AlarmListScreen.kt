package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onToggleAlarm: (AlarmItem, Boolean) -> Unit,
    onDeleteAlarm: (AlarmItem) -> Unit,
    onEditAlarm: (AlarmItem) -> Unit,
    onAddNewAlarm: () -> Unit,
    onOpenProfileManager: () -> Unit,
    onQuickTest: () -> Unit
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
                    // 快速测试按钮
                    IconButton(onClick = onQuickTest) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "快速测试5秒后闹钟", tint = IosOrange)
                    }
                    // 模板管理
                    IconButton(onClick = onOpenProfileManager) {
                        Icon(Icons.Default.Settings, contentDescription = "闪烁模板设置", tint = IosOrange)
                    }
                    // 新建闹钟
                    IconButton(onClick = onAddNewAlarm) {
                        Icon(Icons.Default.Add, contentDescription = "添加闹钟", tint = IosOrange, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                    .padding(paddingValues)
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
            // iOS 风格超大时间
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

            Spacer(modifier = Modifier.height(4.dp))

            // 状态徽标 (声音、亮屏模式、自动关闭时长)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (alarm.isSoundEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = IosOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(alarm.ringtoneTitle, fontSize = 12.sp, color = IosTextSecondary, maxLines = 1)
                    }
                } else {
                    Text("[静音]", fontSize = 12.sp, color = IosTextTertiary)
                }

                if (alarm.isFlashEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = IosOrange, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(profile.name, fontSize = 12.sp, color = IosTextSecondary)
                    }
                }

                Text("· ${alarm.autoDismissSummary}", fontSize = 12.sp, color = IosTextTertiary)
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
