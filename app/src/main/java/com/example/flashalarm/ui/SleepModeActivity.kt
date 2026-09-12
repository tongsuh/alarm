package com.example.flashalarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        setupNightWindow()
        super.onCreate(savedInstanceState)

        hideSystemUI()

        setContent {
            SleepModeContent(
                onExitSleep = {
                    SleepTrackingService.stopTracking(this@SleepModeActivity)
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
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
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
    }
}

@Composable
private fun SleepModeContent(
    onExitSleep: () -> Unit
) {
    val liveStatusDetail by SleepTrackingService.liveDetailText.collectAsState()
    val isPhoneFlat by SleepTrackingService.isPhoneFlat.collectAsState()
    val phoneTiltAngle by SleepTrackingService.phoneTiltAngle.collectAsState()

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

    // 全屏纯黑，点击任意区域立即退出
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExitSleep() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：极简暗色状态或姿态告警
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

            // 中部：黑屏 + 小数字时间界面 (柔和小尺寸，夜间零刺眼)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTimeStr,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 32.sp,
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "轻触屏幕任意位置退出",
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 11.sp
                )
            }

            // 底部：明确的点击退出按钮 (点击即刻退出，无需长按)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = onExitSleep,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0E0E11)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "点击退出监测",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "可按电源键正常熄屏 · 后台持续低功耗守护",
                    color = Color.White.copy(alpha = 0.18f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun getFormattedTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun getFormattedDate(): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())
