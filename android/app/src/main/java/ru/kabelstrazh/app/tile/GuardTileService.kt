package ru.kabelstrazh.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.kabelstrazh.app.KabelStrazhApp
import ru.kabelstrazh.app.MainActivity
import ru.kabelstrazh.app.usb.GuardRuntime

class GuardTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        val armed = runCatching {
            runBlocking { (application as KabelStrazhApp).store.currentSettings().armed }
        }.getOrDefault(false)
        qsTile?.apply {
            state = if (armed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "USB"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (armed) "вкл" else "выкл"
            }
            updateTile()
        }
    }

    override fun onClick() {
        val app = application as KabelStrazhApp
        val armed = runCatching {
            runBlocking { app.store.currentSettings().armed }
        }.getOrDefault(false)
        if (armed) {
            openApp()
            return
        }
        scope.launch {
            app.store.setArmed(true)
            GuardRuntime.sync(app, app.store.currentSettings())
        }
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "вкл"
            }
            updateTile()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
