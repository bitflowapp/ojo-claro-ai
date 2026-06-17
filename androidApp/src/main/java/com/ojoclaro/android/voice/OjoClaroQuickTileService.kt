package com.ojoclaro.android.voice

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.ojoclaro.android.MainActivity
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.global.GlobalAssistantService

/**
 * Quick Settings tile de Estela.
 *
 * Comportamiento:
 *  - Si Accesibilidad y micrófono están listos, activa el modo global y escucha.
 *  - Si falta algo, abre MainActivity con ACTION_START_LISTENING para que la UI
 *    pida permisos con explicación.
 *  - NO graba audio.
 *  - NO usa hotword.
 *  - NO ejecuta acciones sensibles ni toca WhatsApp.
 *
 * Por qué TileService y no un Service de larga duración:
 *  Para una persona ciega, el atajo del panel de ajustes rápidos es uno de los pocos
 *  caminos rápidos sin tener que buscar el ícono entre apps. Es discoverable por
 *  TalkBack ("Ojo Claro AI"), no requiere triple power, y respeta la batería.
 */
class OjoClaroQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val readiness = estelaQuickTileReadiness(
            hasMicrophonePermission = hasMicrophonePermission(),
            isAccessibilityActive = OjoClaroAccessibilityService.isConnected()
        )
        qsTile?.apply {
            state = if (readiness.canStartGlobal) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = TILE_LABEL
            contentDescription = TILE_CONTENT_DESCRIPTION
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val readiness = estelaQuickTileReadiness(
            hasMicrophonePermission = hasMicrophonePermission(),
            isAccessibilityActive = OjoClaroAccessibilityService.isConnected()
        )
        if (readiness.canStartGlobal) {
            val started = GlobalAssistantService.startOverlayVoice(
                context = this,
                sourcePackageName = OjoClaroAccessibilityService.readActivePackageName(),
                startListeningDelayMillis = 0L
            )
            if (started) return
        }

        val fallbackMessage = readiness.messageWhenUnavailable.ifBlank {
            "Abro Estela para escuchar."
        }
        Toast.makeText(this, fallbackMessage, Toast.LENGTH_LONG).show()
        openMainListening()
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun openMainListening() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = OjoClaroIntents.ACTION_START_LISTENING
            putExtra(OjoClaroIntents.EXTRA_START_LISTENING, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: el sistema exige PendingIntent para colapsar el shade
            // y abrir la activity en una sola operación.
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE_OPEN_LISTENING,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(launchIntent)
        }
    }

    companion object {
        private const val TILE_LABEL = "Estela"
        private const val TILE_CONTENT_DESCRIPTION =
            "Activar Estela en modo escucha."
        private const val REQUEST_CODE_OPEN_LISTENING = 100
    }
}

internal data class EstelaQuickTileReadiness(
    val canStartGlobal: Boolean,
    val messageWhenUnavailable: String
)

internal fun estelaQuickTileReadiness(
    hasMicrophonePermission: Boolean,
    isAccessibilityActive: Boolean
): EstelaQuickTileReadiness =
    if (hasMicrophonePermission && isAccessibilityActive) {
        EstelaQuickTileReadiness(canStartGlobal = true, messageWhenUnavailable = "")
    } else {
        EstelaQuickTileReadiness(
            canStartGlobal = false,
            messageWhenUnavailable = "Necesito permiso de micrófono y accesibilidad activa."
        )
    }
