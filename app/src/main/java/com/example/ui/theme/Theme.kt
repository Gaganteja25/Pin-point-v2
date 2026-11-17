package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PinpointLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = PureWhite,
    primaryContainer = ElectricBlueSoft,
    onPrimaryContainer = DarkNavy,
    secondary = DarkNavy,
    onSecondary = PureWhite,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = DarkNavy,
    tertiary = CyanAccent,
    onTertiary = PureWhite,
    background = LightGrey,
    onBackground = TextPrimaryLight,
    surface = LightCardSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = SlateBorder,
    error = CrimsonAlert,
    onError = PureWhite,
    errorContainer = CrimsonSoft,
    onErrorContainer = CrimsonAlert
)

private val PinpointDarkColorScheme = darkColorScheme(
    primary = ElectricBlueBright,
    onPrimary = PureWhite,
    primaryContainer = ElectricBlueDarkContainer,
    onPrimaryContainer = PureWhite,
    secondary = CyanAccent,
    onSecondary = DarkNavy,
    secondaryContainer = NavyElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = EmeraldLive,
    onTertiary = DarkNavy,
    background = DarkNavy,
    onBackground = TextPrimaryDark,
    surface = NavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = NavyBorder,
    error = CrimsonAlert,
    onError = PureWhite,
    errorContainer = NavyElevated,
    onErrorContainer = CrimsonAlert
)

val PinpointShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PinpointTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PinpointDarkColorScheme else PinpointLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PinpointTypography,
        shapes = PinpointShapes,
        content = content
    )
}
