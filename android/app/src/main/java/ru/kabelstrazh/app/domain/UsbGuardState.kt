package ru.kabelstrazh.app.domain

data class UsbSnapshot(
    val connected: Boolean = false,
    val configured: Boolean = false,
    val charging: Boolean = false,
    val mtp: Boolean = false,
    val ptp: Boolean = false,
    val adb: Boolean = false,
) {
    val dataExposed: Boolean get() = mtp || ptp || adb
}

data class AllowWindow(
    val untilEpochMs: Long = 0L,
) {
    fun isActive(nowMs: Long): Boolean = nowMs < untilEpochMs

    fun remainingMs(nowMs: Long): Long = (untilEpochMs - nowMs).coerceAtLeast(0L)
}

enum class GuardStatus {
    Idle,
    ChargeOnly,
    Allowed,
    DataLeak,
}

data class JournalEvent(
    val id: Long,
    val timeMs: Long,
    val kind: JournalKind,
    val detail: String,
)

enum class JournalKind {
    Plugged,
    Unplugged,
    ChargeOnly,
    DataOpened,
    AllowGranted,
    AllowExpired,
    PolicyOn,
    PolicyOff,
}

data class GuardUiState(
    val snapshot: UsbSnapshot = UsbSnapshot(),
    val allow: AllowWindow = AllowWindow(),
    val nowMs: Long = 0L,
    val deviceOwner: Boolean = false,
    val policyEnforced: Boolean = false,
    val events: List<JournalEvent> = emptyList(),
    val allowMinutes: Int = 5,
) {
    val status: GuardStatus
        get() = when {
            !snapshot.connected -> GuardStatus.Idle
            snapshot.dataExposed && !allow.isActive(nowMs) -> GuardStatus.DataLeak
            allow.isActive(nowMs) -> GuardStatus.Allowed
            else -> GuardStatus.ChargeOnly
        }
}
