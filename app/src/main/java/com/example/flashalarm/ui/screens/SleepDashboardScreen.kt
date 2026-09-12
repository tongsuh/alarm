package com.example.flashalarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashalarm.sleep.model.SleepSession
import com.example.flashalarm.sleep.model.SleepStage
import com.example.flashalarm.ui.components.SleepHypnogramChart
import com.example.flashalarm.ui.theme.IosOrange
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SleepDashboardScreen(
    sessions: List<SleepSession>,
    currentSession: SleepSession?,
    selectedDateString: String,
    onSelectDate: (String) -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDateFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINESE) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "睡眠分析",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "多模态感知与清醒梦结构表",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }

                // 触梦配置设置按钮
                IconButton(
                    onClick = onOpenConfig,
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "触梦参数配置",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // 1. 顶部日期历史横向胶囊切换器 (过去 7 天)
            item {
                WeekDateSelector(
                    selectedDateString = selectedDateString,
                    sessions = sessions,
                    onSelectDate = onSelectDate
                )
            }

            if (currentSession == null) {
                item {
                    EmptySleepStateCard()
                }
            } else {
                // 2. 核心大字战报卡片
                item {
                    SleepOverviewCard(session = currentSession)
                }

                // 3. 核心催眠图 (Hypnogram)
                item {
                    SleepHypnogramChart(
                        session = currentSession,
                        epochs = currentSession.epochs
                    )
                }

                // 4. 生理与梦境四宫格卡片
                item {
                    SleepMetricsGrid(session = currentSession)
                }
            }

            // 5. 历史夜晚记录归档流
            if (sessions.isNotEmpty()) {
                item {
                    Text(
                        text = "历史夜晚归档",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(sessions, key = { it.id }) { s ->
                    HistoricalNightCard(
                        session = s,
                        isSelected = s.dateString == selectedDateString,
                        onClick = { onSelectDate(s.dateString) }
                    )
                }
            }
        }
    }
}

/**
 * 横向单周日期选择器
 */
@Composable
fun WeekDateSelector(
    selectedDateString: String,
    sessions: List<SleepSession>,
    onSelectDate: (String) -> Unit
) {
    val dates = remember {
        val list = mutableListOf<Date>()
        val cal = Calendar.getInstance()
        for (i in 0 until 7) {
            list.add(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        list.reversed()
    }

    val sdfKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val sdfDay = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val sdfWeek = remember { SimpleDateFormat("E", Locale.CHINESE) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141416))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dates.forEach { date ->
            val key = sdfKey.format(date)
            val isSelected = key == selectedDateString
            val hasRecord = sessions.any { it.dateString == key }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectDate(key) }
                    .background(if (isSelected) IosOrange else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = sdfWeek.format(date),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = sdfDay.format(date),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.Black else Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                // 标记小圆点
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> Color.Black
                                hasRecord -> IosOrange
                                else -> Color.Transparent
                            }
                        )
                )
            }
        }
    }
}

/**
 * 睡眠总览卡片 (时长、入睡耗时、比例条、分期时长)
 */
@Composable
fun SleepOverviewCard(session: SleepSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151518)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "实际睡眠净时长",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.formattedDuration,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "就寝时间：${session.formattedTimeRange} · 在床 ${session.formattedTotalInBed}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }

                // 睡眠评分圆环徽章
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E24))
                        .border(2.dp, IosOrange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${session.sleepScore}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "分",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 多段比例胶囊堆叠条
            MultiStageProgressBar(session = session)

            Spacer(modifier = Modifier.height(14.dp))

            // 四阶段标签拆解
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StageLegendItem(
                    label = "深睡",
                    durationText = "${session.deepSleepMin}m",
                    percentText = "${session.deepPercent}%",
                    color = SleepStage.DEEP.composeColor
                )
                StageLegendItem(
                    label = "浅睡",
                    durationText = "${session.lightSleepMin}m",
                    percentText = "${session.lightPercent}%",
                    color = SleepStage.LIGHT.composeColor
                )
                StageLegendItem(
                    label = "REM",
                    durationText = "${session.remSleepMin}m",
                    percentText = "${session.remPercent}%",
                    color = SleepStage.REM.composeColor
                )
                StageLegendItem(
                    label = "清醒",
                    durationText = "${session.awakeMin}m",
                    percentText = "${session.awakePercent}%",
                    color = SleepStage.AWAKE.composeColor
                )
            }
        }
    }
}

