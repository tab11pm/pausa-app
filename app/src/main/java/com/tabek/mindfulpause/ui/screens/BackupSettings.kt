package com.tabek.mindfulpause.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tabek.mindfulpause.ui.MainViewModel
import com.tabek.mindfulpause.ui.theme.SurfaceGlass
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettings(vm: MainViewModel) {
    val state by vm.backupState.collectAsState()
    var pickerError by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }

    GlassCard {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Сохраните статистику, настройки и блокировки перед удалением приложения. " +
                    "Выберите папку «Документы» или облачное хранилище: файл останется после переустановки.",
                color = TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && !state.awaitingConfirmation,
                onClick = {
                    pickerError = null
                    try {
                        val date = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date())
                        save.launch("pausa-$date.mpbackup")
                    } catch (_: Exception) {
                        pickerError = "Не удалось открыть выбор файла. Проверьте, доступно ли приложение «Файлы»."
                    }
                },
            ) { Text("Сохранить в файл") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && !state.awaitingConfirmation,
                onClick = {
                    pickerError = null
                    try {
                        open.launch(arrayOf("*/*"))
                    } catch (_: Exception) {
                        pickerError = "Не удалось открыть выбор файла. Проверьте, доступно ли приложение «Файлы»."
                    }
                },
            ) { Text("Восстановить из файла") }
            if (state.busy) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Обработка копии…", color = TextMuted)
            }
            (pickerError ?: state.message)?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = TextPrimary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }

    if (state.awaitingConfirmation) {
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            containerColor = SurfaceGlass,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = { Text("Восстановить данные?") },
            text = {
                Text("Текущие статистика, настройки и блокировки будут заменены данными из файла. " +
                    "При необходимости сначала сохраните текущие данные в отдельную копию.")
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore() }) { Text("Восстановить") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Отмена") } },
        )
    }
}
