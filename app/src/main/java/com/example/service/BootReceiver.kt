package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.data.FloatingSettingsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Fast-path: read directly from device-protected storage without creating heavy objects
        val storageContext = FloatingSettingsRepository.getStorageContext(context)
        val prefs = storageContext.getSharedPreferences(FloatingSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("is_enabled", false)

        if (isEnabled && Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, FloatingLunarService::class.java).apply {
                putExtra("EXTRA_BOOT_START", true)
                putExtra("EXTRA_BOOT_ACTION", action)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

