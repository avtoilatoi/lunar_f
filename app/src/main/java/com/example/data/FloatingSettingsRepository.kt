package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TextFormatOption(val id: String, val displayName: String, val example: String) {
    SHORT("short", "Ngắn (18/6)", "18/6"),
    PAD_ZERO("pad_zero", "Đầy đủ (18/06)", "18/06"),
    SHORT_AM("short_am", "Có chữ Âm lịch (18/6 Âm lịch)", "18/6 Âm lịch"),
    FULL_TEXT("full_text", "Chi tiết (Ngày 18 Thg 6)", "Ngày 18 Thg 6")
}

enum class BackgroundStyleOption(val id: String, val displayName: String) {
    TRANSLUCENT_DARK("translucent_dark", "Nền tối mờ (Khuyên dùng)"),
    GLASS_BORDER("glass_border", "Nền thủy tinh viền sáng"),
    PURE_DARK("pure_dark", "Nền đen tuyền"),
    TEXT_ONLY("text_only", "Trong suốt (Chỉ có chữ)")
}

enum class BadgeSizeOption(val id: String, val displayName: String, val widthDp: Int, val heightDp: Int, val fontSp: Int) {
    MINI_20X50("mini_20x50", "20x50px (Siêu nhỏ)", 52, 24, 12),
    MEDIUM_STANDARD("medium_standard", "Vừa vặn (Standard)", 66, 30, 14),
    LARGE_ACCESSIBLE("large_accessible", "Lớn (Dễ nhìn)", 82, 36, 16)
}

enum class DisplayModeOption(val id: String, val displayName: String, val description: String) {
    HOME_OR_NAVBAR("home_or_navbar", "Tự động nhận diện Màn hình Ô tô & Đa nhiệm PIP (Khuyên dùng)", "Luôn hiện khi ở màn hình chính ô tô, kể cả khi đang mở 2 ứng dụng chia đôi PIP (YouTube + Bản đồ). Tự động ẩn khi vào ứng dụng khác hoặc xem toàn màn hình."),
    NAVBAR_VISIBILITY("navbar_visibility", "Theo Thanh Điều Hướng / Thanh Đáy", "Tự động hiện khi thanh đáy/thanh điều hướng xuất hiện. Ẩn khi vào ứng dụng toàn màn hình không có thanh đáy."),
    HOME_ONLY("home_only", "Chỉ hiện ở Màn hình chính (Home Launcher / PIP)", "Chỉ hiện trên giao diện Launcher và Dashboard PIP ô tô. Tự động ẩn khi mở ứng dụng khác (Maps toàn màn hình, YouTube full, Settings...)."),
    ALWAYS_SHOW("always_show", "Luôn hiển thị trên mọi ứng dụng", "Widget luôn luôn hiển thị trên tất cả ứng dụng")
}

data class FloatingSettings(
    val isEnabled: Boolean = false,
    val textColorHex: String = "#FF1744", // Bright Red
    val textFormat: TextFormatOption = TextFormatOption.SHORT,
    val bgStyle: BackgroundStyleOption = BackgroundStyleOption.TRANSLUCENT_DARK,
    val sizeOption: BadgeSizeOption = BadgeSizeOption.MINI_20X50,
    val fontSizeSp: Int = 13,
    val posX: Int = 100,
    val posY: Int = 200,
    val lockPosition: Boolean = false,
    val displayMode: DisplayModeOption = DisplayModeOption.HOME_OR_NAVBAR,
    val customHomePackages: String = "",
    val supportPipMode: Boolean = true,
    val pipAllowedPackages: String = "com.google.android.youtube,com.google.android.apps.maps,com.vietmap.s1,com.vietmap.live,com.navitel,com.spotify.music,com.zing.mp3",
    val autoHideWhenTextNotFound: Boolean = false,
    val targetKeywords: String = "Lịch, Âm, YouTube, Facebook, Messenger",
    val exactMatch: Boolean = true,
    val autoCheckUpdate: Boolean = true,
    val customUpdateUrl: String = ""
)

