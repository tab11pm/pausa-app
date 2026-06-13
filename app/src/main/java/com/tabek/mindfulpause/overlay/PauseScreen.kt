package com.tabek.mindfulpause.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.data.PauseConfig
import kotlinx.coroutines.delay

private val Background = Color(0xFF0B0F0C)
private val Accent = Color(0xFF22C55E)
private val AccentDeep = Color(0xFF16A34A)
private val TextPrimary = Color(0xFFF1F5F2)
private val TextMuted = Color(0xFF8A968D)

private enum class Phase { BREATHING, QUESTION }

/**
 * The mindful-pause UI shown over a tracked app.
 * Flow: optional breathing animation -> question with custom message ->
 * "Выйти" (reflect) or "Продолжить" (continue). The "Продолжить" button is
 * gated by an optional countdown so the choice can't be reflexive.
 */
@Composable
fun PauseScreen(
    config: PauseConfig,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    var phase by remember {
        mutableStateOf(if (config.breathingEnabled) Phase.BREATHING else Phase.QUESTION)
    }

    // Advance past the breathing phase after its configured duration.
    LaunchedEffect(Unit) {
        if (config.breathingEnabled) {
            delay(config.breathingSeconds * 1000L)
            phase = Phase.QUESTION
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF11201A), Background),
                    radius = 1400f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            Phase.BREATHING -> BreathingPhase(seconds = config.breathingSeconds)
            Phase.QUESTION -> QuestionPhase(
                config = config,
                onContinue = onContinue,
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun BreathingPhase(seconds: Int) {
    val transition = rememberInfiniteTransition(label = "breath")
    // One full inhale+exhale cycle. Kept around 4s regardless of total length.
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Accent.copy(alpha = 0.35f), Color.Transparent),
                        ),
                        shape = RoundedCornerShape(percent = 50),
                    )
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(Accent.copy(alpha = 0.9f), RoundedCornerShape(percent = 50))
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Сделай вдох…",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun QuestionPhase(
    config: PauseConfig,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    // Optional countdown gating the "Продолжить" button.
    var remaining by remember {
        mutableStateOf(if (config.timerEnabled) config.timerSeconds else 0)
    }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
    }
    val continueEnabled = remaining == 0

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500)),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (config.messageEnabled) config.message else "Ты осознанно сюда заходишь?",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Остановись на секунду. Это твой выбор.",
                color = TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(56.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Exit is the encouraged, prominent action.
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentDeep,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Выйти", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onContinue,
                    enabled = continueEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextMuted,
                    ),
                ) {
                    Text(
                        text = if (continueEnabled) "Продолжить" else "Продолжить · $remaining",
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
