package org.hdhmc.saki.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.rememberDynamicColorScheme
import org.hdhmc.saki.domain.model.SakiPaletteStyle
import org.hdhmc.saki.domain.model.ThemeStyle

/** Brand seed color; all roles are generated from this so the full M3 role set stays consistent. */
private val BrandSeedColor = HarborBlue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SakiAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeStyle: ThemeStyle = ThemeStyle.SAKI,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    // Optional user-selected seed (currently exposed only for the Material Expressive style).
    seedColor: Color? = null,
    // Palette style for the Material Expressive theme.
    paletteStyle: SakiPaletteStyle = SakiPaletteStyle.TONAL_SPOT,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSakiVisualTokens provides sakiVisualTokens(themeStyle),
        LocalSakiPaletteStyle provides paletteStyle,
    ) {
        when (themeStyle) {
            ThemeStyle.SAKI -> {
                MaterialTheme(
                    colorScheme = sakiColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor),
                    typography = Typography,
                    shapes = SakiShapes,
                    content = content,
                )
            }

            ThemeStyle.MATERIAL_EXPRESSIVE -> {
                val scheme = rememberSakiExpressiveColorScheme(
                    seedColor = seedColor ?: BrandSeedColor,
                    isDark = darkTheme,
                    paletteStyle = paletteStyle,
                )
                MaterialExpressiveTheme(
                    colorScheme = scheme,
                    motionScheme = MotionScheme.expressive(),
                    content = content,
                )
            }
        }
    }
}

private fun sakiVisualTokens(themeStyle: ThemeStyle) = when (themeStyle) {
    ThemeStyle.SAKI -> DefaultSakiVisualTokens
    ThemeStyle.MATERIAL_EXPRESSIVE -> MaterialExpressiveSakiVisualTokens
}

@Composable
private fun sakiColorScheme(darkTheme: Boolean, dynamicColor: Boolean) = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    else -> rememberDynamicColorScheme(seedColor = BrandSeedColor, isDark = darkTheme)
}

/** Maps the persisted palette-style preference to MaterialKolor's [PaletteStyle]. */
private fun SakiPaletteStyle.toMaterialKolor(): PaletteStyle = when (this) {
    SakiPaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    SakiPaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    SakiPaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
}

/**
 * The Material Expressive color scheme exactly as the app renders it (selected palette style,
 * SPEC_2025, global chroma dial). Exposed so the Settings color picker can preview each seed's
 * primary/secondary pairing faithfully.
 */
@Composable
fun rememberSakiExpressiveColorScheme(
    seedColor: Color,
    isDark: Boolean,
    paletteStyle: SakiPaletteStyle,
): ColorScheme = rememberDynamicColorScheme(
    seedColor = seedColor,
    isDark = isDark,
    style = paletteStyle.toMaterialKolor(),
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
).desaturate(SchemeChromaScale)

/**
 * Global saturation dial applied on top of whichever palette style is active. MaterialKolor's
 * styles run a bit hot for our taste (our calmest style sits near other apps' "vibrant"), so we
 * scale the HCT chroma of every visible role down. Hue and tone are left untouched, so tonal
 * contrast — notably card vs background legibility — is fully preserved.
 */
private const val SchemeChromaScale = 0.7

private fun ColorScheme.desaturate(scale: Double): ColorScheme {
    if (scale >= 1.0) return this
    return copy(
        primary = primary.scaleChroma(scale),
        primaryContainer = primaryContainer.scaleChroma(scale),
        inversePrimary = inversePrimary.scaleChroma(scale),
        secondary = secondary.scaleChroma(scale),
        secondaryContainer = secondaryContainer.scaleChroma(scale),
        tertiary = tertiary.scaleChroma(scale),
        tertiaryContainer = tertiaryContainer.scaleChroma(scale),
        background = background.scaleChroma(scale),
        surface = surface.scaleChroma(scale),
        surfaceDim = surfaceDim.scaleChroma(scale),
        surfaceBright = surfaceBright.scaleChroma(scale),
        surfaceContainerLowest = surfaceContainerLowest.scaleChroma(scale),
        surfaceContainerLow = surfaceContainerLow.scaleChroma(scale),
        surfaceContainer = surfaceContainer.scaleChroma(scale),
        surfaceContainerHigh = surfaceContainerHigh.scaleChroma(scale),
        surfaceContainerHighest = surfaceContainerHighest.scaleChroma(scale),
        surfaceVariant = surfaceVariant.scaleChroma(scale),
        surfaceTint = surfaceTint.scaleChroma(scale),
        outline = outline.scaleChroma(scale),
        outlineVariant = outlineVariant.scaleChroma(scale),
    )
}

/** Scales a color's HCT chroma by [scale], keeping its hue and tone. */
private fun Color.scaleChroma(scale: Double): Color {
    val hct = Hct.fromInt(toArgb())
    return Color(Hct.from(hct.hue, hct.chroma * scale, hct.tone).toInt())
}
