package com.example.flashalarm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.model.AlarmItem
import com.example.flashalarm.model.FlashProfile

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("闪烁闹钟") },
                actions = {
                    // 快速测试按钮（5秒后触发唤醒，便于验证锁屏与闪烁）
                    IconButton(onClick = onQuickTest) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "测试5秒后闹钟")
                    }
                    // 模板管理
                    IconButton(onClick = onOpenProfileManager) {
                        Icon(Icons.Default.Palette, contentDescription = "亮屏模板管理")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNewAlarm) {
                Icon(Icons.Default.Add, contentDescription = "添加闹钟")
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
                        "还没有闹钟，点击下方按钮添加",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddNewAlarm) {
                        Text("添加第一个闹钟")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    val profile = profiles.find { it.id == alarm.flashProfileId }
                        ?: FlashProfile.PRESET_SUNRISE

                    AlarmItemCard(
                        alarm = alarm,
                        profile = profile,
                        onToggle = { onToggleAlarm(alarm, it) },
                        onDelete = { onDeleteAlarm(alarm) },
                        onClick = { onEditAlarm(alarm) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmItemCard(
    alarm: AlarmItem,
    profile: FlashProfile,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.formattedTime,
                    fontSize = 38.sp,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface
                    else Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${alarm.label} · ${alarm.repeatDaysSummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "模板: ${profile.name} · ${alarm.autoDismissSec}秒自动关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除闹钟",
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}
