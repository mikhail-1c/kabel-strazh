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
        store.allowMinutesFlow,
        store.policyEnforced,
        store.events,
    ) { snapshot: UsbSnapshot, allow: AllowWindow, minutes: Int, policyOn: Boolean, events: List<JournalEvent> ->
        PersistSlice(snapshot, allow, minutes, policyOn, events)
    }

    val state = combine(persisted, clock) { slice, now ->
        GuardUiState(
            snapshot = slice.snapshot,
            allow = slice.allow,
            nowMs = now,
            deviceOwner = policy.isDeviceOwner(),
            policyEnforced = slice.policyOn,
            events = slice.events,
            allowMinutes = slice.minutes,
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
            val minutes = state.value.allowMinutes
            store.grantAllow(minutes * 60_000L)
            store.append(JournalKind.AllowGranted, "Окно на $minutes мин")
            if (policy.isDeviceOwner() && state.value.policyEnforced) {
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

    fun setMinutes(minutes: Int) {
        viewModelScope.launch { store.setAllowMinutes(minutes) }
    }

    fun setPolicy(on: Boolean) {
        viewModelScope.launch {
            store.setPolicyEnforced(on)
            store.append(
                if (on) JournalKind.PolicyOn else JournalKind.PolicyOff,
                "Принудительная блокировка USB",
            )
            if (!policy.isDeviceOwner()) return@launch
            if (on && !state.value.allow.isActive(System.currentTimeMillis())) {
                policy.lockData()
            } else if (!on) {
                policy.unlockData()
            }
        }
    }

    private data class PersistSlice(
        val snapshot: UsbSnapshot,
        val allow: AllowWindow,
        val minutes: Int,
        val policyOn: Boolean,
        val events: List<JournalEvent>,
    )
}
