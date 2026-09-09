package ru.kabelstrazh.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.kabelstrazh.app.data.GuardStore
import ru.kabelstrazh.app.usb.GuardRuntime

class KabelStrazhApp : Application() {
    lateinit var store: GuardStore
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        store = GuardStore(this)
        scope.launch {
            GuardRuntime.sync(this@KabelStrazhApp, store.currentSettings())
        }
    }
}
