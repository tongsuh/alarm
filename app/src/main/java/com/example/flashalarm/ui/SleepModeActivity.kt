package com.example.flashalarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.flashalarm.sleep.SleepTrackingService
import com.example.flashalarm.ui.theme.IosOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * 专为夜间就寝打造的纯黑极暗睡眠态 Activity (SleepModeActivity)
 *
 * 交互架构：
 * 1. 退出黑屏界面 (临时离开看手机)：轻触屏幕任意空白区域即可返回主界面，后台睡眠感知持续低功耗守护。
 * 2. 结束睡眠追踪 (晨起结算)：底部防误触【▷ 向右滑动结束睡眠】滑块，只有滑动达到阈值才真正终止服务并封存报告。
 */
class SleepModeActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SleepModeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupNightWindow()
        super.onCreate(savedInstanceState)

        hideSystemUI()

        setContent {
            SleepModeContent(
                onExitBedsideScreen = {
                    // 仅退出当前黑屏时钟界面，后台睡眠感知继续运行！
                    finish()
                },
                onStopSleepTracking = {
                    // 晨起结算：滑块确认退出睡眠追踪
                    SleepTrackingService.stopTracking(this@SleepModeActivity)
                    finish()
                }
            )
        }
    }

    private fun setupNightWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 极暗屏幕背光 (0.015f)，夜间床头零刺眼
        val lp = window.attributes
        lp.screenBrightness = 0.015f
        window.attributes = lp
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
}

@Composable
private fun SleepModeContent(
    onExitBedsideScreen: () -> Unit,
    onStopSleepTracking: () -> Unit
) {
    val liveStatusDetail by SleepTrackingService.liveDetailText.collectAsState()
    val isPhoneFlat by SleepTrackingService.isPhoneFlat.collectAsState()
    val phoneTiltAngle by SleepTrackingService.phoneTiltAngle.collectAsState()

    var currentTimeStr by remember { mutableStateOf(getFormattedTime()) }
    var currentDateStr by remember { mutableStateOf(getFormattedDate()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTimeStr = getFormattedTime()
            currentDateStr = getFormattedDate()
            delay(1000L)
        }
    }

    // 全屏纯黑背景，点击背景空白区域仅退出黑屏界面 (后台持续守护)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onExitBedsideScreen()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：微弱暗色状态或倾角姿态提示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                if (!isPhoneFlat) {
                    Text(
                        text = "⚠️ 手机倾斜 ${phoneTiltAngle.toInt()}° · 请平放于床面",
                        color = Color(0xFFFF5252).copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(IosOrange.copy(alpha = 0.6f), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "正在感知睡眠与后半夜梦境",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = liveStatusDetail,
                    color = Color.White.copy(alpha = 0.2f),
                    fontSize = 10.sp
                )
            }

            // 中部：黑屏 + 小数字时间显示
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTimeStr,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 34.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currentDateStr,
                    color = Color.White.copy(alpha = 0.22f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "轻触屏幕任意位置可返回主页 (后台持续守护)",
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 11.sp
                )
            }

            // 底部：防误触滑动滑块 (Slide to Stop Tracking)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 拦截滑块区域点击，防止误触发背景点击
                    }
            ) {
                SlideToStopTrackingSlider(
                    onStop = onStopSleepTracking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "可按电源键熄屏 · 整夜低功耗后台运行",
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 成熟高质感滑动结束睡眠滑块 (Slide to Stop)
 */
@Composable
private fun SlideToStopTrackingSlider(
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 48.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    val maxDragRange = (trackWidthPx - thumbSizePx - with(density) { 8.dp.toPx() }).coerceAtLeast(1f)
    val progress = (offsetX / maxDragRange).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141416))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 背景渐变填充光效 (随滑动展开)
        if (progress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF9B51E0).copy(alpha = 0.25f),
                                Color(0xFFFF9F0A).copy(alpha = 0.45f)
                            )
                        )
                    )
            )
        }

        // 中间提示文案 (随滑动渐隐)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▷ 向右滑动结束睡眠",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = (1f - progress * 1.4f).coerceIn(0.1f, 0.65f)),
                letterSpacing = 1.sp
            )
        }

        // 滑块圆形手柄 (可横向拖拽)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFF9F0A), Color(0xFFE65100))
                    )
                )
                .pointerInput(maxDragRange) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX >= maxDragRange * 0.72f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStop()
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxDragRange)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "☀",
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }
}

private fun getFormattedTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun getFormattedDate(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())
