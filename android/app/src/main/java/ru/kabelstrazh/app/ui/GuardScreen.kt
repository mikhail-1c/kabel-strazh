package ru.kabelstrazh.app.ui

import android.provider.Settings
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.kabelstrazh.app.domain.GuardStatus
import ru.kabelstrazh.app.domain.GuardUiState
import ru.kabelstrazh.app.domain.JournalEvent
import ru.kabelstrazh.app.domain.JournalKind
import ru.kabelstrazh.app.usb.UsbMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuardApp(viewModel: GuardViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var journal by remember { mutableStateOf(false) }
    if (journal) {
        JournalScreen(events = state.events, onBack = { journal = false })
    } else {
        GuardScreen(
            state = state,
            onAllow = viewModel::grantAllow,
            onClose = viewModel::closeAllow,
            onMinutes = viewModel::setMinutes,
            onPolicy = viewModel::setPolicy,
            onJournal = { journal = true },
        )
    }
}

@Composable
private fun GuardScreen(
    state: GuardUiState,
    onAllow: () -> Unit,
    onClose: () -> Unit,
    onMinutes: (Int) -> Unit,
    onPolicy: (Boolean) -> Unit,
    onJournal: () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    val accent = when (state.status) {
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
        Text("Кабель-страж", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Чужой ПК без вашего окна получает только заряд.", color = Mute)

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
                Text("Осталось ${sec / 60}:${(sec % 60).toString().padStart(2, '0')}", color = Ink, fontWeight = FontWeight.Bold)
            }
        }

        Text("Каналы: ${UsbMonitor.dataChannels(state.snapshot)}", color = Mute)

        if (state.status == GuardStatus.Allowed) {
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Закрыть окно сейчас")
            }
        } else {
            Button(
                onClick = {
                    BiometricGate.confirm(activity, onOk = onAllow)
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

        Text("Длительность окна", color = Mute)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 5, 15).forEach { minutes ->
                FilterChip(
                    selected = state.allowMinutes == minutes,
                    onClick = { onMinutes(minutes) },
                    label = { Text("$minutes мин") },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Жёсткая блокировка")
                Text(
                    if (state.deviceOwner) {
                        "Device Owner включён — MTP/ADB режет система"
                    } else {
                        "Без Device Owner страж только орёт и пишет журнал"
                    },
                    color = Mute,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.policyEnforced,
                onCheckedChange = onPolicy,
                enabled = state.deviceOwner,
            )
        }

        TextButton(onClick = onJournal) { Text("Журнал подключений") }
        Spacer(Modifier.height(8.dp))
        Text(
            "iPhone этим приложением не закрыть: там USB Restricted Mode в самой системе. Android без прав владельца устройства тоже не умеет глушить провод — только заметить съём.",
            color = Mute,
            style = MaterialTheme.typography.bodySmall,
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
    GuardStatus.Idle -> "Кабеля нет"
    GuardStatus.ChargeOnly -> "Только заряд"
    GuardStatus.Allowed -> "Данные открыты вами"
    GuardStatus.DataLeak -> "Съём без разрешения"
}

private fun statusDetail(state: GuardUiState): String = when (state.status) {
    GuardStatus.Idle -> "Можно класть телефон. Страж ждёт провод."
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
}
