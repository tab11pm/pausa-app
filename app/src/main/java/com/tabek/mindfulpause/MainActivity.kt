package com.tabek.mindfulpause

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tabek.mindfulpause.ui.MainViewModel
import com.tabek.mindfulpause.ui.screens.HomeScreen
import com.tabek.mindfulpause.ui.screens.StatsScreen
import com.tabek.mindfulpause.ui.theme.Accent
import com.tabek.mindfulpause.ui.theme.AccentDeep
import com.tabek.mindfulpause.ui.theme.Background
import com.tabek.mindfulpause.ui.theme.MindfulPauseTheme
import com.tabek.mindfulpause.ui.theme.SurfaceGlass
import com.tabek.mindfulpause.ui.theme.TextMuted
import com.tabek.mindfulpause.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        vm.loadApps()

        setContent {
            MindfulPauseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background,
                ) {
                    var tab by remember { mutableStateOf(Tab.HOME) }
                    Scaffold(
                        containerColor = Background,
                        bottomBar = {
                            NavigationBar(containerColor = SurfaceGlass) {
                                NavigationBarItem(
                                    selected = tab == Tab.HOME,
                                    onClick = { tab = Tab.HOME },
                                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                    label = { Text("Главная") },
                                    colors = navColors(),
                                )
                                NavigationBarItem(
                                    selected = tab == Tab.STATS,
                                    onClick = { tab = Tab.STATS },
                                    icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                                    label = { Text("Статистика") },
                                    colors = navColors(),
                                )
                            }
                        },
                    ) { inner ->
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(inner),
                            color = Background,
                        ) {
                            when (tab) {
                                Tab.HOME -> HomeScreen(
                                    vm = vm,
                                    onRequestOverlay = ::openOverlaySettings,
                                    onRequestAccessibility = ::openAccessibilitySettings,
                                )
                                Tab.STATS -> StatsScreen(vm = vm)
                            }
                        }
                    }
                }
            }
        }
    }

    private enum class Tab { HOME, STATS }

    override fun onResume() {
        super.onResume()
        // Permissions are granted in external system screens, so re-check here.
        vm.refreshPermissions()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@androidx.compose.runtime.Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Accent,
    selectedTextColor = TextPrimary,
    indicatorColor = AccentDeep.copy(alpha = 0.18f),
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted,
)
