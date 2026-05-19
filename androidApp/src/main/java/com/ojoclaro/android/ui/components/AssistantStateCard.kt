package com.ojoclaro.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.ui.theme.OjoClaroPalette

/**
 * Estados visibles del asistente. Sirven para el chip principal del Home.
 */
enum class AssistantStatusKind {
    Inactive,
    Listening,
    Processing,
    ReadingScreen,
    AwaitingConfirmation,
    Speaking,
    ActionCompleted,
    ActionCancelled,
    Error
}

data class AssistantStatusViewModel(
    val kind: AssistantStatusKind,
    val title: String,
    val supporting: String
)

internal fun AssistantStatusKind.color(): Color = when (this) {
    AssistantStatusKind.Inactive -> OjoClaroPalette.StatusIdle
    AssistantStatusKind.Listening -> OjoClaroPalette.StatusListening
    AssistantStatusKind.Processing -> OjoClaroPalette.StatusProcessing
    AssistantStatusKind.ReadingScreen -> OjoClaroPalette.StatusReading
    AssistantStatusKind.AwaitingConfirmation -> OjoClaroPalette.StatusWaiting
    AssistantStatusKind.Speaking -> OjoClaroPalette.Orange
    AssistantStatusKind.ActionCompleted -> OjoClaroPalette.StatusOk
    AssistantStatusKind.ActionCancelled -> OjoClaroPalette.StatusIdle
    AssistantStatusKind.Error -> OjoClaroPalette.StatusError
}

internal fun AssistantStatusKind.shortLabel(): String = when (this) {
    AssistantStatusKind.Inactive -> "Inactivo"
    AssistantStatusKind.Listening -> "Escuchando"
    AssistantStatusKind.Processing -> "Procesando"
    AssistantStatusKind.ReadingScreen -> "Leyendo pantalla"
    AssistantStatusKind.AwaitingConfirmation -> "Esperando confirmación"
    AssistantStatusKind.Speaking -> "Respondiendo"
    AssistantStatusKind.ActionCompleted -> "Acción completada"
    AssistantStatusKind.ActionCancelled -> "Acción cancelada"
    AssistantStatusKind.Error -> "Aviso"
}

private fun AssistantStatusKind.isAnimated(): Boolean = when (this) {
    AssistantStatusKind.Listening,
    AssistantStatusKind.Processing,
    AssistantStatusKind.ReadingScreen,
    AssistantStatusKind.Speaking -> true
    else -> false
}

/**
 * Bloque grande con el estado actual del asistente. Texto grande, color por estado,
 * sin depender SOLO del color (también usa la etiqueta y el subtítulo).
 */
@Composable
fun AssistantStateCard(
    state: AssistantStatusViewModel,
    modifier: Modifier = Modifier
) {
    val color = state.kind.color()
    val label = state.kind.shortLabel()
    val pulse = if (state.kind.isAnimated()) {
        val transition = rememberInfiniteTransition(label = "assistant-status-pulse")
        val animated by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "assistant-status-alpha"
        )
        animated
    } else {
        1f
    }
    val accessibility = "Estado: $label. ${state.title}. ${state.supporting}".trim()

    OjoClaroCard(
        modifier = modifier,
        accent = color.copy(alpha = 0.6f),
        accentWidth = 2.dp,
        background = OjoClaroPalette.BackgroundElevated,
        semanticContentDescription = null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = pulse))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = state.title,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Black,
            color = OjoClaroPalette.TextPrimary,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = accessibility
            }
        )
        if (state.supporting.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.supporting,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = OjoClaroPalette.TextSecondary
            )
        }
    }
}

@Composable
fun AssistantStatusPill(
    kind: AssistantStatusKind,
    modifier: Modifier = Modifier
) {
    val color = kind.color()
    val label = kind.shortLabel()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Estado actual: $label" }
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
