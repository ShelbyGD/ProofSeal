package de.proofseal.app.ui.theme

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

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

// Modernes Google Material 3 (Material You / Pixel UI) Slate & Mint/Emerald Farbschema
val SlateBackgroundDark = Color(0xFF0F1419)
val SlateSurfaceDark = Color(0xFF161D24)
val SlateSurfaceContainerDark = Color(0xFF1A2129)
val SlateSurfaceElevatedDark = Color(0xFF212A34)
val SlateOutlineDark = Color(0xFF323F4E)

val MintPrimaryDark = Color(0xFF00E676)
val MintAccentBright = Color(0xFF69F0AE)
val MintContainerDark = Color(0xFF003919)
val OnMintContainerDark = Color(0xFF85F8BE)

val CyanSecondaryDark = Color(0xFF4FC3F7)
val CyanContainerDark = Color(0xFF003549)
val OnCyanContainerDark = Color(0xFFBFE9FF)

val AmberTertiaryDark = Color(0xFFFFB74D)
val AmberContainerDark = Color(0xFF4A2800)
val OnAmberContainerDark = Color(0xFFFFDDB3)

val CoralErrorDark = Color(0xFFFF6B6B)
val CoralContainerDark = Color(0xFF5C1111)

// Rückwärtskompatible Farb-Konstanten für Screens & Custom-Canvas
val AnthraciteDeep = SlateBackgroundDark
val AnthraciteSurface = SlateSurfaceContainerDark
val AnthraciteCard = SlateSurfaceElevatedDark
val AnthraciteBorder = SlateOutlineDark

val EmeraldPrimary = MintPrimaryDark
val EmeraldBright = MintAccentBright
val EmeraldContainer = MintContainerDark

val CyanTelemetry = CyanSecondaryDark
val AmberWarning = AmberTertiaryDark
val AmberContainer = AmberContainerDark

val CrimsonAlert = CoralErrorDark
val CrimsonContainer = CoralContainerDark

val TextOffWhite = Color(0xFFF1F5F9)
val TextSlateMuted = Color(0xFF94A3B8)

private val ProofSealMaterial3DarkScheme = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = Color(0xFF00210C),
    primaryContainer = MintContainerDark,
    onPrimaryContainer = OnMintContainerDark,
    secondary = CyanSecondaryDark,
    onSecondary = Color(0xFF001F2B),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = OnCyanContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = Color(0xFF2B1700),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = SlateBackgroundDark,
    onBackground = Color(0xFFE3E8EE),
    surface = SlateSurfaceDark,
    onSurface = Color(0xFFE3E8EE),
    surfaceVariant = SlateSurfaceElevatedDark,
    onSurfaceVariant = Color(0xFFB8C4D0),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF131920),
    surfaceContainer = SlateSurfaceContainerDark,
    surfaceContainerHigh = Color(0xFF222B35),
    surfaceContainerHighest = Color(0xFF2C3744),
    error = CoralErrorDark,
    onError = Color(0xFF3B0000),
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
    outline = SlateOutlineDark,
    outlineVariant = Color(0xFF25303C)
)

private val ProofSealMaterial3LightScheme = lightColorScheme(
    primary = Color(0xFF006D36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9AF6B5),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF00658B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5E7FF),
    onSecondaryContainer = Color(0xFF001E2D),
    tertiary = Color(0xFF855300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB7),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFF6F9FC),
    onBackground = Color(0xFF171C20),
    surface = Color(0xFFF6F9FC),
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E9EE),
    surfaceContainerHighest = Color(0xFFDEE3E9),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE)
)

@Composable
fun ProofSealTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ProofSealMaterial3DarkScheme
        else -> ProofSealMaterial3LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
