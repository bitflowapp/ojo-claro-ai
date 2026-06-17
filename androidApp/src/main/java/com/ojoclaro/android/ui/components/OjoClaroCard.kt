package com.ojoclaro.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.ui.theme.OjoClaroPalette

/**
 * Tarjeta base de Ojo Claro. Fondo oscuro elevado, borde sutil naranja opcional,
 * esquinas suaves. El cuerpo lo arma el composable que la usa.
 */
@Composable
fun OjoClaroCard(
    modifier: Modifier = Modifier,
    accent: Color = OjoClaroPalette.Outline,
    accentWidth: androidx.compose.ui.unit.Dp = 1.dp,
    background: Color = OjoClaroPalette.Surface,
    semanticContentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(background)
        .border(BorderStroke(accentWidth, accent), RoundedCornerShape(16.dp))
        .padding(horizontal = 18.dp, vertical = 16.dp)
    val resolved = if (semanticContentDescription != null) {
        baseModifier.semantics { contentDescription = semanticContentDescription }
    } else {
        baseModifier
    }
    Column(modifier = resolved) {
        content()
    }
}

/**
 * Encabezado de tarjeta: rótulo discreto en mayúscula corta + título grande.
 */
@Composable
fun OjoClaroCardHeader(
    label: String,
    title: String,
    titleColor: Color = OjoClaroPalette.TextPrimary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OjoClaroPalette.Orange,
            modifier = Modifier.semantics { contentDescription = label }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            modifier = Modifier.semantics { heading() }
        )
    }
}
