package com.ojoclaro.android.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ojoclaro.android.MainActivity

/**
 * Quick Settings tile que abre Ojo Claro en modo escucha.
 *
 * Comportamiento:
 *  - Tocar el tile lanza MainActivity con ACTION_START_LISTENING.
 *  - NO escucha en background.
 *  - NO graba audio.
 *  - NO usa hotword.
 *  - Solo activa el voice loop cuando la app queda visible y el usuario tiene
 *    RECORD_AUDIO concedido. Si no lo tiene, la UI pide permiso con explicación.
 *
 * Por qué TileService y no un Service de larga duración:
 *  Para una persona ciega, el atajo del panel de ajustes rápidos es uno de los pocos
 *  caminos rápidos sin tener que buscar el ícono entre apps. Es discoverable por
 *  TalkBack ("Ojo Claro AI"), no requiere triple power, y respeta la batería.
 */
class OjoClaroQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = TILE_LABEL
            contentDescription = TILE_CONTENT_DESCRIPTION
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
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
