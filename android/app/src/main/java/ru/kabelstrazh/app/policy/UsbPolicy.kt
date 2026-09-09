package ru.kabelstrazh.app.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

class UsbPolicy(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, GuardDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm?.isDeviceOwnerApp(context.packageName) == true

    fun isAdmin(): Boolean = dpm?.isAdminActive(admin) == true

    fun lockData() {
        val manager = dpm ?: return
        if (!isDeviceOwner()) return
        runCatching {
            manager.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            manager.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.setUsbDataSignalingEnabled(false) }
        }
    }

    fun unlockData() {
        val manager = dpm ?: return
        if (!isDeviceOwner()) return
        runCatching {
            manager.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            manager.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.setUsbDataSignalingEnabled(true) }
        }
    }
}
