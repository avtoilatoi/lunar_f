package com.example.util

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isHomeApp: Boolean = false,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
)

object HomeAppDetector {

    private val cachedHomePackages = mutableSetOf<String>()
    private var lastHomeQueryTime: Long = 0L
    private val appStartTimestamp = System.currentTimeMillis()

    // System overlay, framework and input packages that should NOT change the current Home/App state
    private val SYSTEM_OVERLAY_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.nexuslauncher.search",
        "com.google.android.inputmethod.latin",
        "com.google.android.inputmethod.pinyin",
        "com.google.android.inputmethod.vietnamese",
        "com.vng.inputmethod.labankey",
        "com.sohu.inputmethod.sogou",
        "com.touchtype.swiftkey",
        "com.android.keyguard",
        "com.android.wallpaper.livepicker",
        "com.android.wallpaperpicker"
    )

    // Known system utility apps that must NEVER be treated as Home launchers
    private val KNOWN_NON_LAUNCHER_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.incallui",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.android.gallery3d",
        "com.google.android.apps.photos",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.google.android.apps.nbu.files",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.android.calculator2",
        "com.google.android.calculator",
        "com.android.vending",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    private val KNOWN_LAUNCHER_PREFIXES_OR_EXACT = listOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.huawei.android.launcher",
        "com.vivo.launcher",
        "com.oneplus.launcher",
        "com.android.car.carlauncher",
        "com.autonavi.carlauncher",
        "com.carlinkit.autokit",
        "com.zjinnova.zlink",
        "com.zjinnova.zlink5",
        "com.txznet.adapter",
        "com.zestech.launcher",
        "com.vietmap.launcher",
        "com.gotech.launcher",
        "com.icar.launcher",
        "com.icar.gofa",
        "com.icar.elliview",
        "com.bravigo.launcher",
        "com.owin.launcher",
        "com.carwebguru",
        "com.altercars.launcher",
        "com.studioruma.carlauncher",
        "com.syu.ms",
        "com.syu.car",
        "com.syu.canbus",
        "com.syu.dudu",
        "com.fyt.launcher",
        "com.fyt.car",
        "com.ts.launcher",
        "com.ts.main",
        "com.ksw.carlauncher",
        "com.dudu.launcher",
        "com.dudu.car",
        "com.mstar.launcher",
        "com.microntek.controlsettings",
        "com.tbox.launcher",
        "com.xyauto.launcher",
        "com.topway.launcher",
        "net.easyconn.carman",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.teslacoilsw.launcher",
        "ch.deletescape.lawnchair"
    )

    // Running state caches
    @Volatile
    private var lastKnownForegroundPackage: String? = null
    @Volatile
    private var lastKnownIsHome: Boolean = true

    /**
     * Check whether PACKAGE_USAGE_STATS permission is granted
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Check if a specific package is a home launcher
     */
    fun isPackageLauncher(pkg: String?, homePackages: Set<String>): Boolean {
        if (pkg.isNullOrBlank()) return false
        val lower = pkg.lowercase().trim()

        // 1. Explicitly reject system utilities and non-launcher apps
        if (KNOWN_NON_LAUNCHER_PACKAGES.contains(lower) ||
            KNOWN_NON_LAUNCHER_PACKAGES.any { lower == it.lowercase() }
        ) {
            return false
        }
        if (lower.contains("settings") || lower.contains("dialer") || lower.contains("incallui") ||
            lower.contains(".mms") || lower.contains("messaging") || lower.contains("contacts") ||
            lower.contains("camera") || lower.contains("gallery") || lower.contains("calculator") ||
            lower.contains("deskclock") || lower.contains("telecom") || lower.contains("stk")
        ) {
            return false
        }

        // 2. Direct match against detected Home packages
        if (homePackages.contains(pkg) || homePackages.any { it.equals(pkg, ignoreCase = true) }) {
            return true
        }

        // 3. Known launcher prefixes or exact matches
        if (KNOWN_LAUNCHER_PREFIXES_OR_EXACT.any { lower.startsWith(it) || lower == it }) {
            return true
        }

        // 4. Ends with launcher / home / carlauncher
        if (lower.endsWith(".launcher") || lower.endsWith(".home") || lower.endsWith(".carlauncher")) {
            return true
        }

        return false
    }

    /**
     * Fetch all installed applications for the Launcher App Picker
     */
    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val homePackages = getHomePackages(context)
        val list = mutableListOf<InstalledAppInfo>()

        try {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in packages) {
                // Ignore our own app
                if (app.packageName == context.packageName) continue

                val isHome = isPackageLauncher(app.packageName, homePackages)
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val appName = try {
                    pm.getApplicationLabel(app).toString()
                } catch (e: Exception) {
                    app.packageName
                }

                val icon = try {
                    pm.getApplicationIcon(app)
                } catch (e: Exception) {
                    null
                }

                list.add(
                    InstalledAppInfo(
                        packageName = app.packageName,
                        appName = appName,
                        isHomeApp = isHome,
                        isSystemApp = isSystem,
                        icon = icon
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort: Home apps first, then alphabetical by name
        return list.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isHomeApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Query all installed launcher / home packages on the device
     */
    fun getHomePackages(context: Context, customExtraPackages: String = ""): Set<String> {
        val now = System.currentTimeMillis()
        if (cachedHomePackages.isNotEmpty() && now - lastHomeQueryTime < 10_000L && customExtraPackages.isBlank()) {
            return cachedHomePackages
        }

        val homePackages = mutableSetOf<String>()

        // 1. Query standard HOME and CAR DOCK intent handlers (Do NOT query CATEGORY_DEFAULT as it matches all standard apps)
        val homeCategories = listOf(
            Intent.CATEGORY_HOME,
            Intent.CATEGORY_CAR_DOCK,
            Intent.CATEGORY_DESK_DOCK
        )

        for (category in homeCategories) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
                for (info in resolveInfos) {
                    info.activityInfo?.packageName?.let { pkg ->
                        if (!KNOWN_NON_LAUNCHER_PACKAGES.contains(pkg.lowercase())) {
                            homePackages.add(pkg)
                        }
                    }
                }

                val defaultHome = context.packageManager.resolveActivity(intent, 0)
                defaultHome?.activityInfo?.packageName?.let { pkg ->
                    if (!KNOWN_NON_LAUNCHER_PACKAGES.contains(pkg.lowercase())) {
                        homePackages.add(pkg)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 2. Comprehensive Automotive & Android Launcher Packages
        homePackages.addAll(KNOWN_LAUNCHER_PREFIXES_OR_EXACT)

        // 3. User configured extra packages
        if (customExtraPackages.isNotBlank()) {
            customExtraPackages.split(",", ";", " ", "\n").forEach { pkg ->
                val trimmed = pkg.trim()
                if (trimmed.isNotEmpty()) {
                    homePackages.add(trimmed)
                }
            }
        }

        cachedHomePackages.clear()
        cachedHomePackages.addAll(homePackages)
        lastHomeQueryTime = now
        return homePackages
    }

    /**
     * Get the single latest active / top foreground package name
     */
    fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val time = System.currentTimeMillis()

        // 1. Check UsageEvents in the last 10 seconds for the most recent RESUMED event
        if (usageStatsManager != null) {
            try {
                val usageEvents = usageStatsManager.queryEvents(time - 15_000L, time + 500L)
                val event = UsageEvents.Event()
                var latestFgPackage: String? = null
                var latestFgTime: Long = 0L

                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    val pkg = event.packageName
                    if (!pkg.isNullOrEmpty() && !SYSTEM_OVERLAY_PACKAGES.contains(pkg)) {
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                            event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                        ) {
                            if (event.timeStamp >= latestFgTime) {
                                latestFgPackage = pkg
                                latestFgTime = event.timeStamp
                            }
                        }
                    }
                }

                if (!latestFgPackage.isNullOrEmpty()) {
                    lastKnownForegroundPackage = latestFgPackage
                    return latestFgPackage
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Check ActivityManager top running foreground process
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val fgProcess = am?.runningAppProcesses?.find {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        !SYSTEM_OVERLAY_PACKAGES.contains(it.pkgList?.firstOrNull() ?: it.processName)
            }
            val processPkg = fgProcess?.pkgList?.firstOrNull() ?: fgProcess?.processName
            if (!processPkg.isNullOrEmpty()) {
                lastKnownForegroundPackage = processPkg
                return processPkg
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return lastKnownForegroundPackage
    }

    /**
     * Check whether the user is currently on the Home / Launcher screen or Car Dashboard with PIP.
     * Accurately distinguishes between PIP on Home vs opening an app in FULLSCREEN mode,
     * and completely prevents flickering when the launcher package reloads or restarts.
     */
    fun isCurrentScreenHome(
        context: Context,
        customExtraPackages: String = "",
        supportPip: Boolean = true,
        pipAllowedPackages: String = ""
    ): Boolean {
        val homePackages = getHomePackages(context, customExtraPackages)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val time = System.currentTimeMillis()

        // 1. Check ActivityManager process list for any active / visible launcher process
        var isLauncherProcessAlive = false
        var isLauncherProcessVisible = false
        var topForegroundProcessPackage: String? = null

        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = am?.runningAppProcesses
            processes?.forEach { proc ->
                val isProcHome = proc.pkgList?.any { isPackageLauncher(it, homePackages) } == true ||
                        isPackageLauncher(proc.processName, homePackages)

                if (isProcHome) {
                    isLauncherProcessAlive = true
                    if (proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        isLauncherProcessVisible = true
                    }
                }

                if (proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    val pkg = proc.pkgList?.firstOrNull() ?: proc.processName
                    if (!pkg.isNullOrEmpty() && !SYSTEM_OVERLAY_PACKAGES.contains(pkg) && pkg != context.packageName) {
                        topForegroundProcessPackage = pkg
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Check UsageEvents timeline
        if (usageStatsManager != null) {
            try {
                val usageEvents = usageStatsManager.queryEvents(time - 60_000L, time + 500L)
                val event = UsageEvents.Event()

                var lastHomeResumedTime: Long = 0L
                var lastHomeStoppedTime: Long = 0L
                var lastHomePackage: String? = null

                var lastNonHomeResumedTime: Long = 0L
                var lastNonHomeStoppedTime: Long = 0L
                var lastNonHomePackage: String? = null

                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    val pkg = event.packageName
                    if (pkg.isNullOrEmpty() || pkg == context.packageName || SYSTEM_OVERLAY_PACKAGES.contains(pkg)) {
                        continue
                    }

                    val isHome = isPackageLauncher(pkg, homePackages)

                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            if (isHome) {
                                if (event.timeStamp >= lastHomeResumedTime) {
                                    lastHomeResumedTime = event.timeStamp
                                    lastHomePackage = pkg
                                }
                            } else {
                                if (event.timeStamp >= lastNonHomeResumedTime) {
                                    lastNonHomeResumedTime = event.timeStamp
                                    lastNonHomePackage = pkg
                                }
                            }
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            if (isHome) {
                                if (event.timeStamp >= lastHomeStoppedTime) {
                                    lastHomeStoppedTime = event.timeStamp
                                }
                            } else {
                                if (event.timeStamp >= lastNonHomeStoppedTime) {
                                    lastNonHomeStoppedTime = event.timeStamp
                                }
                            }
                        }
                    }
                }

                // If supportPip is ON:
                // If the launcher has been opened and has NOT been stopped/paused by a fullscreen app,
                // OR if a launcher process is alive/visible on screen, we remain on Home.
                if (supportPip) {
                    val homeHasBeenResumed = lastHomeResumedTime > 0L
                    val homeNotStopped = lastHomeStoppedTime <= lastHomeResumedTime
                    val launcherStillPresent = isLauncherProcessVisible || isLauncherProcessAlive

                    if (homeHasBeenResumed && (homeNotStopped || launcherStillPresent)) {
                        lastKnownForegroundPackage = if (lastHomeResumedTime >= lastNonHomeResumedTime) lastHomePackage else lastNonHomePackage
                        lastKnownIsHome = true
                        return true
                    }
                }

                // If no non-home app has been resumed (e.g. device just booted or launcher is active)
                if (lastNonHomeResumedTime == 0L) {
                    lastKnownForegroundPackage = lastHomePackage
                    lastKnownIsHome = true
                    return true
                }

                // Standard non-PIP checks:
                if (lastHomeResumedTime >= lastNonHomeResumedTime && lastHomeResumedTime > 0L) {
                    lastKnownForegroundPackage = lastHomePackage
                    lastKnownIsHome = true
                    return true
                }

                if (lastNonHomeResumedTime > lastHomeResumedTime) {
                    lastKnownForegroundPackage = lastNonHomePackage
                    lastKnownIsHome = false
                    return false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback using ActivityManager top process
        if (topForegroundProcessPackage != null) {
            val isHome = isPackageLauncher(topForegroundProcessPackage, homePackages)
            lastKnownForegroundPackage = topForegroundProcessPackage
            lastKnownIsHome = isHome || (supportPip && (isLauncherProcessVisible || isLauncherProcessAlive))
            return lastKnownIsHome
        }

        return lastKnownIsHome
    }
}
