package com.tabek.mindfulpause.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabek.mindfulpause.data.AppInfo
import com.tabek.mindfulpause.data.BackupCodec
import com.tabek.mindfulpause.data.BackupRepository
import com.tabek.mindfulpause.data.blockStore
import com.tabek.mindfulpause.data.dataStore
import com.tabek.mindfulpause.data.eventStore
import com.tabek.mindfulpause.data.BlockRepository
import com.tabek.mindfulpause.data.DailyLimit
import com.tabek.mindfulpause.data.EventLog
import com.tabek.mindfulpause.data.PauseConfig
import com.tabek.mindfulpause.data.ReminderConfig
import com.tabek.mindfulpause.data.Period
import com.tabek.mindfulpause.data.SettingsRepository
import com.tabek.mindfulpause.data.Stats
import com.tabek.mindfulpause.data.TimedBlock
import com.tabek.mindfulpause.data.canDrawOverlays
import com.tabek.mindfulpause.data.computeStats
import com.tabek.mindfulpause.data.getUsageStats
import com.tabek.mindfulpause.data.hasUsageStatsPermission
import com.tabek.mindfulpause.data.isAccessibilityServiceEnabled
import com.tabek.mindfulpause.data.isIgnoringBatteryOptimizations
import com.tabek.mindfulpause.data.loadLaunchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PermissionState(
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val batteryUnrestricted: Boolean = false,
) {
    /** Core permissions required for the app to function. Battery exemption is
     *  strongly recommended but not strictly required, so it's not gated here. */
    val allGranted: Boolean get() = overlayGranted && accessibilityEnabled
}

