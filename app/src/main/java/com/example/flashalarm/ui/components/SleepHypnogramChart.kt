package com.example.flashalarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.sleep.model.SleepEpoch
import com.example.flashalarm.sleep.model.SleepSession
import com.example.flashalarm.sleep.model.SleepStage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SleepHypnogramChart(
    session: SleepSession,
    epochs: List<SleepEpoch>,
    modifier: Modifier = Modifier
) {
    var selectedEpoch by remember { mutableStateOf<SleepEpoch?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF151518))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        // 头部标题与图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "睡眠结构催眠图",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Hypnogram 时序时段分布",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            // 触梦星标图例
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF241505))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = "✨", fontSize = 11.sp)
                Text(
                    text = "触梦提醒",
                    fontSize = 11.sp,
                    color = Color(0xFFFF9F0A),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 交互选中提示条 (点击图表任意点出现)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selectedEpoch != null) {
                val ep = selectedEpoch!!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ep.stage.composeColor, CircleShape)
                    )
                    Text(
                        text = "${timeFormat.format(Date(ep.timestamp))} · ${ep.stage.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (ep.breathBpm > 0f) {
                        Text(
                            text = "呼吸 ${ep.breathBpm.toInt()} bpm",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    if (ep.isCueTriggered) {
                        Text(
                            text = "✨ 黄金触梦已击发",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9F0A)
                        )
                    }
                }
            } else {
                Text(
                    text = "轻触或滑动图表可查看详细时刻状态",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 核心 Canvas 阶梯时序图
        if (epochs.isEmpty()) {
            // 空数据引导
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "昨夜暂无连续切片数据\n开启整夜就寝监测后将在此自动生成",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        } else {
            val stages = listOf(
                SleepStage.AWAKE to "清醒",
                SleepStage.REM to "REM",
                SleepStage.LIGHT to "浅睡",
                SleepStage.DEEP to "深睡"
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                // 左侧阶段文字标签
                Column(
                    modifier = Modifier
                        .height(180.dp)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    stages.forEach { (stage, label) ->
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = stage.composeColor.copy(alpha = 0.9f)
                        )
                    }
                }

                // 右侧图表区域
                Column(modifier = Modifier.weight(1f)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .pointerInput(epochs) {
                                detectTapGestures { offset ->
                                    val progress = (offset.x / size.width).coerceIn(0f, 1f)
                                    val index = (progress * (epochs.size - 1)).toInt().coerceIn(0, epochs.size - 1)
                                    selectedEpoch = epochs[index]
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height

                        // 四层 Y 坐标 (0.12, 0.38, 0.65, 0.90)
                        val yLevels = mapOf(
                            SleepStage.AWAKE to h * 0.12f,
                            SleepStage.REM to h * 0.38f,
                            SleepStage.LIGHT to h * 0.65f,
                            SleepStage.DEEP to h * 0.90f
                        )

                        // 绘制 4 条极弱背景参考虚线
                        yLevels.values.forEach { y ->
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // 绘制各 Epoch 的阶梯线条与触梦星标
                        val n = epochs.size
                        val stepX = w / (n - 1).coerceAtLeast(1)

                        // 绘制下部渐变发光填充与线条
                        for (i in 0 until n - 1) {
                            val epCurrent = epochs[i]
                            val epNext = epochs[i + 1]

                            val x1 = i * stepX
                            val y1 = yLevels[epCurrent.stage] ?: (h * 0.65f)
                            val x2 = (i + 1) * stepX
                            val y2 = yLevels[epNext.stage] ?: (h * 0.65f)

                            // 水平段
                            drawLine(
                                color = epCurrent.stage.composeColor,
                                start = Offset(x1, y1),
                                end = Offset(x2, y1),
                                strokeWidth = 3.dp.toPx()
                            )

                            // 垂直过渡连接段
                            if (y1 != y2) {
                                drawLine(
                                    color = epNext.stage.composeColor.copy(alpha = 0.7f),
                                    start = Offset(x2, y1),
                                    end = Offset(x2, y2),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }

                            // 如果该分钟触发了触梦，绘制亮金星标
                            if (epCurrent.isCueTriggered) {
                                drawCircle(
                                    color = Color(0xFFFF9F0A),
                                    radius = 6.dp.toPx(),
                                    center = Offset(x1, y1)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = Offset(x1, y1)
                                )
                            }
                        }

                        // 绘制最后一个点触梦标记 (若有)
                        if (epochs.last().isCueTriggered) {
                            val lastX = (n - 1) * stepX
                            val lastY = yLevels[epochs.last().stage] ?: (h * 0.65f)
                            drawCircle(
                                color = Color(0xFFFF9F0A),
                                radius = 6.dp.toPx(),
                                center = Offset(lastX, lastY)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // X 轴时间刻度
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = timeFormat.format(Date(session.startTimestamp)),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        val midTimestamp = (session.startTimestamp + session.endTimestamp) / 2
                        Text(
                            text = timeFormat.format(Date(midTimestamp)),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                        Text(
                            text = timeFormat.format(Date(session.endTimestamp)),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
