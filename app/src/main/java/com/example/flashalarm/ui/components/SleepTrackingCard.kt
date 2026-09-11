package com.example.flashalarm.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.ui.theme.IosOrange
import com.example.flashalarm.ui.theme.IosRed

/**
 * 黑曜石奢华极简风格 · 清醒梦入眠感知与 REM 触梦卡片
 */
@Composable
fun SleepTrackingCard(
    isTrackingRunning: Boolean,
    statusTitle: String,
    statusDetail: String,
    isPhoneFlat: Boolean,
    isWhiteNoiseActive: Boolean,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 监测中的呼吸微光动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131316)),
        border = BorderStroke(
            1.dp,
            if (isTrackingRunning) IosOrange.copy(alpha = 0.45f) else Color(0xFF24242A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. 卡片头部：图标 + 标题 + 设置齿轮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isTrackingRunning) IosOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = if (isTrackingRunning) IosOrange else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "清醒梦 · 入眠感知",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isTrackingRunning) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(IosOrange.copy(alpha = pulseAlpha), CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (isTrackingRunning) statusTitle else "睡前放置床垫 · 自动捕捉入眠并温和触梦",
                            color = if (isTrackingRunning) IosOrange else Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontWeight = if (isTrackingRunning) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }

                // 设置按钮 (磨砂圆钮)
                IconButton(
                    onClick = onOpenConfig,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 实时状态徽章条 (姿态、抗噪、白噪音)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(
                    text = if (isPhoneFlat) "姿态: 平放正常" else "姿态: 偏斜",
                    isGood = isPhoneFlat
                )
                StatusBadge(
                    text = "抗噪: 180~800Hz",
                    isGood = true
                )
                StatusBadge(
                    text = if (isWhiteNoiseActive) "白噪音: 正在避让" else "白噪音: 就绪",
                    isGood = true
                )
            }

            // 3. 运行时的动态细节提示
            AnimatedVisibility(visible = isTrackingRunning) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = statusDetail,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. 核心主控制按钮
            if (!isTrackingRunning) {
                Button(
                    onClick = onStartTracking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221612)),
                    border = BorderStroke(1.dp, IosOrange.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = IosOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "开始今夜就寝监测 (平放手机)",
                        color = IosOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = onStopTracking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosRed.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, IosRed.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = IosRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "结束监测 / 起床",
                        color = IosRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, isGood: Boolean) {
    Box(
        modifier = Modifier
            .background(Color(0xFF0D0D10), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        if (isGood) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp
            )
        }
    }
}