@Composable
fun MultiStageProgressBar(session: SleepSession) {
    val total = session.totalDurationMin.coerceAtLeast(1).toFloat()
    val deepWeight = (session.deepSleepMin / total).coerceAtLeast(0.01f)
    val lightWeight = (session.lightSleepMin / total).coerceAtLeast(0.01f)
    val remWeight = (session.remSleepMin / total).coerceAtLeast(0.01f)
    val awakeWeight = (session.awakeMin / total).coerceAtLeast(0.01f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        Box(modifier = Modifier.weight(deepWeight).fillMaxHeight().background(SleepStage.DEEP.composeColor))
        Box(modifier = Modifier.weight(lightWeight).fillMaxHeight().background(SleepStage.LIGHT.composeColor))
        Box(modifier = Modifier.weight(remWeight).fillMaxHeight().background(SleepStage.REM.composeColor))
        Box(modifier = Modifier.weight(awakeWeight).fillMaxHeight().background(SleepStage.AWAKE.composeColor))
    }
}

@Composable
fun StageLegendItem(label: String, durationText: String, percentText: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            Text(text = "$durationText ($percentText)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/**
 * 生理与梦境四宫格指标卡
 */
@Composable
fun SleepMetricsGrid(session: SleepSession) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricGridCard(
                title = "黄金触梦命中",
                value = "${session.cueTriggerCount} 次",
                subtitle = if (session.cueTriggerCount > 0) "后半夜成功击发微光轻语" else "未到达触发阈值",
                icon = Icons.Default.AutoAwesome,
                iconTint = Color(0xFFFF9F0A),
                modifier = Modifier.weight(1f)
            )

            MetricGridCard(
                title = "入睡潜伏耗时",
                value = "${session.sleepOnsetLatencyMin} 分钟",
                subtitle = if (session.sleepOnsetLatencyMin <= 20) "入眠极速平顺" else "入睡耗时略长",
                icon = Icons.Default.Bedtime,
                iconTint = Color(0xFF64B5F6),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricGridCard(
                title = "平均呼吸速率",
                value = "${session.avgBreathBpm.toInt()} bpm",
                subtitle = "整夜呼吸均匀平稳",
                icon = Icons.Default.Air,
                iconTint = Color(0xFF81C784),
                modifier = Modifier.weight(1f)
            )

            MetricGridCard(
                title = "夜间翻身微动",
                value = "${session.turnoverCount} 次",
                subtitle = "床垫微动感知记录",
                icon = Icons.Default.ScreenRotation,
                iconTint = Color(0xFFBA68C8),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricGridCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

/**
 * 历史夜晚记录单卡
 */
@Composable
fun HistoricalNightCard(
    session: SleepSession,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E1E24) else Color(0xFF141416)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) IosOrange.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.dateString,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${session.formattedTimeRange} · 净睡眠 ${session.formattedDuration}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.cueTriggerCount > 0) {
                    Text(
                        text = "✨ ${session.cueTriggerCount}次触梦",
                        fontSize = 11.sp,
                        color = Color(0xFFFF9F0A),
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "${session.sleepScore}分",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosOrange
                )
            }
        }
    }
}

@Composable
fun EmptySleepStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.NightsStay,
                contentDescription = null,
                tint = IosOrange.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "今日暂无睡眠记录",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "今晚睡前在【闹钟】首页点击「开始今夜睡眠」，系统将自动进行整夜多模态感知并在早晨生成详细催眠图谱。",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
