package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.LunarCalendarHelper
import com.example.calendar.LunarDate
import com.example.data.BackgroundStyleOption
import com.example.data.BadgeSizeOption
import com.example.data.DisplayModeOption
import com.example.data.FloatingSettings
import com.example.data.FloatingSettingsRepository
import com.example.data.TextFormatOption
import com.example.service.FloatingLunarService
import com.example.update.AppUpdateManager
import com.example.update.UpdateInfo
import com.example.update.UpdateState
import com.example.util.HomeAppDetector
import com.example.util.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = FloatingSettingsRepository(application)
    val settings: StateFlow<FloatingSettings> = repository.settingsState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FloatingSettings()
    )

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _hasUsageStatsPermission = MutableStateFlow(false)
    val hasUsageStatsPermission: StateFlow<Boolean> = _hasUsageStatsPermission.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _selectedCalendar = MutableStateFlow(Calendar.getInstance())
    private val _selectedLunarDate = MutableStateFlow(LunarCalendarHelper.getTodayLunar())
    val selectedLunarDate: StateFlow<LunarDate> = _selectedLunarDate.asStateFlow()

    private val _liveForegroundPackage = MutableStateFlow<String?>(null)
    val liveForegroundPackage: StateFlow<String?> = _liveForegroundPackage.asStateFlow()

    private val _isLiveHomeDetected = MutableStateFlow(true)
    val isLiveHomeDetected: StateFlow<Boolean> = _isLiveHomeDetected.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersionName: String = AppUpdateManager.getCurrentVersionName(application)
    val currentVersionCode: Int = AppUpdateManager.getCurrentVersionCode(application)

    private var downloadedApkFile: java.io.File? = null

    init {
        checkPermission()
        loadInstalledApps()
        startLiveDiagnostic()
        checkUpdateOnStartup()
    }

    private fun checkUpdateOnStartup() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (settings.value.autoCheckUpdate) {
                checkForUpdate(manual = false)
            }
        }
    }

    private fun startLiveDiagnostic() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val context = getApplication<Application>()
                val fg = HomeAppDetector.getForegroundPackage(context)
                val isHome = HomeAppDetector.isCurrentScreenHome(
                    context = context,
                    customExtraPackages = settings.value.customHomePackages,
                    supportPip = settings.value.supportPipMode,
                    pipAllowedPackages = settings.value.pipAllowedPackages
                )
                _liveForegroundPackage.value = fg
                _isLiveHomeDetected.value = isHome
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun addPackageToHome(pkg: String) {
        if (pkg.isBlank()) return
        val current = settings.value.customHomePackages
        val list = current.split(",", ";", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (!list.contains(pkg)) {
            list.add(pkg)
            repository.updateCustomHomePackages(list.joinToString(", "))
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val apps = HomeAppDetector.getInstalledApps(getApplication())
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun checkPermission() {
        val context = getApplication<Application>()
        _hasOverlayPermission.value = Settings.canDrawOverlays(context)
        _hasUsageStatsPermission.value = HomeAppDetector.hasUsageStatsPermission(context)
    }

    fun toggleService(context: Context) {
        val current = settings.value
        val newStatus = !current.isEnabled

        if (newStatus) {
            if (!Settings.canDrawOverlays(context)) {
                _hasOverlayPermission.value = false
                return
            }
            repository.updateEnabled(true)
            startOverlayService(context)
        } else {
            repository.updateEnabled(false)
            stopOverlayService(context)
        }
    }

    private fun startOverlayService(context: Context) {
        val intent = Intent(context, FloatingLunarService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopOverlayService(context: Context) {
        val intent = Intent(context, FloatingLunarService::class.java)
        context.stopService(intent)
    }

    fun setTextColor(colorHex: String) {
        repository.updateTextColor(colorHex)
    }

    fun setTextFormat(format: TextFormatOption) {
        repository.updateFormat(format)
    }

    fun setBgStyle(bg: BackgroundStyleOption) {
        repository.updateBgStyle(bg)
    }

    fun setSizeOption(size: BadgeSizeOption) {
        repository.updateSize(size)
    }

    fun setFontSizeSp(sp: Int) {
        repository.updateFontSize(sp)
    }

    fun setLockPosition(locked: Boolean) {
        repository.updateLockPosition(locked)
    }

    fun setDisplayMode(mode: DisplayModeOption) {
        repository.updateDisplayMode(mode)
    }

    fun setCustomHomePackages(packages: String) {
        repository.updateCustomHomePackages(packages)
    }

    fun setSupportPipMode(enabled: Boolean) {
        repository.updateSupportPipMode(enabled)
    }

    fun setPipAllowedPackages(packages: String) {
        repository.updatePipAllowedPackages(packages)
    }

    fun toggleCustomHomePackage(pkg: String) {
        val current = settings.value.customHomePackages
        val list = current.split(",", ";", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.contains(pkg)) {
            list.remove(pkg)
        } else {
            list.add(pkg)
        }
        val result = list.joinToString(", ")
        repository.updateCustomHomePackages(result)
    }

    fun setAutoHideWhenTextNotFound(enabled: Boolean) {
        repository.updateAutoHideWhenTextNotFound(enabled)
    }

    fun setTargetKeywords(keywords: String) {
        repository.updateTargetKeywords(keywords)
    }

    fun setExactMatch(exact: Boolean) {
        repository.updateExactMatch(exact)
    }

    fun navigateDate(daysOffset: Int) {
        val cal = _selectedCalendar.value.clone() as Calendar
        cal.add(Calendar.DAY_OF_MONTH, daysOffset)
        _selectedCalendar.value = cal

        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        _selectedLunarDate.value = LunarCalendarHelper.convertSolarToLunar(d, m, y)
    }

    fun resetToToday() {
        val cal = Calendar.getInstance()
        _selectedCalendar.value = cal
        _selectedLunarDate.value = LunarCalendarHelper.getTodayLunar()
    }

    fun checkForUpdate(manual: Boolean = true) {
        viewModelScope.launch {
            if (manual) {
                _updateState.value = UpdateState.Checking
            }
            val context = getApplication<Application>()
            val result = AppUpdateManager.checkForUpdate(context, settings.value.customUpdateUrl)
            result.onSuccess { updateInfo ->
                if (updateInfo != null) {
                    _updateState.value = UpdateState.Available(updateInfo)
                } else {
                    if (manual) {
                        _updateState.value = UpdateState.UpToDate(currentVersionName)
                    }
                }
            }.onFailure { error ->
                if (manual) {
                    _updateState.value = UpdateState.Error(error.message ?: "Không thể kết nối máy chủ cập nhật")
                }
            }
        }
    }

    fun downloadAndInstallUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _updateState.value = UpdateState.Downloading(
                info = info,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L
            )

            val downloadResult = AppUpdateManager.downloadApk(context, info.downloadUrl) { progress, downloaded, total ->
                _updateState.value = UpdateState.Downloading(
                    info = info,
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }

            downloadResult.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                _updateState.value = UpdateState.ReadyToInstall(info, apkFile)
                AppUpdateManager.installApk(context, apkFile)
            }.onFailure { error ->
                _updateState.value = UpdateState.Error("Tải gói cập nhật thất bại: ${error.message}")
            }
        }
    }

    fun installDownloadedApk() {
        val apkFile = downloadedApkFile ?: return
        val context = getApplication<Application>()
        AppUpdateManager.installApk(context, apkFile)
    }

    fun simulateUpdateForTesting() {
        val info = AppUpdateManager.getSimulatedUpdateInfo(currentVersionName, currentVersionCode)
        _updateState.value = UpdateState.Available(info)
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateState.Idle
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        repository.updateAutoCheckUpdate(enabled)
    }

    fun setCustomUpdateUrl(url: String) {
        repository.updateCustomUpdateUrl(url)
    }
}
