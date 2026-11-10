package dev.nexium.launcher.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val NexiumColorScheme = darkColorScheme(
    primary = NexiumColors.Electric,
    onPrimary = NexiumColors.OnBackground,
    secondary = NexiumColors.Cyan,
    tertiary = NexiumColors.Cyan,
    background = NexiumColors.Background,
    onBackground = NexiumColors.OnBackground,
    surface = NexiumColors.Surface,
    onSurface = NexiumColors.OnBackground,
    surfaceVariant = NexiumColors.SurfaceRaised,
    onSurfaceVariant = NexiumColors.OnSurfaceMuted,
    border = NexiumColors.Electric,
    error = NexiumColors.Error,
)

private fun inter(weight: Int): Font =
    @OptIn(ExperimentalTextApi::class)
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

/** Inter (SIL OFL 1.1), bundled as a single variable font. See third_party/inter/OFL.txt. */
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

private val NexiumTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.01).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.14.em,
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
