package ru.kabelstrazh.app.policy

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class GuardDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Кабель-страж получил права администратора", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Права администратора сняты — блокировка USB ослаблена", Toast.LENGTH_LONG).show()
    }
}
