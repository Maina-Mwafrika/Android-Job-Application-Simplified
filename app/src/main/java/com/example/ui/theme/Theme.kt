package com.example.ui.theme

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
    primary = BrandPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    secondaryContainer = BrandSecondaryContainer,
    error = ErrorRed,
    errorContainer = ErrorRedContainer,
    background = PolishBackground,
    surface = PolishSurface,
    surfaceVariant = PolishSurfaceVariant,
    onBackground = PolishOnBackground,
    onSurface = PolishOnSurface,
    onSurfaceVariant = PolishOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    secondaryContainer = BrandSecondaryContainer,
    error = ErrorRed,
    errorContainer = ErrorRedContainer,
    background = PolishBackground,
    surface = PolishSurface,
    surfaceVariant = PolishSurfaceVariant,
    onBackground = PolishOnBackground,
    onSurface = PolishOnSurface,
    onSurfaceVariant = PolishOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to enforce our gorgeous brand theme
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
