package com.example.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

object NavBarDetector {

    /**
     * Check whether the Navigation Bar is currently visible on the screen.
     * Returns true when the system navigation bar / insets are present.
     * Returns false when an application enters immersive / full-screen mode.
     */
    fun isNavBarVisible(view: View?, context: Context): Boolean {
        // 1. Check via View's RootWindowInsets if view is attached
        if (view != null && view.isAttachedToWindow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val rootInsets = view.rootWindowInsets
                if (rootInsets != null) {
                    val isNavVisible = rootInsets.isVisible(WindowInsets.Type.navigationBars())
                    val navInsets = rootInsets.getInsets(WindowInsets.Type.navigationBars())
                    val hasNavSize = (navInsets.bottom > 0 || navInsets.left > 0 || navInsets.right > 0 || navInsets.top > 0)
                    
                    if (isNavVisible && hasNavSize) {
                        return true
                    }
                    if (!isNavVisible) {
                        return false
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val rootInsets = view.rootWindowInsets
                if (rootInsets != null) {
                    @Suppress("DEPRECATION")
                    val bottom = rootInsets.systemWindowInsetBottom
                    @Suppress("DEPRECATION")
                    val left = rootInsets.systemWindowInsetLeft
                    @Suppress("DEPRECATION")
                    val right = rootInsets.systemWindowInsetRight
                    return (bottom > 0 || left > 0 || right > 0)
                }
            }
        }

        // 2. Fallback: Compare real screen size vs usable display size
        return checkDisplayHasNavBar(context)
    }

    private fun checkDisplayHasNavBar(context: Context): Boolean {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = windowManager.currentWindowMetrics
                val insets = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
                insets.bottom > 0 || insets.left > 0 || insets.right > 0 || insets.top > 0
            } else {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay ?: return true
                val realSize = Point()
                val usableSize = Point()
                @Suppress("DEPRECATION")
                display.getRealSize(realSize)
                @Suppress("DEPRECATION")
                display.getSize(usableSize)

                (realSize.x > usableSize.x) || (realSize.y > usableSize.y)
            }
        } catch (e: Exception) {
            true
        }
    }
}


