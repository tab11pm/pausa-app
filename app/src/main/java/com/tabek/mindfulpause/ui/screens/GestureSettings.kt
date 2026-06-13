package com.tabek.mindfulpause.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.data.PauseConfig
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.Divider
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

@Composable
fun GestureSettings(
    config: PauseConfig,
    onBreathing: (Boolean) -> Unit,
    onMessageEnabled: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit,
    onTimer: (Boolean) -> Unit,
) {
    GlassCard {
        ToggleRow(
            title = "Дыхание",
            subtitle = "Анимация вдоха перед вопросом",
            checked = config.breathingEnabled,
            onCheckedChange = onBreathing,
        )
        HorizontalDivider(color = Divider, thickness = 1.dp)

        ToggleRow(
            title = "Своё сообщение",
            subtitle = "Покажет твой текст-напоминание",
            checked = config.messageEnabled,
            onCheckedChange = onMessageEnabled,
        )
        AnimatedVisibility(visible = config.messageEnabled) {
            MessageField(
                initial = config.message,
                onChange = onMessageChange,
            )
        }
        HorizontalDivider(color = Divider, thickness = 1.dp)

        ToggleRow(
            title = "Задержка",
            subtitle = "Кнопка «Продолжить» откроется не сразу",
            checked = config.timerEnabled,
            onCheckedChange = onTimer,
        )
    }
}

@Composable
private fun MessageField(
    initial: String,
    onChange: (String) -> Unit,
) {
    // Local editable copy; committed to storage on every change.
    var text by rememberSaveable(initial) { mutableStateOf(initial) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        label = { Text("Текст на экране паузы", color = TextMuted) },
        singleLine = false,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Divider,
            cursorColor = Accent,
        ),
    )
}
