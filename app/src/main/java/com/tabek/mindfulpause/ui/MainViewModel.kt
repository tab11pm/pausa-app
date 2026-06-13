package com.tabek.mindfulpause.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabek.mindfulpause.data.AppInfo
import com.tabek.mindfulpause.data.EventLog
import com.tabek.mindfulpause.data.PauseConfig
import com.tabek.mindfulpause.data.Period
import com.tabek.mindfulpause.data.SettingsRepository
import com.tabek.mindfulpause.data.Stats
import com.tabek.mindfulpause.data.canDrawOverlays
import com.tabek.mindfulpause.data.computeStats
import com.tabek.mindfulpause.data.isAccessibilityServiceEnabled
import com.tabek.mindfulpause.data.loadLaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionState(
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
) {
    val allGranted: Boolean get() = overlayGranted && accessibilityEnabled
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val eventLog = EventLog(app)

    /** Selected stats period (drives the Stats screen). */
    private val _period = MutableStateFlow(Period.WEEK)
    val period: StateFlow<Period> = _period.asStateFlow()

    /**
     * Aggregated statistics, recomputed whenever events or the chosen period
     * change. now is sampled at collection time inside the combine.
     */
    val stats: StateFlow<Stats?> =
        combine(eventLog.events, _period) { events, period ->
            val chartDays = if (period == Period.ALL) 30 else 7
            computeStats(events, System.currentTimeMillis(), period, chartDays)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setPeriod(p: Period) { _period.value = p }

    val trackedApps: StateFlow<Set<String>> =
        repo.trackedApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val pauseConfig: StateFlow<PauseConfig?> =
        repo.pauseConfig.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val reflectedCount: StateFlow<Int> =
        repo.reflectedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val continuedCount: StateFlow<Int> =
        repo.continuedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    /** Re-read permission state — call from Activity onResume since the user
     *  grants these in external system screens. */
    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = PermissionState(
            overlayGranted = canDrawOverlays(ctx),
            accessibilityEnabled = isAccessibilityServiceEnabled(ctx),
        )
    }

    fun loadApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { loadLaunchableApps(getApplication()) }
            _installedApps.value = apps
        }
    }

    fun toggleApp(packageName: String, tracked: Boolean) =
        viewModelScope.launch { repo.setAppTracked(packageName, tracked) }

    fun setBreathing(enabled: Boolean, seconds: Int? = null) =
        viewModelScope.launch { repo.setBreathing(enabled, seconds) }

    fun setMessageEnabled(enabled: Boolean) =
        viewModelScope.launch { repo.setMessageEnabled(enabled) }

    fun setMessage(text: String) =
        viewModelScope.launch { repo.setMessage(text) }

    fun setTimer(enabled: Boolean, seconds: Int? = null) =
        viewModelScope.launch { repo.setTimer(enabled, seconds) }
}
