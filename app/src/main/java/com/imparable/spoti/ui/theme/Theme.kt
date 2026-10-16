package com.imparable.spoti.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Tema Material 3 con **color dinámico** (Material You) cuando el sistema lo
 * soporta, y un esquema propio como respaldo.
 *
 * El color de acento resultante se puede propagar al reproductor web
 * (`50_theme.js`), de forma que la web herede la paleta del sistema en vez del
 * verde de Spotify. Es una de las ideas de Spotilol, pero aquí el color lo
 * decide Material You en lugar de un ajuste fijo.
 */
private val FallbackDark = darkColorScheme(
    primary = Color(0xFF7BE0B0),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF00522F),
    secondary = Color(0xFFB9CCBD),
    background = Color(0xFF000000),      // Negro puro: coherente con el AMOLED
    surface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFF1A1C1A),
    onBackground = Color(0xFFE1E3E0),
    onSurface = Color(0xFFE1E3E0)
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF006D42),
    secondary = Color(0xFF4C6355),
    background = Color(0xFFFBFDF8),
    surface = Color(0xFFFBFDF8)
)

@Composable
fun SpotiImparableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }

    // AMOLED: lleva los fondos a negro puro sin tocar los colores de acento.
    val scheme = if (darkTheme && amoled) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black
        )
    } else {
        base
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
