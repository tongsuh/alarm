package com.example.flashalarm.ui.components

import android.view.SoundEffectConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.ui.theme.IosCardSurfaceVariant
import com.example.flashalarm.ui.theme.IosOrange
import com.example.flashalarm.ui.theme.IosTextPrimary
import com.example.flashalarm.ui.theme.IosTextSecondary

/**
 * 仿 iOS 原生鼓轮时间选择器 (Wheel Time Picker)
 * 支持惯性滑动、居中吸附、立体透视、以及 iPhone 原生调时间机械齿轮音效与触觉反馈
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosWheelTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val visibleItemsCount = 5

    val hours = (0..23).toList()
    val minutes = (0..59).toList()

    val hourListState = rememberLazyListState(initialFirstVisibleItemIndex = initialHour)
    val minuteListState = rememberLazyListState(initialFirstVisibleItemIndex = initialMinute)

    val hourFlingBehavior = rememberSnapFlingBehavior(lazyListState = hourListState)
    val minuteFlingBehavior = rememberSnapFlingBehavior(lazyListState = minuteListState)

    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    // 动态监听中心选中项
    val selectedHour by remember {
        derivedStateOf {
            val index = hourListState.firstVisibleItemIndex
            val offset = hourListState.firstVisibleItemScrollOffset
            if (offset > 50) (index + 1).coerceIn(0, 23) else index.coerceIn(0, 23)
        }
    }

    val selectedMinute by remember {
        derivedStateOf {
            val index = minuteListState.firstVisibleItemIndex
            val offset = minuteListState.firstVisibleItemScrollOffset
            if (offset > 50) (index + 1).coerceIn(0, 59) else index.coerceIn(0, 59)
        }
    }

    // 跟踪是否是初次加载，避免刚打开页面就发声
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(selectedHour, selectedMinute) {
        if (isInitialized) {
            // 播放 iPhone 机械齿轮音效与震动
            try {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            isInitialized = true
        }
        onTimeChanged(selectedHour, selectedMinute)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleItemsCount),
        contentAlignment = Alignment.Center
    ) {
        // iOS 风格中央高亮半透明卡片选中条
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(itemHeight)
                .background(IosCardSurfaceVariant, RoundedCornerShape(10.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 小时滚轮
            WheelColumn(
                items = hours,
                state = hourListState,
                flingBehavior = hourFlingBehavior,
                selectedItem = selectedHour,
                itemHeight = itemHeight,
                unitLabel = "时",
                modifier = Modifier.weight(1f)
            )

            Text(
                text = ":",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = IosOrange,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // 分钟滚轮
            WheelColumn(
                items = minutes,
                state = minuteListState,
                flingBehavior = minuteFlingBehavior,
                selectedItem = selectedMinute,
                itemHeight = itemHeight,
                unitLabel = "分",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    items: List<Int>,
    state: androidx.compose.foundation.lazy.LazyListState,
    flingBehavior: androidx.compose.foundation.gestures.FlingBehavior,
    selectedItem: Int,
    itemHeight: androidx.compose.ui.unit.Dp,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight * 2), // 居中占位
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(count = items.size) { index ->
            val value = items[index]
            val isSelected = value == selectedItem
            val formatted = String.format("%02d", value)

            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatted,
                        fontSize = if (isSelected) 30.sp else 22.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) IosTextPrimary else IosTextSecondary,
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.45f)
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unitLabel,
                            fontSize = 14.sp,
                            color = IosOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
