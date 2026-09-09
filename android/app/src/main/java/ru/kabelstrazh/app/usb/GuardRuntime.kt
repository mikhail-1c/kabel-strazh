package ru.kabelstrazh.app.usb

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import ru.kabelstrazh.app.domain.GuardSettings
import ru.kabelstrazh.app.policy.UsbPolicy
import ru.kabelstrazh.app.stealth.LauncherVisibility

object GuardRuntime {
    fun sync(context: Context, settings: GuardSettings) {
        val app = context.applicationContext
        LauncherVisibility.apply(app, settings.stealthMode, settings.hideLauncherIcon)
        val intent = Intent(app, UsbGuardService::class.java)
        if (settings.armed) {
            app.startForegroundService(intent)
        } else {
            app.stopService(intent)
            app.getSystemService(NotificationManager::class.java).cancelAll()
            val policy = UsbPolicy(app)
            if (policy.isDeviceOwner()) {
                policy.unlockData()
            }
        }
    }
}
