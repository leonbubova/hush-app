package com.hush.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_regular, weight = FontWeight.Normal),
    Font(R.font.playfair_display_bold, weight = FontWeight.Bold),
)

private val CardShape = RoundedCornerShape(20.dp)
private val CardBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
private val CardContainerColor = Color.White.copy(alpha = 0.06f)
private val AccentPink = Color(0xFFB85C8A)
private val AccentLightPink = Color(0xFFD4789C)
private val AccentPurple = Color(0xFF6C63FF)
private val AccentLavender = Color(0xFF9B6BCD)
private val LabelColor = Color.White.copy(alpha = 0.5f)

@Composable
fun UsageScreen(sessions: List<RecordingSession>) {
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Single centered gradient blob at top
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3A1A2E).copy(alpha = 0.8f),
                        Color(0xFF2A1025).copy(alpha = 0.4f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.08f),
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.5f, size.height * 0.08f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StreakCard(sessions, today, zone)
            TranscriptionsCard(sessions, today, zone)
            WordsMinutesRow(sessions)
            ThisWeekCard(sessions, today, zone)
            ActivityHeatmapCard(sessions, today, zone)
            CostCard(sessions, today, zone)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// --------------- Streak Card ---------------

@Composable
private fun StreakCard(sessions: List<RecordingSession>, today: LocalDate, zone: ZoneId) {
    val sessionDays = remember(sessions) {
        sessions.map { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }.toSet()
    }
    val streak = remember(sessionDays, today) {
        var count = 0
        var day = today
        while (sessionDays.contains(day)) {
            count++
            day = day.minusDays(1)
        }
        count
    }

    val weekStart = remember(today) { today.with(DayOfWeek.MONDAY) }

    UsageCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left side: label + number
            Column(modifier = Modifier.widthIn(max = 100.dp)) {
                Text(
                    "CURRENT\nSTREAK",
                    fontSize = 11.sp,
                    color = LabelColor,
                    letterSpacing = 1.5.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$streak",
                        fontSize = 40.sp,
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "days",
                        fontSize = 16.sp,
                        color = LabelColor,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Right side: checkmark grid with day labels below
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0..6) {
                    val day = weekStart.plusDays(i.toLong())
                    val active = sessionDays.contains(day)
                    val label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(30.dp)) {
                            val cornerRad = CornerRadius(7.dp.toPx())
                            if (active) {
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF6C4A7E),
                                            Color(0xFFB85C8A),
                                        ),
                                    ),
                                    cornerRadius = cornerRad,
                                )
                                val path = Path().apply {
                                    moveTo(size.width * 0.28f, size.height * 0.52f)
                                    lineTo(size.width * 0.44f, size.height * 0.68f)
                                    lineTo(size.width * 0.72f, size.height * 0.35f)
                                }
                                drawPath(path, Color.White, style = Stroke(width = 2.5f))
                            } else {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.06f),
                                    cornerRadius = cornerRad,
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(label, fontSize = 9.sp, color = LabelColor)
                    }
                }
            }
        }
    }
}

// --------------- Transcriptions Card ---------------

@Composable
private fun TranscriptionsCard(sessions: List<RecordingSession>, today: LocalDate, zone: ZoneId) {
    val sixMonthsAgo = remember(today) { today.minusMonths(6) }
    val twelveMonthsAgo = remember(today) { today.minusMonths(12) }

    val recentCount = remember(sessions, sixMonthsAgo) {
        sessions.count {
            Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() >= sixMonthsAgo
        }
    }
    val priorCount = remember(sessions, sixMonthsAgo, twelveMonthsAgo) {
        sessions.count {
            val d = Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate()
            d >= twelveMonthsAgo && d < sixMonthsAgo
        }
    }
    val pctChange = remember(recentCount, priorCount) {
        if (priorCount == 0) {
            if (recentCount > 0) 100.0 else 0.0
        } else ((recentCount - priorCount) * 100.0) / priorCount
    }

    val monthlyCounts = remember(sessions, today, zone) {
        val counts = mutableListOf<Int>()
        for (i in 5 downTo 0) {
            val monthStart = today.minusMonths(i.toLong()).withDayOfMonth(1)
            val monthEnd = monthStart.plusMonths(1)
            counts.add(sessions.count {
                val d = Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate()
                d >= monthStart && d < monthEnd
            })
        }
        counts
    }

    val monthLabels = remember(today) {
        (5 downTo 0).map { i ->
            today.minusMonths(i.toLong()).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    UsageCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Transcriptions", fontSize = 11.sp, color = LabelColor, letterSpacing = 1.sp)
            Text("Last 6 months", fontSize = 10.sp, color = LabelColor)
        }
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$recentCount",
                fontSize = 36.sp,
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            val sign = if (pctChange >= 0) "+" else ""
            Text(
                "$sign${String.format("%.1f", pctChange)}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentPink,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        val maxVal = (monthlyCounts.maxOrNull() ?: 1).coerceAtLeast(1)
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val w = size.width
            val h = size.height
            val padBottom = 20f
            val graphH = h - padBottom
            val stepX = w / (monthlyCounts.size - 1).coerceAtLeast(1)

            val points = monthlyCounts.mapIndexed { i, v ->
                Offset(i * stepX, graphH - (v.toFloat() / maxVal) * graphH * 0.85f)
            }

            if (points.size >= 2) {
                val curvePath = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        val cp1x = (points[i - 1].x + points[i].x) / 2
                        cubicTo(cp1x, points[i - 1].y, cp1x, points[i].y, points[i].x, points[i].y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(curvePath)
                    lineTo(points.last().x, graphH)
                    lineTo(points.first().x, graphH)
                    close()
                }
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AccentLavender.copy(alpha = 0.35f),
                            AccentLightPink.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                        startY = points.minOf { it.y },
                        endY = graphH,
                    ),
                )

                drawPath(curvePath, AccentLightPink, style = Stroke(width = 2.5f))

                val last = points.last()
                drawCircle(AccentLightPink, radius = 4.5f, center = last)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            monthLabels.forEach { label ->
                Text(label, fontSize = 9.sp, color = LabelColor, textAlign = TextAlign.Center)
            }
        }
    }
}

