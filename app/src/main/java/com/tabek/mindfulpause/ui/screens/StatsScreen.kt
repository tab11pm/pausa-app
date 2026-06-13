package com.tabek.mindfulpause.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.data.AppBreakdown
import com.tabek.mindfulpause.data.DayBucket
import com.tabek.mindfulpause.data.Period
import com.tabek.mindfulpause.data.Stats
import com.tabek.mindfulpause.ui.MainViewModel
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.AccentDeep
import com.tabek.mindfulpause.ui.theme.Divider
import com.tabek.mindfulpause.ui.theme.SurfaceElevated
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary
import java.util.Calendar

@Composable
fun StatsScreen(vm: MainViewModel) {
    val stats by vm.stats.collectAsState()
    val period by vm.period.collectAsState()
    val apps by vm.installedApps.collectAsState()

    // packageName -> display label, for the per-app breakdown.
    val labelOf: (String) -> String = { pkg ->
        apps.firstOrNull { it.packageName == pkg }?.label ?: pkg.substringAfterLast('.')
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Статистика", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Твоя осознанность в цифрах", color = TextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))

        PeriodSelector(selected = period, onSelect = vm::setPeriod)
        Spacer(Modifier.height(20.dp))

        val s = stats
        if (s == null || s.total == 0) {
            EmptyStats()
            return@Column
        }

        // Headline cards: streak + reflect percent
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BigStat(Modifier.weight(1f), "${s.streakDays}", "дней подряд", highlight = true)
            BigStat(Modifier.weight(1f), "${s.reflectPercent}%", "передумал", highlight = false)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BigStat(Modifier.weight(1f), "${s.reflected}", "вышел", highlight = false)
            BigStat(Modifier.weight(1f), "${s.continued}", "продолжил", highlight = false)
        }
        Spacer(Modifier.height(24.dp))

        SectionTitle("ПО ДНЯМ")
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                DayChart(s.perDay)
                Spacer(Modifier.height(10.dp))
                LegendRow()
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionTitle("ПО ПРИЛОЖЕНИЯМ")
        if (s.perApp.isEmpty()) {
            GlassCard {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("Нет данных за период", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            GlassCard {
                Column {
                    s.perApp.forEachIndexed { i, app ->
                        AppStatRow(app, labelOf(app.packageName), maxTotal = s.perApp.first().total)
                        if (i < s.perApp.lastIndex) {
                            HorizontalDivider(color = Divider, thickness = 1.dp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    val items = listOf(Period.TODAY to "Сегодня", Period.WEEK to "Неделя", Period.ALL to "Всё время")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (p, label) ->
            val active = p == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (active) Modifier.background(AccentDeep) else Modifier.background(SurfaceElevated)
                        )
                        .clickable { onSelect(p) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (active) TextPrimary else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigStat(modifier: Modifier, value: String, label: String, highlight: Boolean) {
    GlassCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = if (highlight) Accent else TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextMuted, fontSize = 13.sp)
        }
    }
}

/** Stacked bar chart: reflected (green) over continued (muted), one bar per day. */
@Composable
private fun DayChart(days: List<DayBucket>) {
    val maxTotal = (days.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val n = days.size
        if (n == 0) return@Canvas
        val gap = size.width * 0.02f
        val barW = (size.width - gap * (n - 1)) / n
        days.forEachIndexed { i, d ->
            val x = i * (barW + gap)
            val totalH = size.height * (d.total.toFloat() / maxTotal)
            val reflH = if (d.total == 0) 0f else totalH * (d.reflected.toFloat() / d.total)
            val contH = totalH - reflH
            // continued (bottom, muted)
            if (contH > 0) {
                drawRoundedBar(x, size.height - contH, barW, contH, Color(0xFF394A40))
            }
            // reflected (top, green)
            if (reflH > 0) {
                drawRoundedBar(x, size.height - totalH, barW, reflH, Accent)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedBar(
    x: Float, y: Float, w: Float, h: Float, color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.25f, w * 0.25f),
    )
}

@Composable
private fun LegendRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(Accent, "Передумал")
        LegendDot(Color(0xFF394A40), "Продолжил")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AppStatRow(app: AppBreakdown, label: String, maxTotal: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${app.reflected}/${app.total}", color = TextMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        // Progress bar: how much of this app's pauses ended in reflection.
        val frac = if (app.total == 0) 0f else app.reflected.toFloat() / app.total
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceElevated),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Accent),
            )
        }
    }
}

@Composable
private fun EmptyStats() {
    GlassCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Пока пусто", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Как только сработает первая пауза — здесь появится статистика.",
                color = TextMuted,
                fontSize = 14.sp,
            )
        }
    }
}
