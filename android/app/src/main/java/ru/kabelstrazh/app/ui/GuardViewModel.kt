package ru.kabelstrazh.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kabelstrazh.app.KabelStrazhApp
import ru.kabelstrazh.app.domain.AllowWindow
import ru.kabelstrazh.app.domain.ControlPreset
import ru.kabelstrazh.app.domain.GuardSettings
import ru.kabelstrazh.app.domain.GuardUiState
import ru.kabelstrazh.app.domain.JournalEvent
import ru.kabelstrazh.app.domain.JournalKind
import ru.kabelstrazh.app.domain.UsbSnapshot
import ru.kabelstrazh.app.policy.UsbPolicy
import ru.kabelstrazh.app.usb.UsbMonitor

class GuardViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as KabelStrazhApp).store
    private val policy = UsbPolicy(application)
    private val clock = MutableStateFlow(System.currentTimeMillis())

    private val persisted = combine(
        UsbMonitor.snapshots(application),
        store.allowWindow,
        store.settings,
        store.events,
    ) { snapshot: UsbSnapshot, allow: AllowWindow, settings: GuardSettings, events: List<JournalEvent> ->
        PersistSlice(snapshot, allow, settings, events)
    }

    val state = combine(persisted, clock) { slice, now ->
        GuardUiState(
            snapshot = slice.snapshot,
            allow = slice.allow,
            nowMs = now,
            deviceOwner = policy.isDeviceOwner(),
            settings = slice.settings,
            events = slice.events,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardUiState())

    init {
        viewModelScope.launch {
            while (true) {
                clock.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun grantAllow() {
        viewModelScope.launch {
            val settings = state.value.settings
            if (settings.forbidDataAllow || settings.allowMinutes <= 0) return@launch
            store.grantAllow(settings.allowMinutes * 60_000L)
            store.append(JournalKind.AllowGranted, "Окно на ${settings.allowMinutes} мин")
            if (policy.isDeviceOwner() && settings.policyEnforced) {
                policy.unlockData()
            }
        }
    }

    fun closeAllow() {
        viewModelScope.launch {
            store.clearAllow()
            store.append(JournalKind.AllowExpired, "Окно закрыто вручную")
            if (policy.isDeviceOwner()) {
                policy.lockData()
            }
        }
    }

    fun applyPreset(preset: ControlPreset) {
        viewModelScope.launch {
            store.applyPreset(preset)
            syncPolicy(GuardSettings.of(preset))
        }
    }

    fun updateSettings(transform: GuardSettings.() -> GuardSettings) {
        viewModelScope.launch {
            store.updateSettings(transform)
            syncPolicy(store.currentSettings())
        }
    }

    private fun syncPolicy(settings: GuardSettings) {
        if (!policy.isDeviceOwner()) return
        if (settings.policyEnforced && !state.value.allow.isActive(System.currentTimeMillis())) {
            policy.lockData()
        } else if (!settings.policyEnforced) {
            policy.unlockData()
        }
    }

    private data class PersistSlice(
        val snapshot: UsbSnapshot,
        val allow: AllowWindow,
        val settings: GuardSettings,
        val events: List<JournalEvent>,
    )
}