class FloatingSettingsRepository(context: Context) {
    companion object {
        const val PREFS_NAME = "floating_lunar_prefs"

        fun getStorageContext(context: Context): Context {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val appContext = context.applicationContext ?: context
                val deviceContext = appContext.createDeviceProtectedStorageContext()
                try {
                    val userManager = appContext.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                    val isUserUnlocked = userManager?.isUserUnlocked ?: true
                    if (isUserUnlocked) {
                        deviceContext.moveSharedPreferencesFrom(appContext, PREFS_NAME)
                    }
                } catch (e: Exception) {
                    // Ignore migration issues
                }
                deviceContext
            } else {
                context.applicationContext ?: context
            }
        }
    }

    private val storageContext: Context = getStorageContext(context)
    private val prefs: SharedPreferences = storageContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<FloatingSettings> = _settingsState.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settingsState.value = loadSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun loadSettings(): FloatingSettings {
        val enabled = prefs.getBoolean("is_enabled", false)
        val color = prefs.getString("text_color_hex", "#FF1744") ?: "#FF1744"
        val formatId = prefs.getString("text_format_id", TextFormatOption.SHORT.id)
        val format = TextFormatOption.entries.find { it.id == formatId } ?: TextFormatOption.SHORT

        val bgId = prefs.getString("bg_style_id", BackgroundStyleOption.TRANSLUCENT_DARK.id)
        val bg = BackgroundStyleOption.entries.find { it.id == bgId } ?: BackgroundStyleOption.TRANSLUCENT_DARK

        val sizeId = prefs.getString("size_option_id", BadgeSizeOption.MINI_20X50.id)
        val size = BadgeSizeOption.entries.find { it.id == sizeId } ?: BadgeSizeOption.MINI_20X50

        val fontSp = prefs.getInt("font_size_sp", size.fontSp)
        val posX = prefs.getInt("pos_x", 100)
        val posY = prefs.getInt("pos_y", 250)
        val lock = prefs.getBoolean("lock_pos", false)

        val modeId = prefs.getString("display_mode_id", DisplayModeOption.NAVBAR_VISIBILITY.id)
        val displayMode = DisplayModeOption.entries.find { it.id == modeId } ?: DisplayModeOption.NAVBAR_VISIBILITY

        val customHomePackages = prefs.getString("custom_home_packages", "") ?: ""
        val supportPip = prefs.getBoolean("support_pip_mode", true)
        val pipPackages = prefs.getString("pip_allowed_packages", "com.google.android.youtube,com.google.android.apps.maps,com.vietmap.s1,com.vietmap.live,com.navitel,com.spotify.music,com.zing.mp3") ?: "com.google.android.youtube,com.google.android.apps.maps,com.vietmap.s1,com.vietmap.live,com.navitel,com.spotify.music,com.zing.mp3"

        val autoHide = prefs.getBoolean("auto_hide_when_not_found", false)
        val keywords = prefs.getString("target_keywords", "Lịch, Âm, YouTube, Facebook, Messenger") ?: "Lịch, Âm, YouTube, Facebook, Messenger"
        val exact = prefs.getBoolean("exact_match", true)
        val autoCheck = prefs.getBoolean("auto_check_update", true)
        val customUrl = prefs.getString("custom_update_url", "") ?: ""

        return FloatingSettings(
            isEnabled = enabled,
            textColorHex = color,
            textFormat = format,
            bgStyle = bg,
            sizeOption = size,
            fontSizeSp = fontSp,
            posX = posX,
            posY = posY,
            lockPosition = lock,
            displayMode = displayMode,
            customHomePackages = customHomePackages,
            supportPipMode = supportPip,
            pipAllowedPackages = pipPackages,
            autoHideWhenTextNotFound = autoHide,
            targetKeywords = keywords,
            exactMatch = exact,
            autoCheckUpdate = autoCheck,
            customUpdateUrl = customUrl
        )
    }

    fun updateAutoCheckUpdate(enabled: Boolean) {
        prefs.edit().putBoolean("auto_check_update", enabled).apply()
        _settingsState.value = _settingsState.value.copy(autoCheckUpdate = enabled)
    }

    fun updateCustomUpdateUrl(url: String) {
        prefs.edit().putString("custom_update_url", url).apply()
        _settingsState.value = _settingsState.value.copy(customUpdateUrl = url)
    }

    fun updateSupportPipMode(enabled: Boolean) {
        prefs.edit().putBoolean("support_pip_mode", enabled).apply()
        _settingsState.value = _settingsState.value.copy(supportPipMode = enabled)
    }

    fun updatePipAllowedPackages(packages: String) {
        prefs.edit().putString("pip_allowed_packages", packages).apply()
        _settingsState.value = _settingsState.value.copy(pipAllowedPackages = packages)
    }

    fun updateDisplayMode(mode: DisplayModeOption) {
        prefs.edit().putString("display_mode_id", mode.id).apply()
        _settingsState.value = _settingsState.value.copy(displayMode = mode)
    }

    fun updateCustomHomePackages(packages: String) {
        prefs.edit().putString("custom_home_packages", packages).apply()
        _settingsState.value = _settingsState.value.copy(customHomePackages = packages)
    }

    fun updateExactMatch(exact: Boolean) {
        prefs.edit().putBoolean("exact_match", exact).apply()
        _settingsState.value = _settingsState.value.copy(exactMatch = exact)
    }

    fun updateAutoHideWhenTextNotFound(enabled: Boolean) {
        prefs.edit().putBoolean("auto_hide_when_not_found", enabled).apply()
        _settingsState.value = _settingsState.value.copy(autoHideWhenTextNotFound = enabled)
    }

    fun updateTargetKeywords(keywords: String) {
        prefs.edit().putString("target_keywords", keywords).apply()
        _settingsState.value = _settingsState.value.copy(targetKeywords = keywords)
    }

    fun updateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_enabled", enabled).apply()
        _settingsState.value = _settingsState.value.copy(isEnabled = enabled)
    }

    fun updateTextColor(hexColor: String) {
        prefs.edit().putString("text_color_hex", hexColor).apply()
        _settingsState.value = _settingsState.value.copy(textColorHex = hexColor)
    }

    fun updateFormat(format: TextFormatOption) {
        prefs.edit().putString("text_format_id", format.id).apply()
        _settingsState.value = _settingsState.value.copy(textFormat = format)
    }

    fun updateBgStyle(bg: BackgroundStyleOption) {
        prefs.edit().putString("bg_style_id", bg.id).apply()
        _settingsState.value = _settingsState.value.copy(bgStyle = bg)
    }

    fun updateSize(size: BadgeSizeOption) {
        prefs.edit().putString("size_option_id", size.id)
            .putInt("font_size_sp", size.fontSp)
            .apply()
        _settingsState.value = _settingsState.value.copy(sizeOption = size, fontSizeSp = size.fontSp)
    }

    fun updateFontSize(sp: Int) {
        prefs.edit().putInt("font_size_sp", sp).apply()
        _settingsState.value = _settingsState.value.copy(fontSizeSp = sp)
    }

    fun updatePosition(x: Int, y: Int) {
        prefs.edit().putInt("pos_x", x).putInt("pos_y", y).apply()
        _settingsState.value = _settingsState.value.copy(posX = x, posY = y)
    }

    fun updateLockPosition(locked: Boolean) {
        prefs.edit().putBoolean("lock_pos", locked).apply()
        _settingsState.value = _settingsState.value.copy(lockPosition = locked)
    }
}
