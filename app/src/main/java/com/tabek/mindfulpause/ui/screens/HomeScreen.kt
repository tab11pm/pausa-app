package com.tabek.mindfulpause.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.ui.MainViewModel
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val permissions by vm.permissions.collectAsState()
    val config by vm.pauseConfig.collectAsState()
    val hasUsagePermission by vm.hasUsagePermission.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("Пауза", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            "Осознанный вход в соцсети",
            color = TextMuted,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(24.dp))

        // Show the gate until core perms are granted AND battery is exempt
        // (battery is the usual reason the pause "stops working" on Samsung).
        if (!permissions.allGranted || !permissions.batteryUnrestricted) {
            PermissionGate(
                overlayGranted = permissions.overlayGranted,
                accessibilityEnabled = permissions.accessibilityEnabled,
                batteryUnrestricted = permissions.batteryUnrestricted,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
                onRequestBattery = onRequestBattery,
            )
            Spacer(Modifier.height(24.dp))
        }

        // Usage stats permission prompt
        if (!hasUsagePermission) {
            GlassCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        "Статистика использования",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Разрешите доступ к статистике, чтобы видеть время использования приложений",
                        color = TextMuted,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                    ) {
                        Text("Открыть настройки", color = Accent)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Gesture settings
        SectionTitle("ЭКРАН ПАУЗЫ")
        config?.let { cfg ->
            GestureSettings(
                config = cfg,
                onBreathing = { enabled -> vm.setBreathing(enabled) },
                onTimer = { vm.setTimer(it) },
            )
        }
    }
}
