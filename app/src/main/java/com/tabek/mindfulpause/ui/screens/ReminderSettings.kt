package com.tabek.mindfulpause.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tabek.mindfulpause.BuildConfig
import com.tabek.mindfulpause.data.ReminderConfig
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

@Composable
fun ReminderSettings(
    config: ReminderConfig,
    onEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
) {
    GlassCard {
        ToggleRow(
            title = "Напомнить выйти",
            subtitle = "Спросит, закончил ли ты дела в приложении",
            checked = config.enabled,
            onCheckedChange = onEnabled,
        )
        AnimatedVisibility(config.enabled) {
            Column(Modifier.selectableGroup().padding(bottom = 8.dp)) {
                if (BuildConfig.DEBUG) {
                    Text(
                        text = "Первое напоминание через 30 секунд",
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                } else {
                    Text(
                        text = "Первое напоминание через",
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                    ReminderConfig.INTERVALS.forEach { minutes ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(
                                    selected = config.intervalMinutes == minutes,
                                    role = Role.RadioButton,
                                    onClick = { onInterval(minutes) },
                                )
                                .padding(horizontal = 18.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = config.intervalMinutes == minutes,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Accent,
                                    unselectedColor = TextMuted,
                                ),
                            )
                            Text(
                                text = "$minutes мин",
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "После «Ещё ${ReminderConfig.SNOOZE_DURATION_TEXT}» вопрос повторится через ${ReminderConfig.SNOOZE_DURATION_TEXT}.",
                    color = TextMuted,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
    }
}
