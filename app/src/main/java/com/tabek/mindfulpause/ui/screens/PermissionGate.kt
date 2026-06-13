package com.tabek.mindfulpause.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.AccentDeep
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

/**
 * Two-step permission onboarding. Shown until both grants are in place, then
 * collapses to a compact "всё готово" confirmation.
 */
@Composable
fun PermissionGate(
    overlayGranted: Boolean,
    accessibilityEnabled: Boolean,
    batteryUnrestricted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val allReady = overlayGranted && accessibilityEnabled && batteryUnrestricted
    GlassCard {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = if (allReady) "Всё готово" else "Настройка разрешений",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (allReady)
                    "Приложение готово замечать выбранные соцсети."
                else
                    "Выдай все три, иначе пауза может перестать срабатывать.",
                color = TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))

            PermissionStep(
                index = 1,
                title = "Доступ к специальным возможностям",
                description = "Позволяет замечать, какое приложение открыто.",
                granted = accessibilityEnabled,
                onClick = onRequestAccessibility,
            )
            Spacer(Modifier.height(12.dp))
            PermissionStep(
                index = 2,
                title = "Показ поверх других приложений",
                description = "Позволяет показать экран паузы над соцсетью.",
                granted = overlayGranted,
                onClick = onRequestOverlay,
            )
            Spacer(Modifier.height(12.dp))
            PermissionStep(
                index = 3,
                title = "Отключить экономию батареи",
                description = "Без этого система выгружает службу и разрешение слетает.",
                granted = batteryUnrestricted,
                onClick = onRequestBattery,
            )
        }
    }
}

@Composable
private fun PermissionStep(
    index: Int,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) Accent else TextMuted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextMuted, fontSize = 13.sp)
        }
        if (!granted) {
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentDeep,
                    contentColor = TextPrimary,
                ),
            ) {
                Text("Выдать", fontSize = 14.sp)
            }
        }
    }
}
