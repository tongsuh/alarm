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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.ui.theme.*
import java.util.UUID

@Composable
fun FlashProfileManageDialog(
    profiles: List<FlashProfile>,
    onSaveProfile: (FlashProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(false) }

    // 表单状态（默认选中 Apple Watch 夜间深红 #FF1A00）
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#FF1A00") }
    var brightness by remember { mutableStateOf(0.85f) }
    var onDurationMs by remember { mutableStateOf("1500") }
    var offDurationMs by remember { mutableStateOf("1000") }
    var totalCycles by remember { mutableStateOf("15") }

    // 经典预设色彩列表（包含 Apple Watch 夜视深红、暖阳橙、琥珀黄、冷白等）
    val presetColors = listOf(
        "#FF1A00" to "AppleWatch夜视红",
        "#FFA726" to "暖阳橙",
        "#FFFFFF" to "纯净白",
        "#00E5FF" to "青空蓝",
        "#76FF03" to "荧光绿",
        "#E040FB" to "柔光紫"
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
                    .padding(horizontal = 16.dp)
            ) {
                // 顶部导航栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCreatingNew) "取消" else "关闭",
                        color = IosOrange,
                        fontSize = 17.sp,
                        modifier = Modifier.clickable {
                            if (isCreatingNew) isCreatingNew = false else onDismiss()
                        }
                    )
                    Text(
                        text = if (isCreatingNew) "新建模板" else "亮屏闪烁模板",
                        color = IosTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isCreatingNew) {
                        Text(
                            text = "保存",
                            color = IosOrange,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val profile = FlashProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = name.ifBlank { "自定义模板" },
                                    targetColorHex = colorHex,
                                    targetBrightness = brightness,
                                    onDurationMs = onDurationMs.toLongOrNull() ?: 1500L,
                                    offDurationMs = offDurationMs.toLongOrNull() ?: 1000L,
                                    totalDurationCircle = totalCycles.toIntOrNull() ?: 10
                                )
                                onSaveProfile(profile)
                                isCreatingNew = false
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.width(36.dp))
                    }
                }

                if (isCreatingNew) {
                    // 新建模板表单
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IosGroupCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("模板名称", color = IosTextSecondary, fontSize = 13.sp)
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = { Text("例如：暗室护眼夜红", color = IosTextSecondary) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        IosGroupCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("选择色彩（推荐 Apple Watch 夜间深红）", color = IosTextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    presetColors.forEach { (hex, title) ->
                                        val parsedColor = Color(android.graphics.Color.parseColor(hex))
                                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(parsedColor)
                                                .border(
                                                    width = if (isSelected) 3.5.dp else 1.dp,
                                                    color = if (isSelected) IosOrange else Color.Gray.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                                .clickable { colorHex = hex }
                                        )
                                    }
                                }
                            }
                        }

                        IosGroupCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("目标屏幕亮度", color = IosTextPrimary)
                                    Text("${(brightness * 100).toInt()}%", color = IosOrange, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = brightness,
                                    onValueChange = { brightness = it },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = IosOrange,
                                        activeTrackColor = IosOrange
                                    )
                                )
                            }
                        }

                        IosGroupCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = onDurationMs,
                                    onValueChange = { onDurationMs = it.filter { c -> c.isDigit() } },
                                    label = { Text("亮屏(ms)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = offDurationMs,
                                    onValueChange = { offDurationMs = it.filter { c -> c.isDigit() } },
                                    label = { Text("暗屏(ms)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            IosDivider()
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = totalCycles,
                                    onValueChange = { totalCycles = it.filter { c -> c.isDigit() } },
                                    label = { Text("闪烁循环总次数 (0为一直循环)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    // 模板列表展示
                    Button(
                        onClick = {
                            name = "自命名模板 ${profiles.size + 1}"
                            isCreatingNew = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IosOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = IosBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新增自命名亮屏模板", color = IosBackground, fontWeight = FontWeight.Bold)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(profiles) { profile ->
                            IosGroupCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.colorInt))
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = profile.name,
                                                color = IosTextPrimary,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "亮${profile.onDurationMs}ms / 灭${profile.offDurationMs}ms · ${profile.totalDurationCircle}次 · 亮度${(profile.targetBrightness * 100).toInt()}%",
                                                color = IosTextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    if (!profile.id.startsWith("preset_")) {
                                        IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.8f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
