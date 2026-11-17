package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AetherDarkColorScheme = darkColorScheme(
    primary = PhosphorEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = GunmetalElevated,
    onPrimaryContainer = PhosphorEmerald,
    secondary = ElectricAmber,
    onSecondary = ObsidianBg,
    secondaryContainer = GunmetalElevated,
    onSecondaryContainer = ElectricAmber,
    tertiary = TacticalCyan,
    onTertiary = ObsidianBg,
    error = CrimsonPulse,
    onError = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = GunmetalPanel,
    onSurface = TextPrimary,
    surfaceVariant = GunmetalElevated,
    onSurfaceVariant = TextSecondary,
    outline = PanelBorder,
    outlineVariant = PanelBorderBright
)

val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun AetherMeshTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AetherDarkColorScheme,
        typography = AetherTypography,
        shapes = AetherShapes,
        content = content
    )
}
