package com.tabek.mindfulpause.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabek.mindfulpause.data.SettingsRepository
import com.tabek.mindfulpause.ui.MainViewModel
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
) {
    val permissions by vm.permissions.collectAsState()
    val tracked by vm.trackedApps.collectAsState()
    val apps by vm.installedApps.collectAsState()
    val config by vm.pauseConfig.collectAsState()

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

        if (!permissions.allGranted) {
            PermissionGate(
                overlayGranted = permissions.overlayGranted,
                accessibilityEnabled = permissions.accessibilityEnabled,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
            )
            Spacer(Modifier.height(24.dp))
        }

        // Gesture settings
        SectionTitle("ЭКРАН ПАУЗЫ")
        config?.let { cfg ->
            GestureSettings(
                config = cfg,
                onBreathing = { enabled -> vm.setBreathing(enabled) },
                onMessageEnabled = { vm.setMessageEnabled(it) },
                onMessageChange = { vm.setMessage(it) },
                onTimer = { vm.setTimer(it) },
            )
        }
        Spacer(Modifier.height(24.dp))

        // App picker
        SectionTitle("ОТСЛЕЖИВАТЬ ПРИЛОЖЕНИЯ")
        AppPicker(
            apps = apps,
            tracked = tracked,
            onToggle = { pkg, on -> vm.toggleApp(pkg, on) },
        )
        Spacer(Modifier.height(40.dp))
    }
}
