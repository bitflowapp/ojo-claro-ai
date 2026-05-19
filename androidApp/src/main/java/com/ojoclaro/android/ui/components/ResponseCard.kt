package com.ojoclaro.android.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.ui.theme.OjoClaroPalette

/**
 * Tarjeta para mostrar lo que dijo el asistente. Texto grande, alto contraste,
 * marcada como liveRegion polite para que TalkBack lo lea cuando cambia.
 */
@Composable
fun AssistantResponseCard(
    text: String,
    modifier: Modifier = Modifier
) {
    val safeText = text.ifBlank { "(sin mensaje del asistente)" }
    OjoClaroCard(
        modifier = modifier,
        accent = OjoClaroPalette.Orange,
        accentWidth = 2.dp,
        background = OjoClaroPalette.Surface
    ) {
        OjoClaroCardHeader(label = "Asistente dice", title = "Respuesta", titleColor = OjoClaroPalette.Orange)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = safeText,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = OjoClaroPalette.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Respuesta del asistente: $safeText"
                }
        )
    }
}

/**
 * Tarjeta de "última acción": frase reconocida + intención detectada.
 * Es la "memoria visible" del asistente para una demo comercial.
 */
@Composable
fun LastActionCard(
    recognizedSpeech: String,
    intent: String?,
    pendingLabel: String,
    modifier: Modifier = Modifier
) {
    val recognized = recognizedSpeech.ifBlank { "—" }
    val intentText = intent?.takeIf { it.isNotBlank() } ?: "—"
    val pending = pendingLabel.ifBlank { "—" }

    OjoClaroCard(
        modifier = modifier,
        accent = OjoClaroPalette.Outline,
        accentWidth = 1.dp,
        background = OjoClaroPalette.Surface
    ) {
        OjoClaroCardHeader(label = "Última acción", title = "Lo que entendí")
        Spacer(modifier = Modifier.height(10.dp))
        LabeledRow(label = "Frase escuchada", value = recognized)
        Spacer(modifier = Modifier.height(8.dp))
        LabeledRow(label = "Intención", value = intentText)
        Spacer(modifier = Modifier.height(8.dp))
        LabeledRow(label = "Pendiente", value = pending)
    }
}

/**
 * Tarjeta de acción sugerida. Pinta el contexto + la sugerencia para que un
 * tercero entienda qué propone Ojo Claro AI sin tener que escuchar el TTS.
 */
@Composable
fun SuggestedActionCard(
    contextTitle: String,
    suggestion: String,
    modifier: Modifier = Modifier
) {
    val safeSuggestion = suggestion.ifBlank { "Podés pedir leer pantalla, abrir WhatsApp o ayuda." }
    OjoClaroCard(
        modifier = modifier,
        accent = OjoClaroPalette.OrangeSoft,
        accentWidth = 1.dp,
        background = OjoClaroPalette.BackgroundElevated
    ) {
        OjoClaroCardHeader(
            label = "Acción sugerida",
            title = contextTitle,
            titleColor = OjoClaroPalette.OrangeSoft
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = safeSuggestion,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = OjoClaroPalette.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Sugerencia: $safeSuggestion" }
        )
    }
}

@Composable
private fun LabeledRow(
    label: String,
    value: String
) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = OjoClaroPalette.TextMuted
    )
    Text(
        text = value,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = OjoClaroPalette.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: $value" }
    )
}
