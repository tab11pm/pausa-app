package com.tabek.mindfulpause.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.ui.theme.AccentDeep
import com.tabek.mindfulpause.ui.theme.Background
import com.tabek.mindfulpause.ui.theme.MindfulPauseTheme
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary
import com.tabek.mindfulpause.data.ReminderConfig

@Composable
fun ReminderScreen(onExit: () -> Unit, onSnooze: () -> Unit) {
    MindfulPauseTheme {
        Surface(color = Background) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding()
                    .verticalScroll(rememberScrollState()).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Ты уже закончил свои дела?",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Можно выйти или остаться ещё на ${ReminderConfig.SNOOZE_DURATION_TEXT}.",
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentDeep,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(
                        text = "Выйти",
                        modifier = Modifier.padding(vertical = 10.dp),
                        fontSize = 17.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "Ещё ${ReminderConfig.SNOOZE_DURATION_TEXT}",
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = TextMuted,
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }
}
