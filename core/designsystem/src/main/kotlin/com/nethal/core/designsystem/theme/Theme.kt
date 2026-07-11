package com.nethal.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColors = darkColorScheme(
    primary = NetHalAccent,
    secondary = NetHalAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnBackgroundDark,
    onSurface = OnBackgroundDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = BorderDark,
    error = ErrorDark,
)

private val LightColors = lightColorScheme(
    primary = NetHalAccent,
    secondary = NetHalAccent,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = OnBackgroundLight,
    onSurface = OnBackgroundLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = BorderLight,
    error = ErrorLight,
)

/**
 * Tema do NetHAL Lab. Resolve o `ColorScheme` certo a partir de [themeMode]:
 * [ThemeMode.SYSTEM] segue `isSystemInDarkTheme()`, [ThemeMode.LIGHT]/[ThemeMode.DARK] forçam.
 *
 * Além do `ColorScheme` do Material 3, injeta [LocalNetHalExtendedColors] com as cores semânticas
 * sem slot M3 (sucesso/aviso/erro-de-chip/texto terciário) — assim uma tela responde ao tema tanto
 * pelas cores M3 (`MaterialTheme.colorScheme.*`) quanto pelas estendidas
 * (`LocalNetHalExtendedColors.current.*`), sem `when (themeMode)` espalhado pelos componentes.
 *
 * [themeMode] tem default [ThemeMode.SYSTEM] para não quebrar chamadores de teste que usam
 * `NetHalLabTheme { ... }`.
 */
@Composable
fun NetHalLabTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) DarkColors else LightColors
    val extendedColors = if (useDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalNetHalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NetHalTypography,
            content = content,
        )
    }
}
