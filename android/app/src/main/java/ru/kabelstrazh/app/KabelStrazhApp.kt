package ru.kabelstrazh.app

import android.app.Application
import android.content.Intent
import ru.kabelstrazh.app.data.GuardStore
import ru.kabelstrazh.app.usb.UsbGuardService

class KabelStrazhApp : Application() {
    lateinit var store: GuardStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = GuardStore(this)
        startForegroundService(Intent(this, UsbGuardService::class.java))
    }
}
