package com.example.pluck.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.example.pluck.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0D5AD),
    onPrimary = Color(0xFF33371F),
    primaryContainer = Color(0xFF4A4E32),
    onPrimaryContainer = Color(0xFFECEFC8),
    secondary = Color(0xFFD0CEC0),
    onSecondary = Color(0xFF33342D),
    secondaryContainer = Color(0xFF4A4A41),
    onSecondaryContainer = Color(0xFFE8E6D7),
    tertiary = Color(0xFFE8B9A4),
    onTertiary = Color(0xFF482A20),
    tertiaryContainer = Color(0xFF604237),
    onTertiaryContainer = Color(0xFFFFDCCE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF14150F),
    onBackground = Color(0xFFE5E5DA),
    surface = Color(0xFF14150F),
    onSurface = Color(0xFFE5E5DA),
    surfaceVariant = Color(0xFF474940),
    onSurfaceVariant = Color(0xFFC8C9BD),
    outline = Color(0xFF919287),
    outlineVariant = Color(0xFF474940),
    scrim = PureBlack,
    inverseSurface = Color(0xFFE5E5DA),
    inverseOnSurface = Color(0xFF2F302A),
    inversePrimary = Color(0xFF5F6446),
    surfaceDim = Color(0xFF14150F),
    surfaceBright = Color(0xFF3A3B33),
    surfaceContainerLowest = Color(0xFF0E0F0A),
    surfaceContainerLow = Color(0xFF1C1D17),
    surfaceContainer = Color(0xFF20211B),
    surfaceContainerHigh = Color(0xFF2B2C25),
    surfaceContainerHighest = Color(0xFF36372F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5F6446),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8BE),
    onPrimaryContainer = Color(0xFF474B30),
    secondary = Color(0xFF5E5F56),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E3D7),
    onSecondaryContainer = Color(0xFF474840),
    tertiary = Color(0xFF79594B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCCF),
    onTertiaryContainer = Color(0xFF604236),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1B1C17),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1B1C17),
    surfaceVariant = Color(0xFFE5E5D9),
    onSurfaceVariant = Color(0xFF47483F),
    outline = Color(0xFF77786E),
    outlineVariant = Color(0xFFC7C7BB),
    scrim = PureBlack,
    inverseSurface = Color(0xFF30312B),
    inverseOnSurface = Color(0xFFF2F1E8),
    inversePrimary = Color(0xFFD0D5AD),
    surfaceDim = Color(0xFFDBDBD1),
    surfaceBright = Color(0xFFFFFBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4EA),
    surfaceContainer = Color(0xFFEFEFE5),
    surfaceContainerHigh = Color(0xFFEAE9DF),
    surfaceContainerHighest = Color(0xFFE4E4DA)
)

/** Keeps wallpaper-derived accents while making all app canvas and surface roles OLED-black. */
private fun ColorScheme.asAmoledBlack(): ColorScheme = copy(
    background = PureBlack,
    surface = PureBlack,
    surfaceVariant = Color(0xFF242424),
    primaryContainer = Color(0xFF1B1B1B),
    onPrimaryContainer = primary,
    secondaryContainer = Color(0xFF1B1B1B),
    onSecondaryContainer = secondary,
    tertiaryContainer = Color(0xFF1B1B1B),
    onTertiaryContainer = tertiary,
    surfaceTint = Color.Transparent,
    surfaceDim = PureBlack,
    surfaceBright = Color(0xFF202020),
    surfaceContainerLowest = PureBlack,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF212121)
)

@Composable
fun PluckTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED_BLACK -> true
    }
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (themeMode == ThemeMode.AMOLED_BLACK) {
        baseColorScheme.asAmoledBlack()
    } else {
        baseColorScheme
    }

    ApplySystemBarIconAppearance(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PluckShapes,
        content = content
    )
}

/** Keeps transparent edge-to-edge system-bar icons readable when Pluck overrides device theme. */
@Composable
private fun ApplySystemBarIconAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val activity = context.findActivity() ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
