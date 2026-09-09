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
import ru.kabelstrazh.app.domain.GuardSettings
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
                store.settings,
            ) { snapshot, allow, settings ->
                Triple(snapshot, allow, settings)
            }.collectLatest { (snapshot, allow, settings) ->
                val now = System.currentTimeMillis()
                val state = GuardUiState(
                    snapshot = snapshot,
                    allow = allow,
                    nowMs = now,
                    deviceOwner = policy.isDeviceOwner(),
                    settings = settings,
                )
                applyPolicy(state)
                rememberTransitions(snapshot, allow, settings, now)
                updateNotification(state)
                if (state.status == GuardStatus.DataLeak && lastStatus != GuardStatus.DataLeak) {
                    raiseLeak(snapshot, settings)
                } else if (
                    settings.alertOnAnyPlug &&
                    snapshot.connected &&
                    lastConnected != true &&
                    state.status != GuardStatus.DataLeak
                ) {
                    raisePlug(settings)
                }
                lastStatus = state.status
                watchExpiry(allow)
            }
        }
    }

    private fun applyPolicy(state: GuardUiState) {
        if (!state.deviceOwner || !state.policyEnforced) return
        if (state.allow.isActive(state.nowMs) && state.canGrantAllow) {
            policy.unlockData()
        } else {
            policy.lockData()
        }
    }

    private suspend fun rememberTransitions(
        snapshot: UsbSnapshot,
        allow: AllowWindow,
        settings: GuardSettings,
        now: Long,
    ) {
        val connected = snapshot.connected
        if (lastConnected != connected) {
            if (connected) {
                store.append(JournalKind.Plugged, "Кабель подключён")
                if (!settings.seesData(snapshot)) {
                    store.append(JournalKind.ChargeOnly, "Режим: только заряд")
                }
            } else if (lastConnected == true) {
                store.append(JournalKind.Unplugged, "Кабель отключён")
                if (settings.closeWindowOnUnplug && allow.isActive(now)) {
                    store.clearAllow()
                    store.append(JournalKind.AllowExpired, "Окно закрыто: кабель вынули")
                    if (policy.isDeviceOwner()) {
                        policy.lockData()
                    }
                }
            }
        }
        lastConnected = connected

        val data = settings.seesData(snapshot)
        if (lastData != data && data) {
            val allowed = allow.isActive(now) && !(snapshot.adb && settings.treatAdbAsCritical)
            store.append(
                JournalKind.DataOpened,
                "Каналы: ${UsbMonitor.dataChannels(snapshot)}" + if (allowed) " (окно открыто)" else " без разрешения",
            )
        }
        lastData = data
    }

    private fun updateNotification(state: GuardUiState) {
        val manager = getSystemService(NotificationManager::class.java)
        val remain = state.allow.remainingMs(state.nowMs) / 1000
        val detail = when (state.status) {
            GuardStatus.Idle -> "Режим ${presetShort(state.settings)}"
            GuardStatus.ChargeOnly -> "Данные закрыты"
            GuardStatus.Allowed -> "Осталось ${remain}с"
            GuardStatus.DataLeak -> "USB без вашего окна"
        }
        manager.notify(Notifications.ID_FOREGROUND, Notifications.guard(this, state.status, detail))
    }

    private fun raiseLeak(snapshot: UsbSnapshot, settings: GuardSettings) {
        val detail = "Открыто: ${UsbMonitor.dataChannels(snapshot)}"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            Notifications.ID_ALERT,
            Notifications.leakAlert(this, detail, settings.fullscreenOnLeak),
        )
        vibrateIfNeeded(settings)
    }

    private fun raisePlug(settings: GuardSettings) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Notifications.ID_PLUG, Notifications.plugAlert(this))
        vibrateIfNeeded(settings)
    }

    private fun vibrateIfNeeded(settings: GuardSettings) {
        if (!settings.vibrateOnAlert) return
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

    private fun presetShort(settings: GuardSettings): String = when (settings.preset) {
        ru.kabelstrazh.app.domain.ControlPreset.Balanced -> "обычный"
        ru.kabelstrazh.app.domain.ControlPreset.Strict -> "жёсткий"
        ru.kabelstrazh.app.domain.ControlPreset.Lockdown -> "замок"
        ru.kabelstrazh.app.domain.ControlPreset.Custom -> "свой"
    }
}
