package ru.kabelstrazh.app.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.kabelstrazh.app.KabelStrazhApp
import ru.kabelstrazh.app.data.GuardStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val store = (context.applicationContext as? KabelStrazhApp)?.store ?: GuardStore(context)
                GuardRuntime.sync(context, store.currentSettings())
            } finally {
                pending.finish()
            }
        }
    }
}
