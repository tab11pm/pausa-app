package com.tabek.mindfulpause.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.data.BlockRepository
import com.tabek.mindfulpause.data.TimedBlock
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.AccentDeep
import com.tabek.mindfulpause.ui.theme.Background
import com.tabek.mindfulpause.ui.theme.Divider
import com.tabek.mindfulpause.ui.theme.SurfaceElevated
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Bottom sheet to configure blocking for a single app:
 *  - timed block via presets / custom hours / no limit
 *  - an optional daily open-limit
 * If a block is already active, shows its status with an unblock action.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockSheet(
    appLabel: String,
    packageName: String,
    activeBlock: TimedBlock?,
    dailyLimit: Int?,
    onBlock: (durationMs: Long?) -> Unit,
    onUnblock: () -> Unit,
    onSetLimit: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { System.currentTimeMillis() }
    val isBlocked = activeBlock?.isActive(now) == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(appLabel, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isBlocked) "Приложение сейчас заблокировано" else "Заблокировать на время",
                color = TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))

            if (isBlocked) {
                ActiveBlockCard(activeBlock!!, now, onUnblock)
                Spacer(Modifier.height(24.dp))
            } else {
                PresetGrid(onBlock = onBlock)
                Spacer(Modifier.height(16.dp))
                CustomHours(onBlock = onBlock)
                Spacer(Modifier.height(24.dp))
            }

            SectionTitle("ЛИМИТ ОТКРЫТИЙ В ДЕНЬ")
            DailyLimitRow(current = dailyLimit, onSetLimit = onSetLimit)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetGrid(onBlock: (Long?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockRepository.PRESETS_MIN.forEach { min ->
            val label = when (min) {
                null -> "Без лимита"
                15 -> "15 мин"
                30 -> "30 мин"
                60 -> "1 час"
                180 -> "3 часа"
                300 -> "5 часов"
                else -> "$min мин"
            }
            PresetChip(label) { onBlock(min?.let { it * 60_000L }) }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CustomHours(onBlock: (Long?) -> Unit) {
    var hours by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hours,
            onValueChange = { hours = it.filter { c -> c.isDigit() }.take(2) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Свой: часов", color = TextMuted, fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Divider,
                cursorColor = Accent,
            ),
        )
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = {
                val h = hours.toIntOrNull()
                if (h != null && h > 0) onBlock(h * 60L * 60_000L)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDeep, contentColor = TextPrimary),
        ) {
            Text("Блок", fontSize = 15.sp)
        }
    }
}

@Composable
private fun ActiveBlockCard(block: TimedBlock, now: Long, onUnblock: () -> Unit) {
    GlassCard {
        Column(Modifier.padding(18.dp)) {
            val remaining = block.remaining(now)
            Text(
                text = if (block.isIndefinite) "Без ограничения времени"
                else "Осталось: ${formatRemaining(remaining ?: 0)}",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onUnblock,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary),
            ) {
                Text("Снять блокировку", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun DailyLimitRow(current: Int?, onSetLimit: (Int?) -> Unit) {
    var value by remember(current) { mutableStateOf(current?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter { c -> c.isDigit() }.take(3) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Напр. 5 раз", color = TextMuted, fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Divider,
                cursorColor = Accent,
            ),
        )
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = { onSetLimit(value.toIntOrNull()) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDeep, contentColor = TextPrimary),
        ) {
            Text("OK", fontSize = 15.sp)
        }
    }
    if (current != null) {
        TextButton(onClick = { onSetLimit(null) }) {
            Text("Убрать лимит", color = TextMuted, fontSize = 13.sp)
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "$h ч $m мин"
        else -> "$m мин"
    }
}
