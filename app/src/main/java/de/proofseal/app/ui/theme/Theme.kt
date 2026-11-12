package de.proofseal.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ProofSeal Dunkles Sicherheits-Farbschema (Anthrazit, Smaragdgrün, Bernstein, Korallenrot)
val AnthraciteDeep = Color(0xFF0B0F17)
val AnthraciteSurface = Color(0xFF131B2E)
val AnthraciteCard = Color(0xFF1A243B)
val AnthraciteBorder = Color(0xFF283552)

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldBright = Color(0xFF34D399)
val EmeraldContainer = Color(0xFF064E3B)

val CyanTelemetry = Color(0xFF38BDF8)
val AmberWarning = Color(0xFFF59E0B)
val AmberContainer = Color(0xFF451A03)

val CrimsonAlert = Color(0xFFEF4444)
val CrimsonContainer = Color(0xFF450A0A)

val TextOffWhite = Color(0xFFF8FAFC)
val TextSlateMuted = Color(0xFF94A3B8)

private val ProofSealDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldBright,
    secondary = CyanTelemetry,
    onSecondary = Color(0xFF082F49),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    background = AnthraciteDeep,
    onBackground = TextOffWhite,
    surface = AnthraciteSurface,
    onSurface = TextOffWhite,
    surfaceVariant = AnthraciteCard,
    onSurfaceVariant = TextSlateMuted,
    error = CrimsonAlert,
    onError = Color.White,
    outline = AnthraciteBorder
)

@Composable
fun ProofSealTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProofSealDarkColorScheme,
        content = content
    )
}
