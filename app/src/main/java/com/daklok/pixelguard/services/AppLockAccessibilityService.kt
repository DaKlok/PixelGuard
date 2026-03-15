package com.daklok.pixelguard.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import androidx.core.content.ContextCompat
import com.daklok.pixelguard.data.AppLockPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appLockPreferences: AppLockPreferences
    
    private var lastLockedApp: String? = null
    private val temporarilyUnlockedApps = mutableSetOf<String>()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.daklok.pixelguard.UNLOCK_APP") {
                val packageName = intent.getStringExtra("PACKAGE_NAME")
                packageName?.let {
                    Log.d("AppLockService", "Temporarily unlocking: $it")
                    temporarilyUnlockedApps.add(it)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        appLockPreferences = AppLockPreferences(applicationContext)
        val filter = IntentFilter("com.daklok.pixelguard.UNLOCK_APP")
        // Use ContextCompat for safe receiver registration across API levels
        ContextCompat.registerReceiver(
            applicationContext,
            unlockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d("AppLockService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Ignore system UI, the locker itself, and common system/input packages
        if (packageName == "com.android.systemui" || 
            packageName == "android" || 
            packageName == applicationContext.packageName ||
            packageName.contains("inputmethod") || 
            packageName.contains("keyboard")) {
            return
        }

        serviceScope.launch {
            val lockedApps = appLockPreferences.lockedApps.first()
            
            if (lockedApps.contains(packageName)) {
                if (!temporarilyUnlockedApps.contains(packageName)) {
                    if (packageName != lastLockedApp) {
                        Log.d("AppLockService", "Locking app: $packageName")
                        lastLockedApp = packageName
                        
                        val intent = Intent().apply {
                            setClassName(applicationContext, "com.daklok.pixelguard.ui.LockActivity")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            putExtra("PACKAGE_NAME", packageName)
                        }
                        startActivity(intent)
                    }
                }
            } else {
                lastLockedApp = null
                if (temporarilyUnlockedApps.isNotEmpty()) {
                    Log.d("AppLockService", "Leaving locked apps, clearing temp list")
                    temporarilyUnlockedApps.clear()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            Log.e("AppLockService", "Error unregistering receiver", e)
        }
    }

    override fun onInterrupt() {}
}
