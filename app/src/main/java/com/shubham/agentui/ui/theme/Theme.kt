package com.shubham.agentui.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    secondary = Indigo80,
    tertiary = Amber80,
    background = androidx.compose.ui.graphics.Color(0xFF101413),
    surface = androidx.compose.ui.graphics.Color(0xFF181C1B),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF202624)
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    secondary = Indigo40,
    tertiary = Amber40,
    background = androidx.compose.ui.graphics.Color(0xFFFAFBF8),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF1F5F1),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD6F2ED),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDEE7F8),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFE8A3)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AgentUITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
