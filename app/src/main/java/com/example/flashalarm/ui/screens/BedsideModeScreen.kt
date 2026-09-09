package com.example.flashalarm.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.model.AlarmItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BedsideModeScreen(
    alarms: List<AlarmItem>,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 默认开启自动息屏
    val sharedPrefs = remember { context.getSharedPreferences("flashalarm_prefs", Context.MODE_PRIVATE) }
    var isAutoDimEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("bedside_auto_dim", true))
    }

    var isDimmed by remember { mutableStateOf(false) }
    var showHud by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(Date()) }

    // 保持屏幕常亮
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = originalBrightness
                it.attributes = lp
            }
        }
    }

    // 每秒刷新时间
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000L)
        }
    }

    // 闲置 5 秒自动沉入超微光息屏
    LaunchedEffect(isAutoDimEnabled, showHud) {
        if (isAutoDimEnabled) {
            delay(4000L)
            showHud = false
            delay(1000L)
            isDimmed = true
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = 0.01f // 压至 1% 极微光防眩目与防烧屏
                window.attributes = lp
            }
        }
    }

    fun wakeUpScreen() {
        isDimmed = false
        showHud = true
        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    val timeStr = remember(currentTime) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
    }
    val dateStr = remember(currentTime) {
        SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(currentTime)
    }

    val nextAlarm = alarms.firstOrNull { it.isEnabled }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.08f else 0.85f,
        animationSpec = tween(800),
        label = "alpha"
    )

    val hudAlpha by animateFloatAsState(
        targetValue = if (showHud) 1f else 0f,
        animationSpec = tween(300),
        label = "hudAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                wakeUpScreen()
            }
    ) {
        // 顶部悬浮控制 HUD (带有清晰的绿色原生开关)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .alpha(hudAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "退出", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // 清晰直观的自动息屏开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "自动息屏",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isAutoDimEnabled,
                    onCheckedChange = { checked ->
                        isAutoDimEnabled = checked
                        sharedPrefs.edit().putBoolean("bedside_auto_dim", checked).apply()
                        wakeUpScreen()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759), // iOS 经典绿
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // 中心床头时钟 (夜间微光下调至 34sp，不起眼、极为柔和不刺眼)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeStr,
                color = Color.White,
                fontSize = if (isDimmed) 34.sp else 48.sp,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = dateStr,
                color = Color.LightGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.alpha(if (isDimmed) 0.6f else 0.8f)
            )

            if (nextAlarm != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = if (isDimmed) 0.04f else 0.1f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%02d:%02d", nextAlarm.hour, nextAlarm.minute),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (nextAlarm.label.isNotBlank()) {
                        Text(
                            text = " • ${nextAlarm.label}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
