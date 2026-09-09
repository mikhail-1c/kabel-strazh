package ru.kabelstrazh.app.usb

import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.kabelstrazh.app.KabelStrazhApp
import ru.kabelstrazh.app.domain.AllowWindow
import ru.kabelstrazh.app.domain.GuardStatus
import ru.kabelstrazh.app.domain.GuardUiState
import ru.kabelstrazh.app.domain.JournalKind
import ru.kabelstrazh.app.domain.UsbSnapshot
import ru.kabelstrazh.app.policy.UsbPolicy

class UsbGuardService : LifecycleService() {
    private val store by lazy { (application as KabelStrazhApp).store }
    private val policy by lazy { UsbPolicy(this) }
    private var lastConnected: Boolean? = null
    private var lastData: Boolean? = null
    private var lastStatus: GuardStatus? = null
    private var expireJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        startForeground(
            Notifications.ID_FOREGROUND,
            Notifications.guard(this, GuardStatus.Idle, "Страж запущен"),
        )
        observeUsb()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun observeUsb() {
        lifecycleScope.launch {
            combine(
                UsbMonitor.snapshots(this@UsbGuardService),
                store.allowWindow,
                store.policyEnforced,
            ) { snapshot, allow, policyOn ->
                Triple(snapshot, allow, policyOn)
            }.collectLatest { (snapshot, allow, policyOn) ->
                val now = System.currentTimeMillis()
                val state = GuardUiState(
                    snapshot = snapshot,
                    allow = allow,
                    nowMs = now,
                    deviceOwner = policy.isDeviceOwner(),
                    policyEnforced = policyOn,
                )
                applyPolicy(state)
                rememberTransitions(snapshot, allow, now)
                updateNotification(state)
                if (state.status == GuardStatus.DataLeak && lastStatus != GuardStatus.DataLeak) {
                    raiseLeak(snapshot)
                }
                lastStatus = state.status
                watchExpiry(allow)
            }
        }
    }

    private fun applyPolicy(state: GuardUiState) {
        if (!state.deviceOwner || !state.policyEnforced) return
        if (state.allow.isActive(state.nowMs)) {
            policy.unlockData()
        } else {
            policy.lockData()
        }
    }

    private suspend fun rememberTransitions(snapshot: UsbSnapshot, allow: AllowWindow, now: Long) {
        val connected = snapshot.connected
        if (lastConnected != connected) {
            if (connected) {
                store.append(JournalKind.Plugged, "Кабель подключён")
                if (!snapshot.dataExposed) {
                    store.append(JournalKind.ChargeOnly, "Режим: только заряд")
                }
            } else if (lastConnected == true) {
                store.append(JournalKind.Unplugged, "Кабель отключён")
            }
        }
        lastConnected = connected

        val data = snapshot.dataExposed
        if (lastData != data && data) {
            val allowed = allow.isActive(now)
            store.append(
                if (allowed) JournalKind.DataOpened else JournalKind.DataOpened,
                "Каналы: ${UsbMonitor.dataChannels(snapshot)}" + if (allowed) " (окно открыто)" else " без разрешения",
            )
        }
        lastData = data
    }

    private fun updateNotification(state: GuardUiState) {
        val manager = getSystemService(NotificationManager::class.java)
        val remain = state.allow.remainingMs(state.nowMs) / 1000
        val detail = when (state.status) {
            GuardStatus.Idle -> "Жду кабель"
            GuardStatus.ChargeOnly -> "Данные закрыты"
            GuardStatus.Allowed -> "Осталось ${remain}с"
            GuardStatus.DataLeak -> "MTP/ADB/PTP без вашего окна"
        }
        manager.notify(Notifications.ID_FOREGROUND, Notifications.guard(this, state.status, detail))
    }

    private fun raiseLeak(snapshot: UsbSnapshot) {
        val detail = "Открыто: ${UsbMonitor.dataChannels(snapshot)}"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Notifications.ID_ALERT, Notifications.leakAlert(this, detail))
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80, 80, 240), -1))
    }

    private fun watchExpiry(allow: AllowWindow) {
        expireJob?.cancel()
        val left = allow.remainingMs(System.currentTimeMillis())
        if (left <= 0L) return
        expireJob = lifecycleScope.launch {
            delay(left)
            store.clearAllow()
            store.append(JournalKind.AllowExpired, "Окно доступа закрыто")
            if (policy.isDeviceOwner()) {
                policy.lockData()
            }
        }
    }
}
