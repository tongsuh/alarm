package com.example.flashalarm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Palette
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
    onOpenProfileManager: () -> Unit
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

            // 第二行：亮屏状态（特性 2: 仅显示模板名字，不再显示长长的时间间隔）
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

                if (alarm.isIntervalRepeatEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "· 间隔${alarm.intervalRepeatMinutes}分重响${alarm.intervalRepeatTimes}次",
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
