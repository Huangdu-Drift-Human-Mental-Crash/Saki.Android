package org.hdhmc.saki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct
import org.hdhmc.saki.domain.model.SakiPaletteStyle

/** Brand seed color; all roles are generated from this so the full M3 role set stays consistent. */
private val BrandSeedColor = HarborBlue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SakiAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color? = null,
    paletteStyle: SakiPaletteStyle = SakiPaletteStyle.TONAL_SPOT,
    // Source the scheme from the system (Material You) palette instead of [seedColor].
    // Honored only on Android 12+; ignored otherwise.
    useSystemColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // System color is fed in as a seed (not the platform scheme) so the selected palette style
    // still applies and the app's calm tuning stays consistent.
    val seed = if (useSystemColor) {
        systemDynamicSeedColor(LocalContext.current)
    } else {
        seedColor ?: BrandSeedColor
    }
    val scheme = rememberSakiExpressiveColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        paletteStyle = paletteStyle,
    )
    CompositionLocalProvider(
        LocalSakiVisualTokens provides DefaultSakiVisualTokens,
        LocalSakiPaletteStyle provides paletteStyle,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
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
): ColorScheme = remember(seedColor, isDark, paletteStyle) {
    sakiExpressiveColorScheme(seedColor, isDark, paletteStyle)
}

/**
 * Non-composable counterpart to [rememberSakiExpressiveColorScheme]. Exposed so previews — notably
 * the Settings color picker, which needs every preset's scheme at once — can compute and memoize
 * them inside a single `remember` block, keeping the expensive desaturation off the recomposition
 * path (the jank #299 addressed) while still previewing the real generated primary/secondary pair.
 */
fun sakiExpressiveColorScheme(
    seedColor: Color,
    isDark: Boolean,
    paletteStyle: SakiPaletteStyle,
): ColorScheme = dynamicColorScheme(
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