// --------------- Words / Minutes Row ---------------

@Composable
private fun WordsMinutesRow(sessions: List<RecordingSession>) {
    val totalWords = remember(sessions) { sessions.sumOf { it.wordCount } }
    val totalMinutes = remember(sessions) { sessions.sumOf { it.durationSeconds } / 60 }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallStatCard(
            modifier = Modifier.weight(1f),
            label = "WORDS",
            value = formatNumber(totalWords),
            sparkData = buildSparkData(sessions) { it.wordCount },
        )
        SmallStatCard(
            modifier = Modifier.weight(1f),
            label = "MINUTES",
            value = formatNumber(totalMinutes),
            sparkData = buildSparkData(sessions) { it.durationSeconds / 60 },
        )
    }
}

@Composable
private fun SmallStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sparkData: List<Float>,
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardContainerColor),
        border = CardBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 11.sp, color = LabelColor, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    fontSize = 28.sp,
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Canvas(modifier = Modifier.weight(1f).height(24.dp)) {
                    if (sparkData.size < 2) return@Canvas
                    val maxV = sparkData.max().coerceAtLeast(1f)
                    val stepX = size.width / (sparkData.size - 1)
                    val pts = sparkData.mapIndexed { i, v ->
                        Offset(i * stepX, size.height - (v / maxV) * size.height * 0.8f)
                    }
                    for (i in 1 until pts.size) {
                        drawLine(AccentLavender.copy(alpha = 0.5f), pts[i - 1], pts[i], strokeWidth = 1.5f)
                    }
                }
            }
        }
    }
}

