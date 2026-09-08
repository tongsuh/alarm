package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
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
    onTestProfile: (FlashProfile) -> Unit,
    onDismiss: () -> Unit
) {
    // 是否处于编辑/新建状态
    var isFormOpen by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    // 表单状态 (亮灭时间单位为秒，去除总循环参数)
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#FF1A00") }
    var brightness by remember { mutableStateOf(0.85f) }
    var onDurationSecStr by remember { mutableStateOf("1.5") }
    var offDurationSecStr by remember { mutableStateOf("1.0") }

    val presetColors = listOf(
        "#FF1A00" to "AppleWatch夜视红",
        "#FFA726" to "暖阳橙",
        "#FFFFFF" to "纯净白",
        "#00E5FF" to "青空蓝",
        "#76FF03" to "荧光绿",
        "#E040FB" to "柔光紫"
    )

    fun startCreateNew() {
        editingProfileId = null
        name = "自定义模板 ${profiles.size + 1}"
        colorHex = "#FF1A00"
        brightness = 0.85f
        onDurationSecStr = "1.5"
        offDurationSecStr = "1.0"
        isFormOpen = true
    }

    fun startEdit(profile: FlashProfile) {
        editingProfileId = profile.id
        name = profile.name
        colorHex = profile.targetColorHex
        brightness = profile.targetBrightness
        onDurationSecStr = profile.onDurationSec.toString()
        offDurationSecStr = profile.offDurationSec.toString()
        isFormOpen = true
    }

    fun getCurrentFormProfile(): FlashProfile {
        val onSec = onDurationSecStr.toFloatOrNull()?.coerceAtLeast(0.1f) ?: 1.5f
        val offSec = offDurationSecStr.toFloatOrNull()?.coerceAtLeast(0.1f) ?: 1.0f
        return FlashProfile(
            id = editingProfileId ?: UUID.randomUUID().toString(),
            name = name.ifBlank { "亮屏模板" },
            targetColorHex = colorHex,
            targetBrightness = brightness,
            onDurationSec = onSec,
            offDurationSec = offSec
        )
    }

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
                        text = if (isFormOpen) "取消" else "完成",
                        color = IosOrange,
                        fontSize = 17.sp,
                        modifier = Modifier.clickable {
                            if (isFormOpen) isFormOpen = false else onDismiss()
                        }
                    )
                    Text(
                        text = if (isFormOpen) (if (editingProfileId == null) "新建模板" else "编辑模板") else "亮屏闪烁模板",
                        color = IosTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isFormOpen) {
                        Text(
                            text = "保存",
                            color = IosOrange,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onSaveProfile(getCurrentFormProfile())
                                isFormOpen = false
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.width(36.dp))
                    }
                }

                if (isFormOpen) {
                    // 编辑/新建表单
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. 快速效果测试按钮 (固定10秒全流程测试)
                        Button(
                            onClick = { onTestProfile(getCurrentFormProfile()) },
                            colors = ButtonDefaults.buttonColors(containerColor = IosCardSurfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = IosOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("测试此效果 (10秒)", color = IosOrange, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 模板名称
                        IosGroupCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("模板名称", color = IosTextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 颜色挑选
                        IosGroupCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("闪烁发光颜色", color = IosTextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    presetColors.forEach { (hex, colorName) ->
                                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Red }
                                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) IosOrange else Color.White.copy(alpha = 0.4f),
                                                    shape = CircleShape
                                                )
                                                .clickable { colorHex = hex }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 亮度调节
                        IosGroupCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("闪烁背光亮度", color = IosTextPrimary, fontSize = 16.sp)
                                    Text("${(brightness * 100).toInt()}%", color = IosOrange, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = brightness,
                                    onValueChange = { brightness = it },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = IosTextPrimary,
                                        activeTrackColor = IosOrange
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 节奏时间 (单位为秒，去除了总循环参数)
                        IosGroupCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = onDurationSecStr,
                                    onValueChange = { onDurationSecStr = it },
                                    label = { Text("亮屏时间(秒)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = offDurationSecStr,
                                    onValueChange = { offDurationSecStr = it },
                                    label = { Text("暗屏时间(秒)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = IosTextPrimary,
                                        unfocusedTextColor = IosTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "提示：总闪烁时间直接跟随闹钟的【自动停止时长】，在响铃全周期内按此亮灭节奏持续呼吸闪烁。",
                                color = IosTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                } else {
                    // 模板列表展示
                    Button(
                        onClick = { startCreateNew() },
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
                                        .clickable { startEdit(profile) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
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
                                                text = "亮${profile.onDurationSec}秒 / 灭${profile.offDurationSec}秒 · 亮度${(profile.targetBrightness * 100).toInt()}%",
                                                color = IosTextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 编辑按钮
                                        IconButton(onClick = { startEdit(profile) }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑模板", tint = IosOrange)
                                        }
                                        // 删除按钮 (允许删除模板)
                                        if (profiles.size > 1) {
                                            IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "删除模板", tint = Color.Red.copy(alpha = 0.8f))
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
}
