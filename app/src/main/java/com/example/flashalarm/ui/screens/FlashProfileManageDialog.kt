package com.example.flashalarm.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flashalarm.model.FlashProfile
import com.example.flashalarm.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID

@Composable
fun FlashProfileManageDialog(
    profiles: List<FlashProfile>,
    onSaveProfile: (FlashProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onTestProfile: (FlashProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var isFormOpen by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    // 表单各项参数状态
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("#FF1A00") }
    var brightness by remember { mutableStateOf(0.85f) }
    var onDurationSec by remember { mutableStateOf(1.5f) }
    var offDurationSec by remember { mutableStateOf(1.0f) }

    // 色谱取色状态 (Hue: 0 ~ 360)
    var hueValue by remember { mutableStateOf(0f) }

    // 高端精选预设色卡
    val curatedPresets = remember {
        listOf(
            "#FF1A00" to "AppleWatch夜视红",
            "#FF0055" to "霓虹赤红",
            "#FF9100" to "暖阳琥珀",
            "#FFD600" to "高亮极金",
            "#00E676" to "极光翡翠",
            "#00E5FF" to "青空冰蓝",
            "#2979FF" to "深邃电蓝",
            "#D500F9" to "电光紫罗兰",
            "#FFFFFF" to "纯净亮白"
        )
    }

    fun startCreateNew() {
        editingProfileId = null
        name = "呼吸模板 ${profiles.size + 1}"
        colorHex = "#FF1A00"
        hueValue = 0f
        brightness = 0.85f
        onDurationSec = 1.5f
        offDurationSec = 1.0f
        isFormOpen = true
    }

    fun startEdit(profile: FlashProfile) {
        editingProfileId = profile.id
        name = profile.name
        colorHex = profile.targetColorHex
        brightness = profile.targetBrightness
        onDurationSec = profile.onDurationSec
        offDurationSec = profile.offDurationSec

        // 根据已有 HEX 计算初始色相
        try {
            val c = AndroidColor.parseColor(profile.targetColorHex)
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(c, hsv)
            hueValue = hsv[0]
        } catch (e: Exception) {
            hueValue = 0f
        }

        isFormOpen = true
    }

    fun getCurrentFormProfile(): FlashProfile {
        return FlashProfile(
            id = editingProfileId ?: UUID.randomUUID().toString(),
            name = name.ifBlank { "亮屏模板" },
            targetColorHex = colorHex,
            targetBrightness = brightness,
            onDurationSec = onDurationSec,
            offDurationSec = offDurationSec
        )
    }

    // 解析当前选中的 Compose Color
    val currentColor = remember(colorHex) {
        try {
            Color(AndroidColor.parseColor(colorHex))
        } catch (e: Exception) {
            Color(0xFFFF1A00)
        }
    }

    // 实时动态呼吸脉冲逻辑 (根据 onDuration 与 offDuration 真实交替)
    var isPulsingActive by remember { mutableStateOf(true) }
    LaunchedEffect(isFormOpen, onDurationSec, offDurationSec) {
        if (!isFormOpen) return@LaunchedEffect
        while (isActive) {
            isPulsingActive = true
            delay((onDurationSec * 1000).toLong().coerceAtLeast(100L))
            isPulsingActive = false
            delay((offDurationSec * 1000).toLong().coerceAtLeast(100L))
        }
    }

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (isPulsingActive) brightness.coerceIn(0.15f, 1f) else 0.04f,
        animationSpec = tween(
            durationMillis = if (isPulsingActive) 300 else 400,
            easing = FastOutSlowInEasing
        ),
        label = "animatedGlowAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
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
                    .padding(horizontal = 16.dp)
            ) {
                // 顶部导航条
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
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            if (isFormOpen) isFormOpen = false else onDismiss()
                        }
                    )
                    Text(
                        text = if (isFormOpen) (if (editingProfileId == null) "新建模板" else "编辑模板") else "呼吸闪烁模板",
                        color = Color.White,
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
                    // 表单编辑模式
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ================= 1. 实时节拍呼吸预览窗口 (iPhone 1:1 复刻) =================
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF0C0C0E))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // 模拟呼吸闪烁的发光层
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(animatedGlowAlpha)
                                        .background(currentColor)
                                )

                                // 浮现时钟与节拍信息
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.alpha(if (animatedGlowAlpha > 0.2f) 0.95f else 0.45f)
                                ) {
                                    Text(
                                        text = "07:30",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Light,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "开 ${String.format("%.1f", onDurationSec)}s / 关 ${String.format("%.1f", offDurationSec)}s · ${(brightness * 100).toInt()}% 亮度",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "实时呼吸节拍预览",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }

                        // ================= 2. 模板名称 =================
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("模板名称", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    placeholder = { Text("输入模板名称", color = Color.Gray) },
                                    modifier = Modifier.width(180.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // ================= 3. 光谱调色盘与颜色选择 =================
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("发光颜色调色盘", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)

                                    // 当前选中颜色圆点与 HEX 显示
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(currentColor)
                                                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = colorHex.uppercase(),
                                            color = IosOrange,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // 彩虹光谱滑动条 (Hue Spectrum)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Red, Color.Yellow, Color.Green,
                                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                )
                                            )
                                        )
                                )
                                Slider(
                                    value = hueValue,
                                    onValueChange = { h ->
                                        hueValue = h
                                        val rgb = AndroidColor.HSVToColor(floatArrayOf(h, 1.0f, 1.0f))
                                        colorHex = String.format("#%06X", (0xFFFFFF and rgb))
                                    },
                                    valueRange = 0f..360f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // 高级精选预设色卡选择行
                                Text("经典色卡", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(curatedPresets) { (hex, presetTitle) ->
                                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                                        val swatchColor = remember(hex) {
                                            try { Color(AndroidColor.parseColor(hex)) } catch (e: Exception) { Color.Red }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) IosOrange else Color.White.copy(alpha = 0.25f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    colorHex = hex
                                                    try {
                                                        val c = AndroidColor.parseColor(hex)
                                                        val hsv = FloatArray(3)
                                                        AndroidColor.colorToHSV(c, hsv)
                                                        hueValue = hsv[0]
                                                    } catch (e: Exception) {
                                                        hueValue = 0f
                                                    }
                                                }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // HEX 代码直接输入框
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("HEX 代码", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                                    OutlinedTextField(
                                        value = colorHex,
                                        onValueChange = { input ->
                                            val formatted = if (input.startsWith("#")) input else "#$input"
                                            colorHex = formatted
                                            if (formatted.length == 7) {
                                                try {
                                                    val c = AndroidColor.parseColor(formatted)
                                                    val hsv = FloatArray(3)
                                                    AndroidColor.colorToHSV(c, hsv)
                                                    hueValue = hsv[0]
                                                } catch (e: Exception) {
                                                    // 格式暂未闭合
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = IosOrange.copy(alpha = 0.6f),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                        modifier = Modifier.width(130.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // ================= 4. 亮度与节拍时间滑块 =================
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 亮度
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("峰值闪烁亮度", color = Color.White, fontSize = 15.sp)
                                    Text("${(brightness * 100).toInt()}%", color = IosOrange, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = brightness,
                                    onValueChange = { brightness = it },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = IosOrange
                                    )
                                )

                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                                // 亮起时长
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("亮起持续时长", color = Color.White, fontSize = 15.sp)
                                    Text("${String.format("%.1f", onDurationSec)} 秒", color = IosOrange, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = onDurationSec,
                                    onValueChange = { onDurationSec = (Math.round(it * 10f) / 10f) },
                                    valueRange = 0.2f..4.0f,
                                    steps = 37,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = IosOrange
                                    )
                                )

                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                                // 暗下时长
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("暗下间隔时长", color = Color.White, fontSize = 15.sp)
                                    Text("${String.format("%.1f", offDurationSec)} 秒", color = IosOrange, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = offDurationSec,
                                    onValueChange = { offDurationSec = (Math.round(it * 10f) / 10f) },
                                    valueRange = 0.2f..3.0f,
                                    steps = 27,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = IosOrange
                                    )
                                )
                            }
                        }

                        // ================= 5. 全屏实战测试按钮 =================
                        Button(
                            onClick = { onTestProfile(getCurrentFormProfile()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = IosOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("全屏闪烁效果测试 (10秒)", color = IosOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                } else {
                    // 模板列表展示
                    Button(
                        onClick = { startCreateNew() },
                        colors = ButtonDefaults.buttonColors(containerColor = IosOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("新增呼吸闪烁模板", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(profiles) { profile ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { startEdit(profile) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                            ) {
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
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.colorInt))
                                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = profile.name,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "开 ${profile.onDurationSec}s / 关 ${profile.offDurationSec}s · ${(profile.targetBrightness * 100).toInt()}% 亮度",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onTestProfile(profile) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "试运行", tint = Color.White.copy(alpha = 0.8f))
                                        }
                                        IconButton(onClick = { startEdit(profile) }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑模板", tint = IosOrange)
                                        }
                                        if (profiles.size > 1) {
                                            IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "删除模板", tint = Color.Red.copy(alpha = 0.7f))
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
