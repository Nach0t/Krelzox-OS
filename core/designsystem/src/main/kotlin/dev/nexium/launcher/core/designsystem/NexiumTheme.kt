package dev.nexium.launcher.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val NexiumColorScheme = darkColorScheme(
    primary = NexiumColors.IonStart,
    onPrimary = NexiumColors.OnBackground,
    secondary = NexiumColors.IonEnd,
    tertiary = NexiumColors.Champagne,
    background = NexiumColors.Background,
    onBackground = NexiumColors.OnBackground,
    surface = NexiumColors.Surface,
    onSurface = NexiumColors.OnBackground,
    surfaceVariant = NexiumColors.SurfaceRaised,
    onSurfaceVariant = NexiumColors.OnSurfaceMuted,
    border = NexiumColors.IonStart,
    error = NexiumColors.Error,
)

// System sans-serif for now; the bundled brand typeface (Inter/Manrope) arrives in stage 4.
private val BrandFamily = FontFamily.SansSerif

private val NexiumTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = BrandFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = BrandFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BrandFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BrandFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BrandFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BrandFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

@Composable
fun NexiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexiumColorScheme,
        typography = NexiumTypography,
        content = content,
    )
}
