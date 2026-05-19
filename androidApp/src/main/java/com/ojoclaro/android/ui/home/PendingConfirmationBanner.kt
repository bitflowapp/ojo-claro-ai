package com.ojoclaro.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Estado de vista puro del banner de confirmación.
 *
 * Es la pieza testeable: tomamos el [HomeUiState] y derivamos qué mostrar.
 * El Composable consume sólo este estado, así podemos validar el mapping con
 * unit tests sin instrumented.
 */
data class PendingConfirmationViewState(
    val visible: Boolean,
    val message: String,
    val hint: String
) {
    companion object {
        const val DEFAULT_HINT: String =
            "Decí confirmar para continuar o cancelar para detener."

        fun from(state: HomeUiState): PendingConfirmationViewState {
            val pending = state.hasPendingConfirmation
            val message = state.pendingConfirmationText
                ?: state.lastAgentBridgeMessage
                ?: ""
            return PendingConfirmationViewState(
                visible = pending && message.isNotBlank(),
                message = if (pending) message else "",
                hint = if (pending) DEFAULT_HINT else ""
            )
        }
    }
}

/**
 * Banner accesible que se muestra cuando el [HomeViewModel] tiene un pending
 * del Agent Runtime Bridge.
 *
 * Reglas:
 *  - Si no hay pending, no se renderiza nada (no ocupa espacio).
 *  - Marca el contenido con `liveRegion = Polite` para que TalkBack lo lea
 *    cuando aparece.
 *  - Botones con tap target ≥ 48dp (Material3 default Button lo cumple).
 *  - Los botones invocan los callbacks que la pantalla ya conoce (delegan al
 *    mismo path que la voz: emiten "confirmar"/"cancelar" via
 *    `submitVoiceText`).
 */
@Composable
fun PendingConfirmationBanner(
    state: PendingConfirmationViewState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return

    val description = "Confirmación pendiente. ${state.message} ${state.hint}".trim()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp)
            .testTag(BANNER_TEST_TAG)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
                heading()
            }
    ) {
        Text(
            text = "Confirmación pendiente",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .testTag(CONFIRM_BUTTON_TEST_TAG)
                    .semantics { contentDescription = "Confirmar acción pendiente" }
            ) {
                Text(text = "Confirmar")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .testTag(CANCEL_BUTTON_TEST_TAG)
                    .semantics { contentDescription = "Cancelar acción pendiente" }
            ) {
                Text(text = "Cancelar")
            }
        }
    }
}

const val BANNER_TEST_TAG: String = "pending-confirmation-banner"
const val CONFIRM_BUTTON_TEST_TAG: String = "pending-confirmation-confirm"
const val CANCEL_BUTTON_TEST_TAG: String = "pending-confirmation-cancel"

/** Frase que se envía al ViewModel cuando el usuario toca "Confirmar". */
const val CONFIRM_BUTTON_VOICE_PHRASE: String = "confirmar"

/** Frase que se envía al ViewModel cuando el usuario toca "Cancelar". */
const val CANCEL_BUTTON_VOICE_PHRASE: String = "cancelar"
