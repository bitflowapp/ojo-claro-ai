package com.ojoclaro.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.ui.theme.OjoClaroPalette

/**
 * Botón primario gigante de Ojo Claro AI.
 * Estilo: relleno naranja, esquinas suaves, tipografía grande, tap target >= 64dp.
 * Usado para las acciones principales: Escuchar, Sí continuar, Confirmar, etc.
 */
@Composable
fun OjoClaroPrimaryButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = OjoClaroPalette.Orange,
            contentColor = OjoClaroPalette.OrangeInk,
            disabledContainerColor = OjoClaroPalette.SurfaceVariant,
            disabledContentColor = OjoClaroPalette.TextMuted
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (large) 72.dp else 56.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = text,
            fontSize = if (large) 22.sp else 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Botón secundario de Ojo Claro: contorno naranja sobre fondo oscuro.
 * Para acciones de soporte: Leer pantalla, Ayuda, Repetir, etc.
 */
@Composable
fun OjoClaroSecondaryButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OjoClaroPalette.TextPrimary,
            disabledContentColor = OjoClaroPalette.TextMuted
        ),
        border = BorderStroke(2.dp, OjoClaroPalette.Orange),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 56.dp else 64.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = text,
            fontSize = if (compact) 18.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = OjoClaroPalette.TextPrimary
        )
    }
}

/**
 * Botón para cancelar / detener. Naranja con tono mas fuerte de aviso.
 */
@Composable
fun OjoClaroDangerButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = OjoClaroPalette.StatusError,
            disabledContentColor = OjoClaroPalette.TextMuted
        ),
        border = BorderStroke(2.dp, OjoClaroPalette.StatusError),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 56.dp else 64.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(
            text = text,
            fontSize = if (compact) 18.sp else 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Fila de dos botones (Sí / No) para confirmaciones grandes.
 */
@Composable
fun OjoClaroConfirmRow(
    confirmText: String,
    cancelText: String,
    confirmContentDescription: String,
    cancelContentDescription: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = OjoClaroPalette.Orange,
                contentColor = OjoClaroPalette.OrangeInk
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 72.dp)
                .semantics { contentDescription = confirmContentDescription }
        ) {
            Text(
                text = confirmText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 72.dp)
                .semantics { contentDescription = cancelContentDescription }
        ) {
            Text(
                text = cancelText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
