package ru.kabelstrazh.app.alert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.kabelstrazh.app.ui.KabelTheme
import ru.kabelstrazh.app.ui.LeakRed

class UsbAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            KabelTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LeakRed)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Кабель снимает данные",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "MTP, PTP или ADB включились без вашего окна. Вытащите кабель или переключите USB в «только заряд».",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { finish() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onError),
                    ) {
                        Text("Понятно")
                    }
                }
            }
        }
    }
}
