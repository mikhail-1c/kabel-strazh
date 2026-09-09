package ru.kabelstrazh.app.usb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ru.kabelstrazh.app.MainActivity
import ru.kabelstrazh.app.R
import ru.kabelstrazh.app.alert.UsbAlertActivity
import ru.kabelstrazh.app.domain.GuardStatus

object Notifications {
    const val CHANNEL_GUARD = "guard"
    const val CHANNEL_ALERT = "alert"
    const val ID_FOREGROUND = 41
    const val ID_ALERT = 42
    const val ID_PLUG = 43

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GUARD,
                context.getString(R.string.channel_guard),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun guard(context: Context, status: GuardStatus, detail: String): Notification {
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (status) {
            GuardStatus.Idle -> "Кабель не подключён"
            GuardStatus.ChargeOnly -> "Только заряд"
            GuardStatus.Allowed -> "Данные разрешены"
            GuardStatus.DataLeak -> "Идёт съём данных"
        }
        return NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun leakAlert(context: Context, detail: String, fullscreen: Boolean = true): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            1,
            Intent(context, UsbAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Кабель открыл данные без разрешения")
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
        if (fullscreen) {
            builder.setFullScreenIntent(fullScreen, true)
        } else {
            builder.setContentIntent(fullScreen)
        }
        return builder.build()
    }

    fun plugAlert(context: Context): Notification {
        val tap = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Вставлен кабель")
            .setContentText("Проверьте, что данные не открылись")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
    }
}
