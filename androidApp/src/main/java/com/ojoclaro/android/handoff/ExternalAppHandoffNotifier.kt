package com.ojoclaro.android.handoff

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ojoclaro.android.MainActivity
import com.ojoclaro.android.R
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.voice.OjoClaroIntents

object ExternalAppHandoffNotifier {
    const val CHANNEL_ID = "ojo_claro_external_app_handoff"
    const val NOTIFICATION_ID = 20260506

    fun show(context: Context, handoff: ExternalActionEvent.ExternalAppHandoff): Boolean {
        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) return false

        ensureChannel(appContext)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_tile_mic)
            .setContentTitle("Estela")
            .setContentText("${handoff.externalAppName}: tocá Escuchar para volver.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${handoff.externalAppName}: ${handoff.returnHint} Tocá Escuchar para volver.")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(startListeningPendingIntent(appContext))
            .addAction(
                R.drawable.ic_quick_tile_mic,
                "Escuchar",
                startListeningPendingIntent(appContext)
            )
            .addAction(
                R.drawable.ic_quick_tile_mic,
                "Callar",
                stopSpeakingPendingIntent(appContext)
            )
            .build()

        return try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    fun canPostNotifications(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Estela",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Acceso para volver a Estela después de abrir otra app."
        }
        manager.createNotificationChannel(channel)
    }

    private fun startListeningPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_START_LISTENING,
            Intent(context, MainActivity::class.java).apply {
                action = OjoClaroIntents.ACTION_START_LISTENING
                putExtra(OjoClaroIntents.EXTRA_START_LISTENING, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopSpeakingPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_STOP_SPEAKING,
            Intent(context, MainActivity::class.java).apply {
                action = OjoClaroIntents.ACTION_STOP_SPEAKING
                putExtra(OjoClaroIntents.EXTRA_STOP_SPEAKING, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private const val REQUEST_START_LISTENING = 1001
    private const val REQUEST_STOP_SPEAKING = 1002
}
