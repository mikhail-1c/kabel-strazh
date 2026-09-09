package ru.kabelstrazh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GuardUiStateTest {
    @Test
    fun idleWhenUnplugged() {
        val state = GuardUiState(snapshot = UsbSnapshot(connected = false), nowMs = 1_000)
        assertEquals(GuardStatus.Idle, state.status)
    }

    @Test
    fun chargeOnlyWhenCableWithoutData() {
        val state = GuardUiState(
            snapshot = UsbSnapshot(connected = true, charging = true),
            nowMs = 1_000,
        )
        assertEquals(GuardStatus.ChargeOnly, state.status)
    }

    @Test
    fun leakWhenMtpWithoutAllow() {
        val state = GuardUiState(
            snapshot = UsbSnapshot(connected = true, mtp = true),
            nowMs = 1_000,
        )
        assertEquals(GuardStatus.DataLeak, state.status)
    }

    @Test
    fun allowedWhileWindowActiveEvenWithMtp() {
        val state = GuardUiState(
            snapshot = UsbSnapshot(connected = true, mtp = true),
            allow = AllowWindow(untilEpochMs = 5_000),
            nowMs = 1_000,
        )
        assertEquals(GuardStatus.Allowed, state.status)
    }

    @Test
    fun configuredUsbLeaksOnStrictDetection() {
        val state = GuardUiState(
            snapshot = UsbSnapshot(connected = true, configured = true),
            settings = GuardSettings(treatConfiguredAsData = true),
            nowMs = 1_000,
        )
        assertEquals(GuardStatus.DataLeak, state.status)
    }

    @Test
    fun adbLeaksEvenInsideAllowWindowWhenCritical() {
        val state = GuardUiState(
            snapshot = UsbSnapshot(connected = true, adb = true),
            allow = AllowWindow(untilEpochMs = 5_000),
            settings = GuardSettings(treatAdbAsCritical = true),
            nowMs = 1_000,
        )
        assertEquals(GuardStatus.DataLeak, state.status)
    }

    @Test
    fun lockdownHidesAllowButton() {
        val state = GuardUiState(settings = GuardSettings.of(ControlPreset.Lockdown))
        assertFalse(state.canGrantAllow)
    }
}
