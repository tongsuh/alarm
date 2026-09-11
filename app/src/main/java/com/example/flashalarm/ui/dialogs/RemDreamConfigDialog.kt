package com.example.flashalarm.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flashalarm.sleep.RemDreamConfig
import com.example.flashalarm.ui.theme.IosOrange
import com.example.flashalarm.ui.theme.IosRed
import kotlin.math.roundToInt

/**
 * 黑曜石奢华极简风格 · 清醒梦与 REM 触梦参数配置抽屉
 */
@Composable
fun RemDreamConfigDialog(
    initialConfig: RemDreamConfig,
    onSaveConfig: (RemDreamConfig) -> Unit,
    onSimulateCue: () -> Unit,
    onDismiss: () -> Unit
) {
    var cycleMode by remember { mutableStateOf(initialConfig.cycleMode) }
    var flashBrightness by remember { mutableFloatStateOf(initialConfig.flashBrightness) }
    var isWhisperEnabled by remember { mutableStateOf(initialConfig.isWhisperEnabled) }
    var whisperVolume by remember { mutableFloatStateOf(initialConfig.whisperVolume) }
    var isVibrationEnabled by remember { mutableStateOf(initialConfig.isVibrationEnabled) }
    var vibrationPatternId by remember { mutableStateOf(initialConfig.vibrationPatternId) }
    var cueDurationSec by remember { mutableIntStateOf(initialConfig.cueDurationSec) }
    var isSimulating by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
            border = BorderStroke(1.dp, Color(0xFF28282D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "清醒梦触梦设置",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "后半夜温和注入意识 · 绝不惊醒肉体",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. REM 触梦周期选择
                    ConfigSectionCard(title = "🎯 目标触发窗口") {
                        val modes = listOf(
                            "cycle_4_5h" to "第4周期 (约4.6h)",
                            "cycle_6_0h" to "第5周期 (约6.0h)",
                            "both" to "双周期黄金守护"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0C0C0E), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            modes.forEach { (id, label) ->
                                val isSelected = cycleMode == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) IosOrange.copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { cycleMode = id }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) IosOrange else Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // 2. 屏幕暗红微光亮度
                    ConfigSectionCard(title = "🔴 屏幕暗红呼吸微光") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("微光强度 (透过眼睑激发视神经)", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                            Text("${(flashBrightness * 100).roundToInt()}%", color = IosOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = flashBrightness,
                            onValueChange = { flashBrightness = it },
                            valueRange = 0.15f..0.40f,
                            colors = SliderDefaults.colors(
                                thumbColor = IosOrange,
                                activeTrackColor = IosOrange,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    // 3. 耳边低语
                    ConfigSectionCard(title = "🎙️ 耳边柔和轻语") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("开启低语现实检验", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                            Switch(
                                checked = isWhisperEnabled,
                                onCheckedChange = { isWhisperEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IosOrange
                                )
                            )
                        }
                        if (isWhisperEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("低语音量 (潜意识识别)", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                                Text("${(whisperVolume * 100).roundToInt()}%", color = IosOrange, fontSize = 12.sp)
                            }
                            Slider(
                                value = whisperVolume,
                                onValueChange = { whisperVolume = it },
                                valueRange = 0.15f..0.40f,
                                colors = SliderDefaults.colors(
                                    thumbColor = IosOrange,
                                    activeTrackColor = IosOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }

                    // 4. 手环微震
                    ConfigSectionCard(title = "📳 手环心跳微震") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("联动穿戴设备心跳节拍", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                                Text("心跳微弱双击 (Heartbeat)，手腕感知无惊醒", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = isVibrationEnabled,
                                onCheckedChange = { isVibrationEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = IosOrange
                                )
                            )
                        }
                    }

                    // 5. 触梦持续时长
                    ConfigSectionCard(title = "⏱️ 单次触梦持续时间") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("时间到自动静默退场 (无需手动关闭)", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                            Text("${cueDurationSec} 秒", color = IosOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = cueDurationSec.toFloat(),
                            onValueChange = { cueDurationSec = it.roundToInt() },
                            valueRange = 15f..40f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = IosOrange,
                                activeTrackColor = IosOrange,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    // 6. 白天试听与体验按钮
                    Button(
                        onClick = {
                            isSimulating = !isSimulating
                            onSimulateCue()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSimulating) IosRed.copy(alpha = 0.25f) else Color(0xFF221815)
                        ),
                        border = BorderStroke(1.dp, IosOrange.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = IosOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isSimulating) "正在体验 15秒触梦 (点击可停止)" else "🎧 模拟试听触梦 (15秒体验)",
                            color = IosOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 底部保存按钮
                Button(
                    onClick = {
                        val newConfig = initialConfig.copy(
                            cycleMode = cycleMode,
                            flashBrightness = flashBrightness,
                            isWhisperEnabled = isWhisperEnabled,
                            whisperVolume = whisperVolume,
                            isVibrationEnabled = isVibrationEnabled,
                            vibrationPatternId = vibrationPatternId,
                            cueDurationSec = cueDurationSec
                        )
                        onSaveConfig(newConfig)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosOrange)
                ) {
                    Text("保存设置", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConfigSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1F)),
        border = BorderStroke(1.dp, Color(0xFF26262B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
