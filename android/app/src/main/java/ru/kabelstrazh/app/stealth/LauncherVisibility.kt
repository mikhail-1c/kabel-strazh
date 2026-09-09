package ru.kabelstrazh.app.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherVisibility {
    private const val PLAIN = "ru.kabelstrazh.app.LauncherPlain"
    private const val STEALTH = "ru.kabelstrazh.app.LauncherStealth"

    fun apply(context: Context, stealth: Boolean, hideIcon: Boolean) {
        val pm = context.packageManager
        when {
            hideIcon -> {
                set(pm, context, PLAIN, false)
                set(pm, context, STEALTH, false)
            }
            stealth -> {
                set(pm, context, PLAIN, false)
                set(pm, context, STEALTH, true)
            }
            else -> {
                set(pm, context, PLAIN, true)
                set(pm, context, STEALTH, false)
            }
        }
    }

    private fun set(pm: PackageManager, context: Context, className: String, enabled: Boolean) {
        pm.setComponentEnabledSetting(
            ComponentName(context, className),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
