package com.example.demonitalk.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = HellRed,
    secondary = DeepBlood,
    tertiary = BrimstoneYellow,
    background = AbyssBlack,
    surface = AbyssBlack,
    onPrimary = Color.White,
    onSecondary = SoulWhite,
    onTertiary = AbyssBlack,
    onBackground = SoulWhite,
    onSurface = SoulWhite,
    error = DeepBlood
)

// Aunque sea modo claro, mantenemos la esencia con el rojo y amarillo de la marca
private val LightColorScheme = lightColorScheme(
    primary = HellRed,
    secondary = Obsidian,
    tertiary = BrimstoneYellow,
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AbyssBlack,
    onSurface = AbyssBlack
)

@Composable
fun DemoniTalkTheme(
    darkTheme: Boolean = true, // Forzamos oscuro por defecto por el nombre de la app
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
