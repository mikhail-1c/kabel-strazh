package ru.kabelstrazh.app.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.kabelstrazh.app.MainActivity
import ru.kabelstrazh.app.data.presetLabel
import ru.kabelstrazh.app.domain.GuardStatus
import ru.kabelstrazh.app.domain.GuardUiState
import ru.kabelstrazh.app.domain.JournalEvent
import ru.kabelstrazh.app.domain.JournalKind
import ru.kabelstrazh.app.usb.UsbMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Page { Home, Journal, Settings }

@Composable
fun GuardApp(viewModel: GuardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(Page.Home) }
    when (page) {
        Page.Journal -> JournalScreen(events = state.events, onBack = { page = Page.Home })
        Page.Settings -> SettingsScreen(
            state = state,
            onBack = { page = Page.Home },
            onPreset = viewModel::applyPreset,
            onChange = viewModel::updateSettings,
        )
        Page.Home -> GuardScreen(
            state = state,
            onAllow = viewModel::grantAllow,
            onClose = viewModel::closeAllow,
            onArm = { viewModel.setArmed(true) },
            onDisarm = { viewModel.setArmed(false) },
            onJournal = { page = Page.Journal },
            onSettings = { page = Page.Settings },
        )
    }
}

@Composable
private fun GuardScreen(
    state: GuardUiState,
    onAllow: () -> Unit,
    onClose: () -> Unit,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onJournal: () -> Unit,
    onSettings: () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    var confirmOpen by remember { mutableStateOf(false) }
    val stealth = state.settings.stealthMode
    val accent = when (state.status) {
        GuardStatus.Disarmed -> Mute
        GuardStatus.Idle -> Mute
        GuardStatus.ChargeOnly -> SafeGreen
        GuardStatus.Allowed -> AllowedBlue
        GuardStatus.DataLeak -> LeakRed
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (stealth) "Заряд" else "Кабель-страж",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onSettings) { Text(if (stealth) "Ещё" else "Настройки") }
        }
        if (!stealth) {
            Text("Режим: ${presetLabel(state.settings.preset)}", color = Gold)
            Text("Страж спит, пока не включите. Чужой ПК без окна получает только заряд.", color = Mute)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(accent)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(statusTitle(state.status), style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text(statusDetail(state), color = Ink.copy(alpha = 0.85f))
            if (state.status == GuardStatus.Allowed) {
                val sec = state.allow.remainingMs(state.nowMs) / 1000
                Text(
                    "Осталось ${sec / 60}:${(sec % 60).toString().padStart(2, '0')}",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (state.status == GuardStatus.Disarmed) {
            Button(
                onClick = {
                    (activity as? MainActivity)?.askNotifications()
                    onArm()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            ) {
                Text("Включить сейчас")
            }
            Text(
                if (stealth) {
                    "Обычный день: служба не работает. Плитка USB в шторке включает её."
                } else {
                    "Выключен: нет службы, нет сирены, кабель обычный. В экстренном случае — эта кнопка или плитка USB в шторке уведомлений."
                },
                color = Mute,
            )
        } else {
            OutlinedButton(onClick = onDisarm, modifier = Modifier.fillMaxWidth()) {
                Text("Выключить")
            }
            Text("Каналы: ${UsbMonitor.dataChannels(state.snapshot)}", color = Mute)
        }

        if (state.status == GuardStatus.Allowed) {
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Закрыть окно сейчас")
            }
        } else if (state.status == GuardStatus.Disarmed) {
            // allow-кнопка только когда страж включён
        } else if (!state.canGrantAllow) {
            Text(
                "В настройках запрещено открывать данные по кабелю. Снять запрет можно пресетом «Обычный» или «Жёсткий».",
                color = Mute,
            )
        } else {
            Button(
                onClick = {
                    BiometricGate.confirm(
                        activity = activity,
                        requireAuth = state.settings.requireAuthToAllow,
                        onUnavailable = { confirmOpen = true },
                        onOk = onAllow,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            ) {
                Text("Разрешить данные на ${state.allowMinutes} мин")
            }
        }

        if (state.status == GuardStatus.DataLeak) {
            OutlinedButton(
                onClick = {
                    val usb = Intent("android.settings.USB_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { activity.startActivity(usb) }.onFailure {
                        activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть настройки USB")
            }
        }

        TextButton(onClick = onJournal) { Text(if (stealth) "История" else "Журнал подключений") }
        if (!stealth) {
            TextButton(onClick = onSettings) { Text("Ужесточить контроль") }
        }
        Spacer(Modifier.height(8.dp))
        if (!stealth) {
            Text(
                "iPhone этим приложением не закрыть: там USB Restricted Mode в самой системе. Android без прав владельца устройства тоже не умеет глушить провод — только заметить съём.",
                color = Mute,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Открыть данные по кабелю?") },
            text = { Text("На телефоне нет отпечатка или PIN. Подтвердите руками: чужой ПК получит доступ на ${state.allowMinutes} мин.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOpen = false
                        onAllow()
                    },
                ) { Text("Открыть") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun JournalScreen(events: List<JournalEvent>, onBack: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale("ru")) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) { Text("Назад") }
        Text("Журнал", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (events.isEmpty()) {
                Text("Пока пусто. Воткните кабель — появится первая запись.", color = Mute)
            }
            events.forEach { event ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Panel)
                        .padding(12.dp),
                ) {
                    Text(kindLabel(event.kind), fontWeight = FontWeight.Medium)
                    Text(event.detail, color = Mute)
                    Text(fmt.format(Date(event.timeMs)), color = Mute, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun statusTitle(status: GuardStatus): String = when (status) {
    GuardStatus.Disarmed -> "Выключен"
    GuardStatus.Idle -> "Кабеля нет"
    GuardStatus.ChargeOnly -> "Только заряд"
    GuardStatus.Allowed -> "Данные открыты вами"
    GuardStatus.DataLeak -> "Съём без разрешения"
}

private fun statusDetail(state: GuardUiState): String = when (state.status) {
    GuardStatus.Disarmed -> "Служба спит. Кабель никто не сторожит."
    GuardStatus.Idle -> "Можно класть телефон. Страж ждёт провод. Режим: ${presetLabel(state.settings.preset)}."
    GuardStatus.ChargeOnly -> "Питание есть, файлы и отладка не торчат."
    GuardStatus.Allowed -> "Это окно истечёт само. Чужой ПК сейчас может снять данные."
    GuardStatus.DataLeak -> "Компьютер уже видит телефон. Вытащите кабель или поставьте «только заряд»."
}

private fun kindLabel(kind: JournalKind): String = when (kind) {
    JournalKind.Plugged -> "Кабель вставлен"
    JournalKind.Unplugged -> "Кабель вынут"
    JournalKind.ChargeOnly -> "Только заряд"
    JournalKind.DataOpened -> "Открылись данные"
    JournalKind.AllowGranted -> "Вы открыли окно"
    JournalKind.AllowExpired -> "Окно закрыто"
    JournalKind.PolicyOn -> "Политика включена"
    JournalKind.PolicyOff -> "Политика выключена"
    JournalKind.PresetApplied -> "Поставлен пресет"
    JournalKind.SettingsChanged -> "Свои настройки"
    JournalKind.Armed -> "Включён"
    JournalKind.Disarmed -> "Выключен"
}
