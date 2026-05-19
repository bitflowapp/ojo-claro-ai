package com.ojoclaro.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.ui.theme.OjoClaroPalette

/**
 * Indicador visible de escucha: micrófono grande circular + transcripción en vivo.
 *
 * @param isListening cuando true el círculo respira y la transcripción se vuelve cabecera.
 * @param partialText texto parcial reconocido. Vacío hasta que el usuario hable.
 * @param recognizedText última frase final reconocida ("-" cuando todavía no hubo nada).
 */
@Composable
fun ListenIndicator(
    isListening: Boolean,
    partialText: String,
    recognizedText: String,
    modifier: Modifier = Modifier
) {
    val pulse = if (isListening) {
        val transition = rememberInfiniteTransition(label = "listen-pulse")
        val a by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "listen-pulse-alpha"
        )
        a
    } else {
        0.35f
    }

    val statusLabel = if (isListening) "Escuchando…" else "Listo para escuchar"
    val live = partialText.ifBlank { recognizedText.ifBlank { "—" } }
    val description = "Estado del micrófono: $statusLabel. Última frase: $live"

    OjoClaroCard(
        modifier = modifier,
        accent = if (isListening) OjoClaroPalette.Orange else OjoClaroPalette.Outline,
        accentWidth = if (isListening) 2.dp else 1.dp,
        background = OjoClaroPalette.BackgroundElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = description
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(OjoClaroPalette.Orange.copy(alpha = pulse)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MIC",
                    color = OjoClaroPalette.OrangeInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusLabel,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isListening) OjoClaroPalette.Orange else OjoClaroPalette.TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "“$live”",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = OjoClaroPalette.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * Tarjeta para mostrar el resultado de la lectura de pantalla.
 * Mantiene un campo de "advertencia" para contenido sensible.
 */
@Composable
fun ScreenReadingCard(
    summary: String,
    detectedText: String,
    warnings: List<String>,
    modifier: Modifier = Modifier
) {
    val safeSummary = summary.ifBlank { "Tocá Leer pantalla para que Estela analice qué hay en la pantalla." }
    val safeDetected = detectedText.ifBlank { "—" }

    OjoClaroCard(
        modifier = modifier,
        accent = OjoClaroPalette.StatusReading,
        accentWidth = 1.dp,
        background = OjoClaroPalette.Surface
    ) {
        OjoClaroCardHeader(
            label = "Lectura de pantalla",
            title = "Resumen",
            titleColor = OjoClaroPalette.StatusReading
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = safeSummary,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = OjoClaroPalette.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Resumen de pantalla: $safeSummary" }
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Texto encontrado",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OjoClaroPalette.TextMuted
        )
        Text(
            text = safeDetected,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            color = OjoClaroPalette.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Texto encontrado: $safeDetected" }
        )
        if (warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            warnings.forEach { warning ->
                Text(
                    text = "⚠ $warning",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OjoClaroPalette.StatusError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Advertencia: $warning" }
                )
            }
        }
    }
}

/**
 * Tarjeta de acción guiada: contexto + propuesta para confirmar.
 * Los botones grandes los pone la pantalla que la consume usando OjoClaroConfirmRow.
 */
@Composable
fun GuidedActionCard(
    contextTitle: String,
    proposedAction: String,
    modifier: Modifier = Modifier
) {
    val safe = proposedAction.ifBlank { "Decime qué querés que haga." }
    OjoClaroCard(
        modifier = modifier,
        accent = OjoClaroPalette.Orange,
        accentWidth = 2.dp,
        background = OjoClaroPalette.BackgroundElevated
    ) {
        OjoClaroCardHeader(
            label = "Acción guiada",
            title = contextTitle,
            titleColor = OjoClaroPalette.Orange
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = safe,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = OjoClaroPalette.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Acción propuesta: $safe"
                }
        )
    }
}
