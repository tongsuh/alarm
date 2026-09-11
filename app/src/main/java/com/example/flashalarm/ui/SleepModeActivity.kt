package com.example.flashalarm.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.flashalarm.sleep.SleepTrackingService
import com.example.flashalarm.ui.theme.IosOrange
import com.example.flashalarm.ui.theme.IosRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*

/**
 * 专为夜间就寝打造的纯黑极暗睡眠态 Activity (SleepModeActivity)
 *
 * 彻底参考成熟睡眠软件 (Sleep as Android / Sleep Cycle) 的设计规范：
 * 1. OLED 纯黑真夜间模式：全屏 0xFF000000 黑色背景，硬件极低背光 (0.01f)，完全不刺眼、不抑制褪黑素。
 * 2. 弱光大时钟屏保：柔和微光时钟 + 实时微动守护状态。
 * 3. 锁屏全兼容：支持按物理电源键直接熄屏（后台服务无缝持续监测）；也支持夜间亮屏时在锁屏上极暗常驻看时间。
 * 4. 防翻身误触：采用【长按 2 秒结束】保护机制，杜绝夜间手臂压到屏幕误关监测。
 * 5. 实时平放姿态预警：检测到手机斜靠或竖立时，柔和提示请平放于床垫。
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
        super.onCreate(savedInstanceState)

        setupNightWindow()
        hideSystemUI()

        setContent {
            SleepModeContent(
                onExitSleep = {
                    SleepTrackingService.stopTracking(this@SleepModeActivity)
                    finish()
                },
                onMinimize = {
                    finish()
                }
            )
        }
    }

    private fun setupNightWindow() {
        // 允许在锁屏上显示，熄屏点亮不刺眼
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // 常驻屏幕（用户也可随时按物理电源键熄屏）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 极暗屏幕背光重载 (0.015f)，OLED 零光污染
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
        // 若服务已停止，自动退出
        if (!SleepTrackingService.isServiceRunning.value) {
            finish()
        }
    }
}

@Composable
private fun SleepModeContent(
    onExitSleep: () -> Unit,
    onMinimize: () -> Unit
) {
    val liveStatusTitle by SleepTrackingService.liveStatusText.collectAsState()
    val liveStatusDetail by SleepTrackingService.liveDetailText.collectAsState()
    val isPhoneFlat by SleepTrackingService.isPhoneFlat.collectAsState()
    val phoneTiltAngle by SleepTrackingService.phoneTiltAngle.collectAsState()
    val isWhiteNoiseActive by SleepTrackingService.isWhiteNoiseActive.collectAsState()

    // 每秒刷新时间
    var currentTimeStr by remember { mutableStateOf(getFormattedTime()) }
    var currentDateStr by remember { mutableStateOf(getFormattedDate()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTimeStr = getFormattedTime()
            currentDateStr = getFormattedDate()
            delay(1000L)
        }
    }

    // 呼吸微光动画
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：极简暗色状态标识
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (isPhoneFlat) IosOrange.copy(alpha = breatheAlpha) else Color(0xFFFF453A),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPhoneFlat) "💤 正在感知入眠与后半夜梦境" else "⚠️ 手机倾斜 ${phoneTiltAngle.toInt()}° · 请平放于床面",
                        color = if (isPhoneFlat) Color.White.copy(alpha = 0.5f) else Color(0xFFFF5252).copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = liveStatusDetail,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp
                )
            }

            // 中部：柔和极暗大时钟 (专为暗夜保护视力设计)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTimeStr,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 72.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp
                )

                Text(
                    text = currentDateStr,
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 暗夜状态胶囊
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SleepBadge(
                        text = if (isPhoneFlat) "床垫平放" else "倾斜需平放",
                        isGood = isPhoneFlat
                    )
                    SleepBadge(
                        text = if (isWhiteNoiseActive) "白噪音避让" else "麦克风抗噪",
                        isGood = true
                    )
                }
            }

            // 底部：防误触【长按 2 秒退出】胶囊按钮
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "可按电源键正常熄屏 · 后台持续低功耗守护",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HoldToExitSleepButton(onHoldComplete = onExitSleep)

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onMinimize,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "查看闹钟列表 (后台继续监测)",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * 仿成熟睡眠 App 的【长按 2 秒防误触退出按钮】
 */
@Composable
private fun HoldToExitSleepButton(onHoldComplete: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val startTime = System.currentTimeMillis()
            val holdDurationMs = 1800f
            while (isPressed && progress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed / holdDurationMs).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    onHoldComplete()
                    break
                }
                delay(16L)
            }
        } else {
            progress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF141418))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 进度填充背景
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(IosRed.copy(alpha = 0.45f))
        )

        // 按钮表面边框与文字
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPressed) "按住不放以结束..." else "长按结束监测",
                color = if (isPressed) Color.White else Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SleepBadge(text: String, isGood: Boolean) {
    Box(
        modifier = Modifier
            .background(Color(0xFF0F0F12), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(if (isGood) Color(0xFF4CAF50) else Color(0xFFFF5252), CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
    }
}

private fun getFormattedTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun getFormattedDate(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())
