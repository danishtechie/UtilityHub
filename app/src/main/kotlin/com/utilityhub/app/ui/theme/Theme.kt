package com.utilityhub.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utilityhub.app.data.ThemeMode

// Apple/iOS Glassmorphic Palette: Vibrant indigo accent, translucent frosted surfaces, crisp typography
private val LightColors = lightColorScheme(
    primary = Color(0xFF635BFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFEEEBFF), onPrimaryContainer = Color(0xFF231E70),
    secondary = Color(0xFF4F5B93), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EBFC), onSecondaryContainer = Color(0xFF161B3B),
    background = Color(0xFFF2F4FD), onBackground = Color(0xFF14172C),
    surface = Color(0xCCFFFFFF), onSurface = Color(0xFF14172C),
    surfaceVariant = Color(0xE0EAEDF9), onSurfaceVariant = Color(0xFF4A4E6B),
    outline = Color(0xFF888CB3), outlineVariant = Color(0xFFD3D6EA),
    error = Color(0xFFE53935), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8C85FF), onPrimary = Color(0xFF161048),
    primaryContainer = Color(0xFF2C2475), onPrimaryContainer = Color(0xFFEBE8FF),
    secondary = Color(0xFFA5B2F5), onSecondary = Color(0xFF171F42),
    secondaryContainer = Color(0xFF232C59), onSecondaryContainer = Color(0xFFE0E5FF),
    background = Color(0xFF0C0E1E), onBackground = Color(0xFFEAEBFA),
    surface = Color(0x2E1E2342), onSurface = Color(0xFFEAEBFA),
    surfaceVariant = Color(0x422B315B), onSurfaceVariant = Color(0xFFC7CAE8),
    outline = Color(0xFF8C90BA), outlineVariant = Color(0x3B3E4575),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val BaseTypography = Typography()
private val AppTypography = BaseTypography.copy(
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun GlassmorphicBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val gradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0C0E1E),
                Color(0xFF141738),
                Color(0xFF0E1022)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF4F6FE),
                Color(0xFFE9ECFA),
                Color(0xFFF7F8FE)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        content()
    }
}

@Composable
fun UtilityHubTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
}
