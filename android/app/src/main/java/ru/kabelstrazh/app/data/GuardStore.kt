package ru.kabelstrazh.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ru.kabelstrazh.app.domain.AllowWindow
import ru.kabelstrazh.app.domain.ControlPreset
import ru.kabelstrazh.app.domain.GuardSettings
import ru.kabelstrazh.app.domain.JournalEvent
import ru.kabelstrazh.app.domain.JournalKind

private val Context.guardDataStore by preferencesDataStore(name = "kabel_strazh")

class GuardStore(private val context: Context) {
    private val allowUntil = longPreferencesKey("allow_until")
    private val allowMinutes = intPreferencesKey("allow_minutes")
    private val policyOn = booleanPreferencesKey("policy_on")
    private val journalJson = stringPreferencesKey("journal")
    private val presetKey = stringPreferencesKey("preset")
    private val forbidData = booleanPreferencesKey("forbid_data_allow")
    private val requireAuth = booleanPreferencesKey("require_auth")
    private val closeOnUnplug = booleanPreferencesKey("close_on_unplug")
    private val alertOnPlug = booleanPreferencesKey("alert_on_plug")
    private val configuredAsData = booleanPreferencesKey("configured_as_data")
    private val adbCritical = booleanPreferencesKey("adb_critical")
    private val vibrate = booleanPreferencesKey("vibrate")
    private val fullscreen = booleanPreferencesKey("fullscreen_leak")

    val allowWindow: Flow<AllowWindow> = context.guardDataStore.data.map { prefs ->
        AllowWindow(prefs[allowUntil] ?: 0L)
    }

    val settings: Flow<GuardSettings> = context.guardDataStore.data.map { prefs ->
        GuardSettings(
            preset = prefs[presetKey]?.let { runCatching { ControlPreset.valueOf(it) }.getOrNull() }
                ?: ControlPreset.Balanced,
            allowMinutes = prefs[allowMinutes] ?: 5,
            forbidDataAllow = prefs[forbidData] ?: false,
            requireAuthToAllow = prefs[requireAuth] ?: true,
            closeWindowOnUnplug = prefs[closeOnUnplug] ?: true,
            alertOnAnyPlug = prefs[alertOnPlug] ?: false,
            treatConfiguredAsData = prefs[configuredAsData] ?: false,
            treatAdbAsCritical = prefs[adbCritical] ?: false,
            vibrateOnAlert = prefs[vibrate] ?: true,
            fullscreenOnLeak = prefs[fullscreen] ?: true,
            policyEnforced = prefs[policyOn] ?: true,
        )
    }

    val events: Flow<List<JournalEvent>> = context.guardDataStore.data.map { prefs ->
        parseEvents(prefs[journalJson].orEmpty())
    }

    suspend fun currentSettings(): GuardSettings = settings.first()

    suspend fun grantAllow(durationMs: Long) {
        context.guardDataStore.edit { prefs ->
            prefs[allowUntil] = System.currentTimeMillis() + durationMs
        }
    }

    suspend fun clearAllow() {
        context.guardDataStore.edit { prefs ->
            prefs[allowUntil] = 0L
        }
    }

    suspend fun applyPreset(preset: ControlPreset) {
        val next = GuardSettings.of(preset)
        writeSettings(next)
        if (next.forbidDataAllow) {
            clearAllow()
        }
        append(JournalKind.PresetApplied, "Пресет: ${presetLabel(preset)}")
    }

    suspend fun updateSettings(transform: GuardSettings.() -> GuardSettings) {
        val next = currentSettings().withManualChange(transform)
        writeSettings(next)
        append(JournalKind.SettingsChanged, "Настройки: ${presetLabel(next.preset)}")
        if (next.forbidDataAllow) {
            clearAllow()
        }
    }

    private suspend fun writeSettings(value: GuardSettings) {
        context.guardDataStore.edit { prefs ->
            prefs[presetKey] = value.preset.name
            prefs[allowMinutes] = value.allowMinutes
            prefs[forbidData] = value.forbidDataAllow
            prefs[requireAuth] = value.requireAuthToAllow
            prefs[closeOnUnplug] = value.closeWindowOnUnplug
            prefs[alertOnPlug] = value.alertOnAnyPlug
            prefs[configuredAsData] = value.treatConfiguredAsData
            prefs[adbCritical] = value.treatAdbAsCritical
            prefs[vibrate] = value.vibrateOnAlert
            prefs[fullscreen] = value.fullscreenOnLeak
            prefs[policyOn] = value.policyEnforced
        }
    }

    suspend fun append(kind: JournalKind, detail: String) {
        context.guardDataStore.edit { prefs ->
            val current = parseEvents(prefs[journalJson].orEmpty()).toMutableList()
            current.add(
                0,
                JournalEvent(
                    id = System.currentTimeMillis(),
                    timeMs = System.currentTimeMillis(),
                    kind = kind,
                    detail = detail,
                ),
            )
            prefs[journalJson] = serializeEvents(current.take(200))
        }
    }

    private fun parseEvents(raw: String): List<JournalEvent> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        JournalEvent(
                            id = obj.getLong("id"),
                            timeMs = obj.getLong("time"),
                            kind = JournalKind.valueOf(obj.getString("kind")),
                            detail = obj.optString("detail"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeEvents(events: List<JournalEvent>): String {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("time", event.timeMs)
                    .put("kind", event.kind.name)
                    .put("detail", event.detail),
            )
        }
        return array.toString()
    }
}

fun presetLabel(preset: ControlPreset): String = when (preset) {
    ControlPreset.Balanced -> "Обычный"
    ControlPreset.Strict -> "Жёсткий"
    ControlPreset.Lockdown -> "Замок"
    ControlPreset.Custom -> "Свой набор"
}
