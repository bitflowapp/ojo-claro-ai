package com.ojoclaro.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.ui.theme.OjoClaroPalette

/**
 * Una entrada del historial de actividad reciente.
 *
 * @param title nombre de la acción ("Leer pantalla", "Abrir WhatsApp", etc.)
 * @param status texto corto de estado: "Completada", "Cancelada", "En curso".
 * @param timestamp etiqueta legible: "hace 2 min", "10:42".
 * @param result detalle: lo que dijo el asistente, lo que se ejecutó.
 */
data class ActivityEntry(
    val title: String,
    val status: String,
    val timestamp: String,
    val result: String,
    val kind: AssistantStatusKind
)

@Composable
fun HistoryCard(
    entries: List<ActivityEntry>,
    modifier: Modifier = Modifier
) {
    OjoClaroCard(
        modifier = modifier,
        accent = OjoClaroPalette.Outline,
        accentWidth = 1.dp,
        background = OjoClaroPalette.Surface
    ) {
        OjoClaroCardHeader(label = "Actividad reciente", title = "Lo que pasó")
        Spacer(modifier = Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                text = "Todavía no hay actividad. Pedime una acción y la vas a ver acá.",
                fontSize = 17.sp,
                lineHeight = 23.sp,
                color = OjoClaroPalette.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Historial vacío." }
            )
            return@OjoClaroCard
        }
        entries.forEachIndexed { index, entry ->
            ActivityRow(entry = entry)
            if (index != entries.lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val color = entry.kind.color()
    val description = "Acción ${entry.title}, estado ${entry.status}, ${entry.timestamp}. ${entry.result}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OjoClaroPalette.BackgroundElevated)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Top
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OjoClaroPalette.TextPrimary
                )
                Text(
                    text = entry.timestamp,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OjoClaroPalette.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.status,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            if (entry.result.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.result,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = OjoClaroPalette.TextSecondary
                )
            }
        }
    }
}
