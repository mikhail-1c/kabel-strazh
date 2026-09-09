package ru.kabelstrazh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardSettingsTest {
    @Test
    fun lockdownForbidsDataWindow() {
        val lockdown = GuardSettings.of(ControlPreset.Lockdown)
        assertTrue(lockdown.forbidDataAllow)
        assertTrue(lockdown.alertOnAnyPlug)
        assertTrue(lockdown.treatAdbAsCritical)
        assertTrue(lockdown.treatConfiguredAsData)
    }

    @Test
    fun manualToggleMarksCustomUnlessMatchesPreset() {
        val custom = GuardSettings.of(ControlPreset.Balanced)
            .withManualChange { copy(alertOnAnyPlug = true) }
        assertEquals(ControlPreset.Custom, custom.preset)
    }

    @Test
    fun matchingStrictStaysStrict() {
        val matched = GuardSettings.of(ControlPreset.Balanced)
            .withManualChange { GuardSettings.of(ControlPreset.Strict).copy(preset = ControlPreset.Balanced) }
        assertEquals(ControlPreset.Strict, matched.preset)
    }

    @Test
    fun configuredBusCountsAsDataOnlyWhenAsked() {
        val snap = UsbSnapshot(connected = true, configured = true)
        assertFalse(GuardSettings().seesData(snap))
        assertTrue(GuardSettings(treatConfiguredAsData = true).seesData(snap))
    }
}
