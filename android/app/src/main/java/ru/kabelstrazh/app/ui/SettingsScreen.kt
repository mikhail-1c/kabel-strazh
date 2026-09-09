package ru.kabelstrazh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.kabelstrazh.app.data.presetLabel
import ru.kabelstrazh.app.domain.ControlPreset
import ru.kabelstrazh.app.domain.GuardSettings
import ru.kabelstrazh.app.domain.GuardUiState

@Composable
fun SettingsScreen(
    state: GuardUiState,
    onBack: () -> Unit,
    onPreset: (ControlPreset) -> Unit,
    onChange: (GuardSettings.() -> GuardSettings) -> Unit,
) {
    val settings = state.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Пресет сразу переписывает тумблеры контроля. Скрытие и «вкл/выкл» пресет не трогает.",
            color = Mute,
            style = MaterialTheme.typography.bodySmall,
        )

        Section("Дежурство")
        Toggle(
            title = "Страж включён",
            hint = "Выключен — обычный телефон, без службы и сирены. Включайте только когда надо.",
            checked = settings.armed,
            onChecked = { onChange { copy(armed = it) } },
        )
        Toggle(
            title = "Скрытый вид",
            hint = "Снаружи «Заряд» и USB. В шторке не пишет про съём. В недавних не висит.",
            checked = settings.stealthMode,
            onChecked = { onChange { copy(stealthMode = it) } },
        )
        Toggle(
            title = "Убрать из меню приложений",
            hint = "Иконка пропадёт. Открыть можно плитки USB в шторке. Сначала добавьте плитку.",
            checked = settings.hideLauncherIcon,
            onChecked = { onChange { copy(hideLauncherIcon = it) } },
        )
        Text("Плитка: шторка → редактировать → USB. Нажатие включает страж.", color = Mute, style = MaterialTheme.typography.bodySmall)

        Text("Контроль", color = Mute)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ControlPreset.Balanced, ControlPreset.Strict, ControlPreset.Lockdown).forEach { preset ->
                FilterChip(
                    selected = settings.preset == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(presetLabel(preset)) },
                )
            }
        }
        if (settings.preset == ControlPreset.Custom) {
            Text("Сейчас свой набор — пресеты не совпадают.", color = Gold, style = MaterialTheme.typography.bodySmall)
        }

        Section("Доступ по кабелю")
        Toggle(
            title = "Запретить открывать данные",
            hint = "Кнопки «разрешить» не будет. Для чужого ПК только заряд.",
            checked = settings.forbidDataAllow,
            onChecked = { onChange { copy(forbidDataAllow = it) } },
        )
        Text("Длительность окна", color = Mute)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 5, 15).forEach { minutes ->
                FilterChip(
                    selected = settings.allowMinutes == minutes,
                    enabled = !settings.forbidDataAllow,
                    onClick = { onChange { copy(allowMinutes = minutes) } },
                    label = { Text("$minutes мин") },
                )
            }
        }
        Toggle(
            title = "Требовать PIN или отпечаток",
            hint = "Без подтверждения окно не откроется, даже если экран уже разблокирован.",
            checked = settings.requireAuthToAllow,
            onChecked = { onChange { copy(requireAuthToAllow = it) } },
        )
        Toggle(
            title = "Закрывать окно, когда кабель вынули",
            hint = "Повторное подключение снова требует разрешения.",
            checked = settings.closeWindowOnUnplug,
            onChecked = { onChange { copy(closeWindowOnUnplug = it) } },
        )

        Section("Что считать съёмом")
        Toggle(
            title = "Шина USB = съём",
            hint = "Даже без MTP/PTP: если телефон уже «настроился» на ПК, это авария.",
            checked = settings.treatConfiguredAsData,
            onChecked = { onChange { copy(treatConfiguredAsData = it) } },
        )
        Toggle(
            title = "ADB всегда авария",
            hint = "Отладка не покрывается окном. Разрешили фото — отладку всё равно орём.",
            checked = settings.treatAdbAsCritical,
            onChecked = { onChange { copy(treatAdbAsCritical = it) } },
        )

        Section("Сигнал")
        Toggle(
            title = "Сирена на любой кабель",
            hint = "Не только на съём: воткнули провод — сразу шторка.",
            checked = settings.alertOnAnyPlug,
            onChecked = { onChange { copy(alertOnAnyPlug = it) } },
        )
        Toggle(
            title = "Вибрация",
            checked = settings.vibrateOnAlert,
            onChecked = { onChange { copy(vibrateOnAlert = it) } },
        )
        Toggle(
            title = "Экран поверх замка при съёме",
            checked = settings.fullscreenOnLeak,
            onChecked = { onChange { copy(fullscreenOnLeak = it) } },
        )

        Section("Система")
        Toggle(
            title = "Жёсткая блокировка USB",
            hint = if (state.deviceOwner) {
                "Device Owner есть — система режет MTP/ADB, пока окно закрыто."
            } else {
                "Нужен Device Owner. Без него тумблер запоминается, но провод не глушит."
            },
            checked = settings.policyEnforced,
            enabled = state.deviceOwner,
            onChecked = { onChange { copy(policyEnforced = it) } },
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Toggle(
    title: String,
    hint: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            if (hint != null) {
                Text(hint, color = Mute, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
