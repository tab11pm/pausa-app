package com.tabek.mindfulpause.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Background = Color(0xFF0B0F0C)
private val Accent = Color(0xFF22C55E)
private val TextPrimary = Color(0xFFF1F5F2)
private val TextMuted = Color(0xFF8A968D)

/**
 * Full-screen lock shown over a blocked app. Displays a live countdown to when
 * the block lifts (or "без ограничения времени" if indefinite) and a single
 * way out: go back home.
 */
@Composable
fun BlockedScreen(
    untilMs: Long?,
    onBack: () -> Unit,
    onElapsed: () -> Unit,
) {
    // Re-sampled every second to drive the countdown.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (untilMs != null && nowMs >= untilMs) {
                onElapsed()
                break
            }
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1110), Background),
                    radius = 1400f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Lock glyph (vector-free): a rounded badge with a keyhole feel.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Accent.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔒", fontSize = 44.sp)
            }

            Column(Modifier.height(28.dp)) {}

            Text(
                text = "Заблокировано",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.height(10.dp)) {}
            Text(
                text = if (untilMs == null) {
                    "Это приложение заблокировано без ограничения времени."
                } else {
                    "Откроется через"
                },
                color = TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            if (untilMs != null) {
                Column(Modifier.height(8.dp)) {}
                Text(
                    text = formatRemaining((untilMs - nowMs).coerceAtLeast(0)),
                    color = Accent,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(Modifier.height(48.dp)) {}

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            ) {
                Text("Назад", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "4 ч 12 мин" / "12 мин 30 с" / "45 с" from a millisecond remainder. */
private fun formatRemaining(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "$h ч $m мин"
        m > 0 -> "$m мин $s с"
        else -> "$s с"
    }
}
