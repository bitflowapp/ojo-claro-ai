package com.ojoclaro.android.global

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ojoclaro.android.MainActivity
import com.ojoclaro.android.R
import com.ojoclaro.android.voice.OjoClaroIntents

class GlobalAssistantNotifier(
    private val context: Context
) {
    private val appContext = context.applicationContext

    fun build(snapshot: ExternalConversationSnapshot): Notification {
        ensureChannel()
        val appName = snapshot.externalApp.spokenName
        return NotificationCompat.Builder(appContext, GlobalAssistantMode.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_tile_mic)
            .setContentTitle("Ojo Claro activo")
            .setContentText("$appName: Escuchar, Callar o Detener.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$appName: Ojo Claro sigue visible por unos segundos. ${snapshot.returnHint}")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openMainListeningPendingIntent(appContext))
            .addAction(
                R.drawable.ic_quick_tile_mic,
                "Escuchar",
                servicePendingIntent(appContext, GlobalAssistantMode.ACTION_LISTEN, REQUEST_LISTEN)
            )
            .addAction(
                R.drawable.ic_quick_tile_mic,
                "Callar",
                servicePendingIntent(appContext, GlobalAssistantMode.ACTION_SILENCE, REQUEST_SILENCE)
            )
            .addAction(
                R.drawable.ic_quick_tile_mic,
                "Detener",
                servicePendingIntent(appContext, GlobalAssistantMode.ACTION_STOP, REQUEST_STOP)
            )
            .build()
    }

    fun cancel() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.cancel(GlobalAssistantMode.NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            GlobalAssistantMode.CHANNEL_ID,
            "Ojo Claro activo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Modo visible para continuar unos segundos al abrir apps externas."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val REQUEST_LISTEN = 2101
        const val REQUEST_SILENCE = 2102
        const val REQUEST_STOP = 2103
        const val REQUEST_OPEN_MAIN_LISTENING = 2104

        fun serviceIntent(context: Context, action: String): Intent =
            Intent(context, GlobalAssistantService::class.java).apply {
                this.action = action
            }

        fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context,
                requestCode,
                serviceIntent(context, action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun openMainListeningIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = OjoClaroIntents.ACTION_START_LISTENING
                putExtra(OjoClaroIntents.EXTRA_START_LISTENING, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        fun openMainListeningPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_MAIN_LISTENING,
                openMainListeningIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
