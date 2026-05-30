package org.hdhmc.saki.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
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
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalSakiVisualTokens provides sakiVisualTokens(themeStyle)) {
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
                val base = rememberDynamicColorScheme(
                    seedColor = seedColor ?: BrandSeedColor,
                    isDark = darkTheme,
                    style = PaletteStyle.Expressive,
                    specVersion = ColorSpec.SpecVersion.SPEC_2025,
                )
                // In dark mode the Expressive secondary rotates to a heavy maroon that fights the
                // blue seed; remap the prominent secondary containers onto the primary family so
                // dark accents stay on-brand. Light keeps its soft pink secondary.
                val scheme = if (darkTheme) {
                    base.copy(
                        secondaryContainer = lerp(base.surfaceContainer, base.primaryContainer, 0.45f),
                        onSecondaryContainer = base.onSurface,
                    )
                } else {
                    base
                }
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
