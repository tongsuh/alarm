package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.flashalarm.model.FlashProfile
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashProfileManageDialog(
    profiles: List<FlashProfile>,
    onSaveProfile: (FlashProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }

    // 编辑表单状态
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#FFFFFF") }
    var brightness by remember { mutableStateOf(1.0f) }
    var onDurationMs by remember { mutableStateOf("1500") }
    var offDurationMs by remember { mutableStateOf("1000") }
    var totalCycles by remember { mutableStateOf("10") }

    val presetColors = listOf(
        "#FFFFFF", "#FFA726", "#FF5252", "#E040FB",
        "#00E5FF", "#76FF03", "#FFFF00", "#FF4081"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isCreatingNew) "新建亮屏模板" else "亮屏闪烁模板管理")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (isCreatingNew) {
                    // 新建模板表单
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("模板名称 (例如: 晨光柔和)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("选择唤醒颜色: $colorHex", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.forEach { hex ->
                            val parsed = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                Color.White
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(parsed)
                                    .border(
                                        width = if (colorHex == hex) 3.dp else 1.dp,
                                        color = if (colorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("屏幕亮度: ${(brightness * 100).toInt()}%")
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = 0.1f..1.0f,
                        steps = 8
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = onDurationMs,
                            onValueChange = { onDurationMs = it },
                            label = { Text("亮时长(ms)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = offDurationMs,
                            onValueChange = { offDurationMs = it },
                            label = { Text("暗时长(ms)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = totalCycles,
                        onValueChange = { totalCycles = it },
                        label = { Text("循环次数 (0为一直循环)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 现有模板列表
                    Button(
                        onClick = {
                            name = "自定义模板 ${profiles.size + 1}"
                            isCreatingNew = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新增自命名模板")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(profiles) { profile ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.colorInt))
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "亮${profile.onDurationMs}ms / 灭${profile.offDurationMs}ms · ${profile.totalDurationCircle}次 · 亮度${(profile.targetBrightness * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    // 内置模板不删除
                                    if (!profile.id.startsWith("preset_")) {
                                        IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "删除",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                Button(
                    onClick = {
                        val newProfile = FlashProfile(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { "自命名模板" },
                            targetColorHex = colorHex,
                            targetBrightness = brightness,
                            onDurationMs = onDurationMs.toLongOrNull() ?: 1500L,
                            offDurationMs = offDurationMs.toLongOrNull() ?: 1000L,
                            totalDurationCircle = totalCycles.toIntOrNull() ?: 10
                        )
                        onSaveProfile(newProfile)
                        isCreatingNew = false
                    }
                ) {
                    Text("保存模板")
                }
            }
        },
        dismissButton = {
            if (isCreatingNew) {
                TextButton(onClick = { isCreatingNew = false }) {
                    Text("取消")
                }
            }
        }
    )
}