private fun buildSparkData(sessions: List<RecordingSession>, extractor: (RecordingSession) -> Int): List<Float> {
    if (sessions.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    return (6 downTo 0).map { daysAgo ->
        val day = today.minusDays(daysAgo.toLong())
        sessions.filter {
            Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() == day
        }.sumOf { extractor(it) }.toFloat()
    }
}

// --------------- This Week Bar Chart ---------------

@Composable
private fun ThisWeekCard(sessions: List<RecordingSession>, today: LocalDate, zone: ZoneId) {
    val weekStart = remember(today) { today.with(DayOfWeek.MONDAY) }
    val dailyCounts = remember(sessions, weekStart, zone) {
        (0..6).map { i ->
            val day = weekStart.plusDays(i.toLong())
            sessions.count {
                Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() == day
            }
        }
    }
    val totalThisWeek = dailyCounts.sum()
    val maxCount = dailyCounts.max().coerceAtLeast(1)
    val maxIdx = dailyCounts.indexOf(dailyCounts.max())

    val dayLabels = remember {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }

    UsageCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("This week", fontSize = 11.sp, color = LabelColor, letterSpacing = 1.sp)
            Text(
                "$totalThisWeek",
                fontSize = 28.sp,
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(16.dp))

        // Bar chart with count label on tallest bar
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val barCount = 7
            val spacing = size.width * 0.035f
            val barW = (size.width - spacing * (barCount + 1)) / barCount
            val cornerR = 8.dp.toPx()
            val labelSpace = 20f // space for label above tallest bar

            dailyCounts.forEachIndexed { i, count ->
                val barH = if (maxCount > 0) (count.toFloat() / maxCount) * (size.height - labelSpace) * 0.85f else 0f
                val minH = if (count > 0) 8f else 4f
                val h = barH.coerceAtLeast(minH)
                val x = spacing + i * (barW + spacing)
                val y = size.height - h

                val isMax = i == maxIdx && count > 0
                val barColor = if (isMax) {
                    AccentPink
                } else {
                    Color.White.copy(alpha = 0.08f + (count.toFloat() / maxCount) * 0.08f)
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            dayLabels.forEach { label ->
                Text(label, fontSize = 9.sp, color = LabelColor, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

// --------------- Activity Heatmap ---------------

@Composable
private fun ActivityHeatmapCard(sessions: List<RecordingSession>, today: LocalDate, zone: ZoneId) {
    val weeksBack = 10
    val dayCounts = remember(sessions, today, zone) {
        val map = mutableMapOf<LocalDate, Int>()
        sessions.forEach { s ->
            val d = Instant.ofEpochMilli(s.startEpochMs).atZone(zone).toLocalDate()
            map[d] = (map[d] ?: 0) + 1
        }
        map
    }

    val gridStart = remember(today) {
        today.with(DayOfWeek.MONDAY).minusWeeks((weeksBack - 1).toLong())
    }

    val heatColors = remember {
        listOf(
            Color(0xFF1A1525),           // 0 sessions — very dark
            Color(0xFF352655),           // 1 — muted purple
            Color(0xFF5A3D8A),           // 2 — medium purple
            Color(0xFF7B52B5),           // 3-4 — brighter purple
            Color(0xFFB85C8A),           // 5+ — pink accent
        )
    }

    val dayLabels = remember { listOf("M", "", "W", "", "F", "", "") }

    UsageCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Activity", fontSize = 11.sp, color = LabelColor, letterSpacing = 1.sp)
            Text("$weeksBack weeks", fontSize = 10.sp, color = LabelColor)
        }
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(16.dp).height(140.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                dayLabels.forEach { label ->
                    Text(
                        label,
                        fontSize = 9.sp,
                        color = LabelColor,
                        modifier = Modifier.height(16.dp),
                    )
                }
            }

            Canvas(modifier = Modifier.weight(1f).height(140.dp)) {
                val cols = weeksBack
                val rows = 7
                val gap = 4.dp.toPx()
                val cellW = (size.width - gap * (cols - 1)) / cols
                val cellH = (size.height - gap * (rows - 1)) / rows
                val cellSize = minOf(cellW, cellH)
                val cornerR = CornerRadius(3.dp.toPx())

                for (col in 0 until cols) {
                    for (row in 0 until rows) {
                        val day = gridStart.plusWeeks(col.toLong()).plusDays(row.toLong())
                        val count = dayCounts[day] ?: 0
                        val colorIdx = when {
                            count == 0 -> 0
                            count == 1 -> 1
                            count == 2 -> 2
                            count <= 4 -> 3
                            else -> 4
                        }
                        val x = col * (cellSize + gap)
                        val y = row * (cellSize + gap)
                        drawRoundRect(
                            color = heatColors[colorIdx],
                            topLeft = Offset(x, y),
                            size = Size(cellSize, cellSize),
                            cornerRadius = cornerR,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text("Less", fontSize = 9.sp, color = LabelColor)
            Spacer(Modifier.width(4.dp))
            heatColors.forEach { c ->
                Spacer(Modifier.width(2.dp))
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawRoundRect(c, cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
            Spacer(Modifier.width(4.dp))
            Text("More", fontSize = 9.sp, color = LabelColor)
        }
    }
}

// --------------- Cost Card ---------------

@Composable
private fun CostCard(sessions: List<RecordingSession>, today: LocalDate, zone: ZoneId) {
    val costPerMinute = 0.003
    val totalMinutes = remember(sessions) { sessions.sumOf { it.durationSeconds } / 60.0 }
    val totalCost = totalMinutes * costPerMinute

    val thisMonthMinutes = remember(sessions, today, zone) {
        val monthStart = today.withDayOfMonth(1)
        sessions.filter {
            Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() >= monthStart
        }.sumOf { it.durationSeconds } / 60.0
    }
    val monthCost = thisMonthMinutes * costPerMinute

    UsageCard {
        Text("ESTIMATED COST", fontSize = 11.sp, color = LabelColor, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Based on Voxtral Mini at \$0.003/min",
            fontSize = 10.sp,
            color = LabelColor,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("All time", fontSize = 10.sp, color = LabelColor)
                Text(
                    formatCost(totalCost),
                    fontSize = 28.sp,
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("This month", fontSize = 10.sp, color = LabelColor)
                Text(
                    formatCost(monthCost),
                    fontSize = 28.sp,
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

// --------------- Shared components ---------------

@Composable
private fun UsageCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardContainerColor),
        border = CardBorder,
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 10_000 -> String.format("%.1fK", n / 1_000.0)
    n >= 1_000 -> String.format("%,d", n)
    else -> n.toString()
}

private fun formatCost(cost: Double): String = when {
    cost < 0.01 -> "$${String.format("%.4f", cost)}"
    cost < 1.0 -> "$${String.format("%.3f", cost)}"
    else -> "$${String.format("%.2f", cost)}"
}
