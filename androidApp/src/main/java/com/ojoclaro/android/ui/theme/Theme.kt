package com.ojoclaro.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val OjoClaroDarkColors = darkColorScheme(
    primary = OjoClaroPalette.Orange,
    onPrimary = OjoClaroPalette.OrangeInk,
    primaryContainer = OjoClaroPalette.OrangeDeep,
    onPrimaryContainer = OjoClaroPalette.TextPrimary,

    secondary = OjoClaroPalette.OrangeSoft,
    onSecondary = OjoClaroPalette.OrangeInk,
    secondaryContainer = OjoClaroPalette.SurfaceVariant,
    onSecondaryContainer = OjoClaroPalette.TextPrimary,

    tertiary = OjoClaroPalette.StatusOk,
    onTertiary = OjoClaroPalette.OrangeInk,

    background = OjoClaroPalette.BackgroundBase,
    onBackground = OjoClaroPalette.TextPrimary,
    surface = OjoClaroPalette.Surface,
    onSurface = OjoClaroPalette.TextPrimary,
    surfaceVariant = OjoClaroPalette.SurfaceVariant,
    onSurfaceVariant = OjoClaroPalette.TextSecondary,

    outline = OjoClaroPalette.Outline,
    error = OjoClaroPalette.StatusError,
    onError = OjoClaroPalette.OrangeInk
)

private val OjoClaroTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun OjoClaroTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OjoClaroDarkColors,
        typography = OjoClaroTypography,
        content = content
    )
}
