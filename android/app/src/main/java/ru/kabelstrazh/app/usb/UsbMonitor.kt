package ru.kabelstrazh.app.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.kabelstrazh.app.domain.UsbSnapshot

object UsbMonitor {
    const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    fun snapshots(context: Context): Flow<UsbSnapshot> = callbackFlow {
        fun currentFromSticky(): UsbSnapshot {
            val usb = context.registerReceiver(null, IntentFilter(ACTION_USB_STATE))
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            return snapshotOf(usb, battery)
        }

        trySend(currentFromSticky())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val usb = if (intent.action == ACTION_USB_STATE) {
                    intent
                } else {
                    ctx.registerReceiver(null, IntentFilter(ACTION_USB_STATE))
                }
                val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                trySend(snapshotOf(usb, battery))
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(receiver, filter)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun snapshotOf(usb: Intent?, battery: Intent?): UsbSnapshot {
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_AC
        val connected = usb?.getBooleanExtra("connected", false) == true || chargingUsb
        return UsbSnapshot(
            connected = connected,
            configured = usb?.getBooleanExtra("configured", false) == true,
            charging = chargingUsb,
            mtp = usb?.getBooleanExtra("mtp", false) == true,
            ptp = usb?.getBooleanExtra("ptp", false) == true,
            adb = usb?.getBooleanExtra("adb", false) == true,
        )
    }

    fun dataChannels(snapshot: UsbSnapshot): String = buildList {
        if (snapshot.mtp) add("MTP")
        if (snapshot.ptp) add("PTP")
        if (snapshot.adb) add("ADB")
    }.ifEmpty { listOf("нет") }.joinToString(", ")
}
