package org.hdhmc.saki.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import org.hdhmc.saki.R

/**
 * A selectable seed color for the Material Expressive theme.
 *
 * Each seed is fed through `rememberDynamicColorScheme` (Expressive / SPEC_2025) in
 * [SakiAndroidTheme]. The global tuning we settled on — the dark-mode secondary→primary
 * remap and the calmer `selectedContainerAlpha` — is applied to the generated scheme
 * regardless of seed, so every preset inherits the same "calm, on-brand" footing instead
 * of leaning on whatever raw tones a seed happens to produce.
 *
 * [key] is the stable identifier persisted as the `themeSeedKey` preference; [nameRes] is
 * the localized display label shown by the Settings theme-color picker.
 */
data class SakiThemePreset(val key: String, val seed: Color, @param:StringRes val nameRes: Int)

/**
 * Curated presets spanning the hue wheel. [HarborBlue] stays first as the brand default.
 * Seeds are mid-chroma on purpose: the Expressive palette derives better-balanced tonal
 * ranges from a mid-tone source than from a fully saturated or very dark one.
 */
val SakiThemePresets: List<SakiThemePreset> = listOf(
    SakiThemePreset("harbor_blue", HarborBlue, R.string.settings_theme_seed_harbor_blue),
    SakiThemePreset("sakura", Color(0xFFB5436E), R.string.settings_theme_seed_sakura),
    SakiThemePreset("wisteria", Color(0xFF6A4FA3), R.string.settings_theme_seed_wisteria),
    SakiThemePreset("matcha", Color(0xFF5C7A2E), R.string.settings_theme_seed_matcha),
    SakiThemePreset("jade", Color(0xFF1E7D6A), R.string.settings_theme_seed_jade),
    SakiThemePreset("ember", Color(0xFFB5612A), R.string.settings_theme_seed_ember),
    SakiThemePreset("ruby", Color(0xFFB0364A), R.string.settings_theme_seed_ruby),
)

/** Resolves a stored preset key to its seed color, falling back to the brand seed. */
fun seedColorForKey(key: String?): Color =
    SakiThemePresets.firstOrNull { it.key == key }?.seed ?: HarborBlue

/**
 * Sentinel seed key for "follow the system" / Material You dynamic color (Android 12+). When this
 * is the active [org.hdhmc.saki.domain.model.AppPreferences.themeSeedKey], the theme sources its
 * colors from the platform dynamic scheme instead of a fixed preset seed.
 */
const val SYSTEM_DYNAMIC_THEME_SEED_KEY = "system"

/** True when [key] selects the system dynamic (Material You) color source. */
fun isSystemDynamicSeed(key: String?): Boolean = key == SYSTEM_DYNAMIC_THEME_SEED_KEY

/**
 * The system (Material You) accent used as a MaterialKolor seed, so the system color source still
 * flows through the same palette-style pipeline as the curated presets (and therefore honors the
 * selected [SakiPaletteStyle]). Falls back to the brand seed below Android 12, where dynamic color
 * is unavailable.
 */
fun systemDynamicSeedColor(context: Context): Color =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_500))
    } else {
        HarborBlue
    }
