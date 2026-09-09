package ru.kabelstrazh.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ru.kabelstrazh.app.domain.AllowWindow
import ru.kabelstrazh.app.domain.JournalEvent
import ru.kabelstrazh.app.domain.JournalKind

private val Context.guardDataStore by preferencesDataStore(name = "kabel_strazh")

class GuardStore(private val context: Context) {
    private val allowUntil = longPreferencesKey("allow_until")
    private val allowMinutes = intPreferencesKey("allow_minutes")
    private val policyOn = booleanPreferencesKey("policy_on")
    private val journalJson = stringPreferencesKey("journal")

    val allowWindow: Flow<AllowWindow> = context.guardDataStore.data.map { prefs ->
        AllowWindow(prefs[allowUntil] ?: 0L)
    }

    val allowMinutesFlow: Flow<Int> = context.guardDataStore.data.map { prefs ->
        prefs[allowMinutes] ?: 5
    }

    val policyEnforced: Flow<Boolean> = context.guardDataStore.data.map { prefs ->
        prefs[policyOn] ?: true
    }

    val events: Flow<List<JournalEvent>> = context.guardDataStore.data.map { prefs ->
        parseEvents(prefs[journalJson].orEmpty())
    }

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

    suspend fun setAllowMinutes(minutes: Int) {
        context.guardDataStore.edit { prefs ->
            prefs[allowMinutes] = minutes
        }
    }

    suspend fun setPolicyEnforced(value: Boolean) {
        context.guardDataStore.edit { prefs ->
            prefs[policyOn] = value
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
