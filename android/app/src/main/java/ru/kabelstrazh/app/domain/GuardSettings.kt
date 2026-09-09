package ru.kabelstrazh.app.domain

enum class ControlPreset {
    Balanced,
    Strict,
    Lockdown,
    Custom,
}

data class GuardSettings(
    val preset: ControlPreset = ControlPreset.Balanced,
    val allowMinutes: Int = 5,
    val forbidDataAllow: Boolean = false,
    val requireAuthToAllow: Boolean = true,
    val closeWindowOnUnplug: Boolean = true,
    val alertOnAnyPlug: Boolean = false,
    val treatConfiguredAsData: Boolean = false,
    val treatAdbAsCritical: Boolean = false,
    val vibrateOnAlert: Boolean = true,
    val fullscreenOnLeak: Boolean = true,
    val policyEnforced: Boolean = true,
    val armed: Boolean = false,
    val stealthMode: Boolean = true,
    val hideLauncherIcon: Boolean = false,
) {
    fun seesData(snapshot: UsbSnapshot): Boolean {
        if (snapshot.mtp || snapshot.ptp || snapshot.adb) return true
        return treatConfiguredAsData && snapshot.connected && snapshot.configured
    }

    fun withManualChange(transform: GuardSettings.() -> GuardSettings): GuardSettings {
        val next = transform()
        val matched = namedPresets().firstOrNull { it.sameControls(next) }
        return next.copy(preset = matched?.preset ?: ControlPreset.Custom)
    }

    fun sameControls(other: GuardSettings): Boolean =
        allowMinutes == other.allowMinutes &&
            forbidDataAllow == other.forbidDataAllow &&
            requireAuthToAllow == other.requireAuthToAllow &&
            closeWindowOnUnplug == other.closeWindowOnUnplug &&
            alertOnAnyPlug == other.alertOnAnyPlug &&
            treatConfiguredAsData == other.treatConfiguredAsData &&
            treatAdbAsCritical == other.treatAdbAsCritical &&
            vibrateOnAlert == other.vibrateOnAlert &&
            fullscreenOnLeak == other.fullscreenOnLeak &&
            policyEnforced == other.policyEnforced

    companion object {
        fun of(preset: ControlPreset): GuardSettings = when (preset) {
            ControlPreset.Balanced -> GuardSettings(
                preset = ControlPreset.Balanced,
                allowMinutes = 5,
                forbidDataAllow = false,
                requireAuthToAllow = true,
                closeWindowOnUnplug = true,
                alertOnAnyPlug = false,
                treatConfiguredAsData = false,
                treatAdbAsCritical = false,
                vibrateOnAlert = true,
                fullscreenOnLeak = true,
                policyEnforced = true,
            )
            ControlPreset.Strict -> GuardSettings(
                preset = ControlPreset.Strict,
                allowMinutes = 2,
                forbidDataAllow = false,
                requireAuthToAllow = true,
                closeWindowOnUnplug = true,
                alertOnAnyPlug = true,
                treatConfiguredAsData = true,
                treatAdbAsCritical = true,
                vibrateOnAlert = true,
                fullscreenOnLeak = true,
                policyEnforced = true,
            )
            ControlPreset.Lockdown -> GuardSettings(
                preset = ControlPreset.Lockdown,
                allowMinutes = 1,
                forbidDataAllow = true,
                requireAuthToAllow = true,
                closeWindowOnUnplug = true,
                alertOnAnyPlug = true,
                treatConfiguredAsData = true,
                treatAdbAsCritical = true,
                vibrateOnAlert = true,
                fullscreenOnLeak = true,
                policyEnforced = true,
            )
            ControlPreset.Custom -> of(ControlPreset.Balanced).copy(preset = ControlPreset.Custom)
        }

        fun namedPresets(): List<GuardSettings> = listOf(
            of(ControlPreset.Balanced),
            of(ControlPreset.Strict),
            of(ControlPreset.Lockdown),
        )
    }
}