data class BackupUiState(
    val busy: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val eventLog = EventLog(app)
    private val blocks = BlockRepository(app)
    private val backups = BackupRepository(mapOf(
        "settings" to app.dataStore, "blocks" to app.blockStore, "events" to app.eventStore,
    ))
    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState = _backupState.asStateFlow()
    private var pendingBackup: ByteArray? = null

    fun exportBackup(uri: Uri) = backupAction("Не удалось сохранить копию. Попробуйте выбрать другой файл.") {
        val bytes = backups.export()
        val resolver = getApplication<Application>().contentResolver
        (resolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")).use {
            it.write(bytes)
        }
        _backupState.value = BackupUiState(message = "Копия сохранена. Её можно восстановить после переустановки.")
    }

    fun prepareRestore(uri: Uri) = backupAction("Не удалось прочитать копию. Выберите целый файл .mpbackup, созданный приложением.") {
        val resolver = getApplication<Application>().contentResolver
        val bytes = (resolver.openInputStream(uri) ?: throw IOException("No input stream")).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size() + read <= BackupCodec.MAX_BYTES) { "Backup too large" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        BackupCodec.decode(bytes)
        pendingBackup = bytes
        _backupState.value = BackupUiState(awaitingConfirmation = true)
    }

    fun cancelRestore() {
        if (_backupState.value.busy) return
        pendingBackup = null
        _backupState.value = BackupUiState()
    }

    fun confirmRestore() = backupAction("Не удалось восстановить данные. Попробуйте ещё раз.") {
        val bytes = pendingBackup ?: throw IOException("No pending backup")
        try {
            backups.restore(bytes)
            _backupState.value = BackupUiState(message = "Статистика и настройки восстановлены.")
        } finally {
            pendingBackup = null
        }
    }

    private fun backupAction(error: String, action: suspend () -> Unit) {
        if (_backupState.value.busy) return
        _backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                pendingBackup = null
                _backupState.value = BackupUiState(message = error)
            }
        }
    }

    /** Active timed blocks, keyed by package. */
    val timedBlocks: StateFlow<Map<String, TimedBlock>> =
        blocks.timedBlocks
            .map { list -> list.associateBy { it.packageName } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Daily open limits, keyed by package. */
    val dailyLimits: StateFlow<Map<String, Int>> =
        blocks.dailyLimits
            .map { list -> list.associate { it.packageName to it.maxOpens } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    val reminderConfig: StateFlow<ReminderConfig> =
        repo.reminderConfig.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderConfig())

    fun setReminderEnabled(enabled: Boolean) =
        viewModelScope.launch { repo.setReminderEnabled(enabled) }

    fun setReminderInterval(minutes: Int) =
        viewModelScope.launch { repo.setReminderInterval(minutes) }

    val reflectedCount: StateFlow<Int> =
        repo.reflectedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val continuedCount: StateFlow<Int> =
        repo.continuedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()
    private val appLoadMutex = Mutex()
    private var socialAppsLoaded = false
    private var allAppsLoaded = false
    private val _appsLoading = MutableStateFlow(true)
    val appsLoading = _appsLoading.asStateFlow()
    private val _appsError = MutableStateFlow<String?>(null)
    val appsError = _appsError.asStateFlow()

    // Stats only need labels for packages with events, not every installed icon.
    val statsAppLabels: StateFlow<Map<String, String>> = stats.map { summary ->
        withContext(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            summary?.perApp.orEmpty().mapNotNull { app ->
                runCatching {
                    app.packageName to pm.getApplicationLabel(
                        pm.getApplicationInfo(app.packageName, 0),
                    ).toString()
                }.getOrNull()
            }.toMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _hasUsagePermission = MutableStateFlow(false)
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    /** Re-read permission state — call from Activity onResume since the user
     *  grants these in external system screens. */
    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = PermissionState(
            overlayGranted = canDrawOverlays(ctx),
            accessibilityEnabled = isAccessibilityServiceEnabled(ctx),
            batteryUnrestricted = isIgnoringBatteryOptimizations(ctx),
        )
        val usageGranted = hasUsageStatsPermission(ctx)
        if (_hasUsagePermission.value != usageGranted) {
            _hasUsagePermission.value = usageGranted
            refreshUsageStats()
        }
    }

    fun loadApps(allApps: Boolean = false) {
        viewModelScope.launch {
            appLoadMutex.withLock {
                if (allAppsLoaded || (!allApps && socialAppsLoaded)) {
                    _appsError.value = null
                    return@withLock
                }
                _appsLoading.value = true
                _appsError.value = null
                try {
                    val ctx = getApplication<Application>()
                    val cached = _installedApps.value
                    val apps = withContext(Dispatchers.IO) {
                        loadLaunchableApps(ctx, socialOnly = !allApps, cachedApps = cached)
                    }
                    val usageMap = withContext(Dispatchers.IO) {
                        if (hasUsageStatsPermission(ctx)) getUsageStats(ctx) else emptyMap()
                    }
                    _installedApps.value = apps.map { app ->
                        app.copy(usageTimeMs = usageMap[app.packageName] ?: 0L)
                    }
                    socialAppsLoaded = true
                    if (allApps) allAppsLoaded = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _appsError.value = "Не удалось загрузить приложения. Попробуйте ещё раз."
                } finally {
                    _appsLoading.value = false
                }
            }
        }
    }

    fun refreshUsageStats() {
        if (!socialAppsLoaded) return
        viewModelScope.launch {
            appLoadMutex.withLock {
                val ctx = getApplication<Application>()
                val usageMap = withContext(Dispatchers.IO) {
                    if (hasUsageStatsPermission(ctx)) getUsageStats(ctx) else emptyMap()
                }
                _installedApps.value = _installedApps.value.map { app ->
                    app.copy(usageTimeMs = usageMap[app.packageName] ?: 0L)
                }
            }
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

    // ---- Blocking actions ----------------------------------------------

    /** Block an app for [durationMs] (null = indefinite). */
    fun blockApp(packageName: String, durationMs: Long?) =
        viewModelScope.launch {
            blocks.setTimedBlock(packageName, System.currentTimeMillis(), durationMs)
        }

    fun unblockApp(packageName: String) =
        viewModelScope.launch { blocks.clearTimedBlock(packageName) }

    /** Set a daily open limit (null/0 removes it). */
    fun setDailyLimit(packageName: String, maxOpens: Int?) =
        viewModelScope.launch { blocks.setDailyLimit(packageName, maxOpens) }
}
