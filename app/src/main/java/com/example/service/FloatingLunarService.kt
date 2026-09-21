package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.calendar.LunarCalendarHelper
import com.example.data.BackgroundStyleOption
import com.example.data.BadgeSizeOption
import com.example.data.DisplayModeOption
import com.example.data.FloatingSettings
import com.example.data.FloatingSettingsRepository
import com.example.data.TextFormatOption
import com.example.util.HomeAppDetector
import com.example.util.NavBarDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingLunarService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var textView: TextView? = null
    private var containerLayout: LinearLayout? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var repository: FloatingSettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentSettings = FloatingSettings()
    private var detectionJob: Job? = null
    private var consecutiveShowCount: Int = 0
    private var consecutiveHideCount: Int = 0
    private val serviceCreatedTime = System.currentTimeMillis()

    // Receiver to update date when time changes (midnight)
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateTextContent()
        }
    }

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_TOGGLE_WIDGET_VISIBILITY") {
                val show = intent.getBooleanExtra("SHOW_WIDGET", true)
                val targetVis = if (show) View.VISIBLE else View.GONE
                if (floatingView?.visibility != targetVis) {
                    floatingView?.visibility = targetVis
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = FloatingSettingsRepository(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeChangeReceiver, intentFilter)

        instance = this

        val visFilter = IntentFilter("com.example.ACTION_TOGGLE_WIDGET_VISIBILITY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(visibilityReceiver, visFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(visibilityReceiver, visFilter)
        }

        // Fast-path: display floating view immediately during boot/service start
        currentSettings = repository.settingsState.value
        if (currentSettings.isEnabled && Settings.canDrawOverlays(this@FloatingLunarService)) {
            setupFloatingView()
        }

        serviceScope.launch {
            repository.settingsState.collectLatest { settings ->
                val prevSettings = currentSettings
                currentSettings = settings
                if (!settings.isEnabled || !Settings.canDrawOverlays(this@FloatingLunarService)) {
                    stopSelf()
                    return@collectLatest
                }

                if (floatingView == null) {
                    setupFloatingView()
                }

                when (settings.displayMode) {
                    DisplayModeOption.NAVBAR_VISIBILITY,
                    DisplayModeOption.HOME_OR_NAVBAR,
                    DisplayModeOption.HOME_ONLY -> {
                        startSmartDetectionLoop()
                    }
                    DisplayModeOption.ALWAYS_SHOW -> {
                        detectionJob?.cancel()
                        detectionJob = null
                        floatingView?.visibility = View.VISIBLE
                    }
                }

                updateFloatingViewStyle(settings)
            }
        }
    }

    private fun startSmartDetectionLoop() {
        if (detectionJob?.isActive == true) return
        detectionJob?.cancel()
        detectionJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                checkAndUpdateVisibility()
                delay(100)
            }
        }
    }

    private fun checkAndUpdateVisibility() {
        val now = System.currentTimeMillis()
        val isBootWarmup = (now - serviceCreatedTime) < 15_000L

        val shouldShow = when (currentSettings.displayMode) {
            DisplayModeOption.NAVBAR_VISIBILITY -> {
                NavBarDetector.isNavBarVisible(floatingView, this@FloatingLunarService)
            }
            DisplayModeOption.HOME_OR_NAVBAR,
            DisplayModeOption.HOME_ONLY -> {
                HomeAppDetector.isCurrentScreenHome(
                    context = this@FloatingLunarService,
                    customExtraPackages = currentSettings.customHomePackages,
                    supportPip = currentSettings.supportPipMode,
                    pipAllowedPackages = currentSettings.pipAllowedPackages
                )
            }
            DisplayModeOption.ALWAYS_SHOW -> true
        }

        if (shouldShow) {
            consecutiveHideCount = 0
            consecutiveShowCount++
            if (consecutiveShowCount >= 1 && floatingView?.visibility != View.VISIBLE) {
                floatingView?.post {
                    floatingView?.visibility = View.VISIBLE
                }
            }
        } else {
            // Keep visible during boot warmup so system background processes don't flicker or hide the widget
            if (isBootWarmup) {
                consecutiveHideCount = 0
                if (floatingView?.visibility != View.VISIBLE) {
                    floatingView?.post {
                        floatingView?.visibility = View.VISIBLE
                    }
                }
                return
            }

            consecutiveShowCount = 0
            consecutiveHideCount++
            // Debounce ~300ms to eliminate reload blips while smoothly hiding before fullscreen app window opens
            if (consecutiveHideCount >= 3 && floatingView?.visibility != View.GONE) {
                floatingView?.post {
                    floatingView?.visibility = View.GONE
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (floatingView == null) {
            setupFloatingView()
        } else {
            updateTextContent()
        }

        return START_STICKY
    }

    private fun setupFloatingView() {
        if (floatingView != null) return

        // Layout Params setup
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSettings.posX
            y = currentSettings.posY
        }

        // Programmatic Floating Container
        containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        }

        textView = TextView(this).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

        containerLayout?.addView(textView)
        floatingView = containerLayout

        // Drag and Drop & Touch Handling
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (currentSettings.lockPosition) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isClick = false
                        }
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        if (floatingView?.isAttachedToWindow == true) {
                            windowManager.updateViewLayout(floatingView, layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            // Tapping floating badge launches main activity
                            val launchIntent = Intent(this@FloatingLunarService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(launchIntent)
                        } else {
                            // Save updated position
                            repository.updatePosition(layoutParams.x, layoutParams.y)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Pre-apply visual styles and text content BEFORE adding to WindowManager
        applyStylesToContainerAndText(currentSettings)
        floatingView?.visibility = View.VISIBLE

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateFloatingViewStyle(settings: FloatingSettings) {
        if (floatingView == null || textView == null || containerLayout == null) return

        applyStylesToContainerAndText(settings)

        // Position update if changed from settings
        layoutParams.x = settings.posX
        layoutParams.y = settings.posY
        if (floatingView?.isAttachedToWindow == true) {
            try {
                windowManager.updateViewLayout(floatingView, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyStylesToContainerAndText(settings: FloatingSettings) {
        if (floatingView == null || textView == null || containerLayout == null) return

        // Text Color (Default Vibrant Red)
        val colorInt = try {
            Color.parseColor(settings.textColorHex)
        } catch (e: Exception) {
            Color.parseColor("#FF1744")
        }
        textView?.setTextColor(colorInt)

        // Font Size & Container Dimensions
        val fontSp = settings.fontSizeSp
        textView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat())

        val padH = when (settings.sizeOption) {
            BadgeSizeOption.MINI_20X50 -> dpToPx(6)
            BadgeSizeOption.MEDIUM_STANDARD -> dpToPx(10)
            BadgeSizeOption.LARGE_ACCESSIBLE -> dpToPx(12)
        }
        val padV = when (settings.sizeOption) {
            BadgeSizeOption.MINI_20X50 -> dpToPx(3)
            BadgeSizeOption.MEDIUM_STANDARD -> dpToPx(5)
            BadgeSizeOption.LARGE_ACCESSIBLE -> dpToPx(7)
        }
        containerLayout?.setPadding(padH, padV, padH, padV)

        // Background Styling
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()

            when (settings.bgStyle) {
                BackgroundStyleOption.TRANSLUCENT_DARK -> {
                    setColor(Color.parseColor("#E6121214")) // 90% dark pill
                    setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
                }
                BackgroundStyleOption.GLASS_BORDER -> {
                    setColor(Color.parseColor("#AA1A0F15"))
                    setStroke(dpToPx(1), Color.parseColor("#80FF1744")) // Red glass glow
                }
                BackgroundStyleOption.PURE_DARK -> {
                    setColor(Color.BLACK)
                    setStroke(dpToPx(1), Color.parseColor("#444444"))
                }
                BackgroundStyleOption.TEXT_ONLY -> {
                    setColor(Color.TRANSPARENT)
                    setStroke(0, Color.TRANSPARENT)
                }
            }
        }
        containerLayout?.background = drawable

        // Content Update
        updateTextContent()
    }

    private fun updateTextContent() {
        val lunarDate = LunarCalendarHelper.getTodayLunar()
        val text = when (currentSettings.textFormat) {
            TextFormatOption.SHORT -> lunarDate.toShortString(showAmLabel = false, padZero = false) // "18/6"
            TextFormatOption.PAD_ZERO -> lunarDate.toShortString(showAmLabel = false, padZero = true) // "18/06"
            TextFormatOption.SHORT_AM -> lunarDate.toShortString(showAmLabel = true, padZero = false) // "18/6 Âm"
            TextFormatOption.FULL_TEXT -> "Ngày ${lunarDate.day} Thg ${lunarDate.month}"
        }
        textView?.text = text
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lịch Âm Nổi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị thông tin ngày tháng Âm lịch nổi trên màn hình"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lunarToday = LunarCalendarHelper.getTodayLunar()
        val textContent = "Hôm nay: ${lunarToday.toShortString(showAmLabel = true)} (${lunarToday.yearCanChi})"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lịch Âm Nổi Đang Chạy")
            .setContentText(textContent)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionJob?.cancel()
        detectionJob = null
        try {
            unregisterReceiver(timeChangeReceiver)
            unregisterReceiver(visibilityReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        const val CHANNEL_ID = "floating_lunar_channel"
        const val NOTIFICATION_ID = 20261

        @Volatile
        var instance: FloatingLunarService? = null

        fun setVisibilityInstant(show: Boolean) {
            instance?.let { service ->
                val targetVis = if (show) View.VISIBLE else View.GONE
                if (service.floatingView?.visibility != targetVis) {
                    service.floatingView?.post {
                        service.floatingView?.visibility = targetVis
                    }
                }
            }
        }
    }
}
