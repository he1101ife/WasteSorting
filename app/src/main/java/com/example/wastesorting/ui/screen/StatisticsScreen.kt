package com.example.wastesorting.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.data.db.dao.CategoryStat
import com.example.wastesorting.util.catColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.ZoneId

@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    var stats by remember { mutableStateOf<List<CategoryStat>>(emptyList()) }
    var weeklyStats by remember { mutableStateOf<List<CategoryStat>>(emptyList()) }

    LaunchedEffect(Unit) {
        val weekStart = LocalDate.now()
            .with(DayOfWeek.MONDAY)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        stats = withContext(Dispatchers.IO) { db.captureRecordDao().getCategoryStats() }
        weeklyStats = withContext(Dispatchers.IO) { db.captureRecordDao().getWeeklyStats(weekStart) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("类别统计", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (stats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无识别记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("类别占比", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                PieChart(data = stats.map { it.categoryName to it.cnt }, modifier = Modifier.fillMaxWidth().height(220.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    stats.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(catColor(s.categoryName)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(s.categoryName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(16.dp))
                Text("本周识别", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
                if (weeklyStats.isEmpty() || weeklyStats.all { it.cnt == 0 }) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("本周暂无识别记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    BarChart(data = weeklyStats, modifier = Modifier.fillMaxWidth().height(200.dp))
                }
            }
        }
    }
}

@Composable
fun PieChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }.toFloat()
    if (total == 0f) return
    val onSurfaceColor = android.graphics.Color.parseColor("#1C1C1E")
    val onSurfaceVariantColor = android.graphics.Color.parseColor("#8E8E93")
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) * 0.78f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        var startAngle = -90f
        data.forEachIndexed { i, (categoryName, count) ->
            val sweep = (count / total) * 360f
            val color = catColor(categoryName)
            drawArc(color = color, startAngle = startAngle, sweepAngle = sweep, useCenter = true, topLeft = topLeft, size = Size(diameter, diameter))
            startAngle += sweep
        }
        val holeR = diameter * 0.38f; val cx = size.width / 2f; val cy = size.height / 2f
        drawCircle(Color.White, radius = holeR + 1f, center = Offset(cx, cy))
        drawContext.canvas.nativeCanvas.drawText("${total.toInt()}", cx, cy - 2f, android.graphics.Paint().apply { textSize = 42f * density; textAlign = android.graphics.Paint.Align.CENTER; this.color = onSurfaceColor; isFakeBoldText = true })
        drawContext.canvas.nativeCanvas.drawText("总计", cx, cy + 24f * density, android.graphics.Paint().apply { textSize = 14f * density; textAlign = android.graphics.Paint.Align.CENTER; this.color = onSurfaceVariantColor })
    }
}

@Composable
fun BarChart(data: List<CategoryStat>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.cnt }.toFloat().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val labelHeight = 36f * density; val valueGap = 18f * density
        val chartArea = size.height - labelHeight
        val maxBarW = 64f * density
        val totalBarsW = data.size * maxBarW
        val barWidth = if (totalBarsW < size.width - 16f * density) maxBarW else (size.width - 16f * density) / data.size
        val gap = barWidth * 0.2f
        val offsetX = (size.width - data.size * barWidth) / 2f
        data.forEachIndexed { i, stat ->
            val barH = (stat.cnt / maxVal) * (chartArea - valueGap)
            val x = offsetX + i * barWidth; val bottomY = chartArea
            drawRoundRect(color = catColor(stat.categoryName), topLeft = Offset(x, bottomY - barH), size = Size(barWidth - gap, barH), cornerRadius = CornerRadius(6f * density, 6f * density))
            drawContext.canvas.nativeCanvas.drawText("${stat.cnt}", x + (barWidth - gap) / 2f, bottomY - barH - 6f * density, android.graphics.Paint().apply { textSize = 12f * density; textAlign = android.graphics.Paint.Align.CENTER; this.color = android.graphics.Color.parseColor("#8E8E93"); isFakeBoldText = true })
            drawContext.canvas.nativeCanvas.drawText(stat.categoryName.take(2), x + (barWidth - gap) / 2f, chartArea + 18f * density, android.graphics.Paint().apply { textSize = 12f * density; textAlign = android.graphics.Paint.Align.CENTER; this.color = android.graphics.Color.parseColor("#1C1C1E") })
        }
    }
}
