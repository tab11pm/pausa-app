package com.tabek.mindfulpause.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.ui.MainViewModel
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val config by vm.pauseConfig.collectAsState()
    val reminderConfig by vm.reminderConfig.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Настройки", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Настрой паузу под себя", color = TextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))

        SectionTitle("СООБЩЕНИЕ")
        config?.let { cfg ->
            MessageSettings(
                config = cfg,
                onEnabled = vm::setMessageEnabled,
                onChange = vm::setMessage,
            )
        }
        Spacer(Modifier.height(24.dp))

        SectionTitle("НАПОМИНАНИЯ")
        ReminderSettings(
            config = reminderConfig,
            onEnabled = vm::setReminderEnabled,
            onInterval = vm::setReminderInterval,
        )
        Spacer(Modifier.height(24.dp))

        SectionTitle("РЕЗЕРВНАЯ КОПИЯ")
        BackupSettings(vm)
        Spacer(Modifier.height(40.dp))
    }
}
