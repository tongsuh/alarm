package com.example.flashalarm.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.flashalarm.sleep.RemDreamConfig
import com.example.flashalarm.sleep.SensorDiagnosticHelper
import com.example.flashalarm.sleep.SleepTrackingService
import com.example.flashalarm.ui.theme.IosOrange
import com.example.flashalarm.ui.theme.IosRed
import kotlin.math.roundToInt

/**
 * 黑曜石奢华极简风格 · 清醒梦参数配置与传感器实时诊断抽屉
 */
@Composable
fun RemDreamConfigDialog(
    initialConfig: RemDreamConfig,
    onSaveConfig: (RemDreamConfig) -> Unit,
    onSimulateCue: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val diagnosticHelper = remember { SensorDiagnosticHelper(context) }

    // 打开面板即开启传感器高灵敏度自检，关闭时彻底销毁释放
    DisposableEffect(Unit) {
        diagnosticHelper.startDiagnostics()
        onDispose {
            diagnosticHelper.stopDiagnostics()
        }
    }

    val liveTiltAngle by diagnosticHelper.tiltAngle.collectAsState()
    val isLiveFlat by diagnosticHelper.isFlat.collectAsState()
    val liveDb by diagnosticHelper.liveDb.collectAsState()
    val noiseFloorDb by diagnosticHelper.noiseFloorDb.collectAsState()
    val isMicResponsive by diagnosticHelper.isMicResponsive.collectAsState()
    val isSimulatingService by SleepTrackingService.isSimulatingCue.collectAsState()

    var cycleMode by remember { mutableStateOf(initialConfig.cycleMode) }
    var flashBrightness by remember { mutableFloatStateOf(initialConfig.flashBrightness) }
    var isWhisperEnabled by remember { mutableStateOf(initialConfig.isWhisperEnabled) }
    var whisperVolume by remember { mutableFloatStateOf(initialConfig.whisperVolume) }
    var isVibrationEnabled by remember { mutableStateOf(initialConfig.isVibrationEnabled) }
    var vibrationPatternId by remember { mutableStateOf(initialConfig.vibrationPatternId) }
    var cueDurationSec by remember { mutableIntStateOf(initialConfig.cueDurationSec) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
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

                Spacer(modifier = Modifier.height(14.dp))

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ========== 0. 核心新增：传感器实时诊断与校准面板 ==========
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101013)),
                        border = BorderStroke(1.dp, IosOrange.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isLiveFlat && isMicResponsive) Color(0xFF4CAF50) else IosOrange, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "🧪 传感器硬件实时自检",
                                    color = IosOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 姿态自检条
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("床垫水平姿态", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Text(
                                    if (isLiveFlat) "🟢 倾角 ${"%.1f".format(liveTiltAngle)}° (平放良好)" else "❌ 倾角 ${"%.1f".format(liveTiltAngle)}° (请水平放置)",
                                    color = if (isLiveFlat) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // 倾角可视化进度条
                            val angleProgress = (liveTiltAngle / 60f).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { angleProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = if (isLiveFlat) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 麦克风拾音自检条
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("麦克风拾音测试", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Text(
                                    "实时: ${liveDb.toInt()}dB | 底噪: ${noiseFloorDb.toInt()}dB",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // 动态分贝跳动条
                            val dbProgress = ((liveDb - 20f) / 60f).coerceIn(0.05f, 1f)
                            LinearProgressIndicator(
                                progress = { dbProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = if (liveDb > 50f) IosOrange else Color(0xFF29B6F6),
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isMicResponsive) "🟢 麦克风灵敏 · 成功检测到吹气或人声波形" else "💡 请对着麦克风轻吹一口气或说话，查看音量条跳跃",
                                color = if (isMicResponsive) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        }
                    }

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

                    // 6. 白天试听与体验按钮 (支持随时退出)
                    Button(
                        onClick = {
                            if (isSimulatingService) {
                                SleepTrackingService.stopSimulateCue(context)
                            } else {
                                onSimulateCue()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSimulatingService) IosRed.copy(alpha = 0.25f) else Color(0xFF221815)
                        ),
                        border = BorderStroke(1.dp, if (isSimulatingService) IosRed.copy(alpha = 0.6f) else IosOrange.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            if (isSimulatingService) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isSimulatingService) IosRed else IosOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isSimulatingService) "⏹ 停止触梦试听 (或轻触屏幕任意位置)" else "🎧 模拟试听触梦 (15秒体验 · 轻触屏幕可退)",
                            color = if (isSimulatingService) IosRed else IosOrange,
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
