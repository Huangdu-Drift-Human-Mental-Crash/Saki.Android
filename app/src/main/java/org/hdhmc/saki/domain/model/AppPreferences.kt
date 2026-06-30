package org.hdhmc.saki.domain.model

import java.util.Locale

enum class TextScale(
    val storageKey: String,
    val label: String,
    val multiplier: Float,
) {
    EXTRA_SMALL(
        storageKey = "extra_small",
        label = "Extra small",
        multiplier = 0.8f,
    ),
    SMALL(
        storageKey = "small",
        label = "Small",
        multiplier = 0.92f,
    ),
    DEFAULT(
        storageKey = "default",
        label = "Default",
        multiplier = 1.0f,
    ),
    LARGE(
        storageKey = "large",
        label = "Large",
        multiplier = 1.12f,
    ),
    EXTRA_LARGE(
        storageKey = "extra_large",
        label = "Extra large",
        multiplier = 1.24f,
    );

    companion object {
        fun fromStorageKey(storageKey: String?): TextScale {
            return entries.firstOrNull { it.storageKey == storageKey } ?: DEFAULT
        }
    }
}

data class AppPreferences(
    val textScale: TextScale = TextScale.DEFAULT,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.SAKI,
    val themeSeedKey: String = DEFAULT_THEME_SEED_KEY,
    val paletteStyle: SakiPaletteStyle = SakiPaletteStyle.TONAL_SPOT,
    val albumViewMode: AlbumViewMode = AlbumViewMode.GRID,
    val defaultBrowseTab: DefaultBrowseTab = DefaultBrowseTab.ARTISTS,
    val defaultAlbumFeed: AlbumListType = AlbumListType.NEWEST,
    val songsPageSize: Int = DEFAULT_SONGS_PAGE_SIZE,
    val hideMergedArtists: Boolean = false,
    val lastSelectedServerId: Long? = null,
    val recentSearchQueries: List<String> = emptyList(),
)

/** Default theme seed preset key; see [org.hdhmc.saki.ui.theme.SakiThemePresets]. */
const val DEFAULT_THEME_SEED_KEY = "harbor_blue"

const val MIN_SONGS_PAGE_SIZE = 250
const val MAX_SONGS_PAGE_SIZE = 3_000
const val SONGS_PAGE_SIZE_STEP = 250
const val DEFAULT_SONGS_PAGE_SIZE = 500

fun Int.normalizeSongsPageSize(): Int {
    val clamped = coerceIn(MIN_SONGS_PAGE_SIZE, MAX_SONGS_PAGE_SIZE)
    val stepsFromMin = ((clamped - MIN_SONGS_PAGE_SIZE) / SONGS_PAGE_SIZE_STEP.toDouble()).toInt()
    val lower = MIN_SONGS_PAGE_SIZE + (stepsFromMin * SONGS_PAGE_SIZE_STEP)
    val upper = (lower + SONGS_PAGE_SIZE_STEP).coerceAtMost(MAX_SONGS_PAGE_SIZE)
    return if (clamped - lower <= upper - clamped) lower else upper
}

enum class DefaultBrowseTab(val storageKey: String) {
    ARTISTS("artists"),
    ALBUMS("albums"),
    PLAYLISTS("playlists"),
    SONGS("songs");

    companion object {
        fun fromStorageKey(storageKey: String?): DefaultBrowseTab =
            entries.firstOrNull { it.storageKey == storageKey } ?: ARTISTS
    }
}

enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    CHINESE("zh");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

fun AppLanguage.indexingLocale(): Locale = when (this) {
    AppLanguage.SYSTEM -> Locale.getDefault()
    AppLanguage.ENGLISH -> Locale.ENGLISH
    AppLanguage.CHINESE -> Locale.CHINESE
}

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageKey(storageKey: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == storageKey } ?: SYSTEM
    }
}

enum class ThemeStyle(val storageKey: String) {
    SAKI("saki"),
    MATERIAL_EXPRESSIVE("material_expressive");

    companion object {
        fun fromStorageKey(storageKey: String?): ThemeStyle =
            entries.firstOrNull { it.storageKey == storageKey } ?: SAKI
    }
}

/**
 * Palette style for the Material Expressive theme. Maps to MaterialKolor's `PaletteStyle`
 * in the theme layer. TonalSpot = calm/analogous, Vibrant = vivid but harmonious,
 * Expressive = high-vibrancy with unexpected (often complementary) accents.
 */
enum class SakiPaletteStyle(val storageKey: String) {
    TONAL_SPOT("tonal_spot"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive");

    companion object {
        fun fromStorageKey(storageKey: String?): SakiPaletteStyle =
            entries.firstOrNull { it.storageKey == storageKey } ?: TONAL_SPOT
    }
}

enum class AlbumViewMode(val storageKey: String) {
    GRID("grid"),
    LIST("list");

    companion object {
        fun fromStorageKey(storageKey: String?): AlbumViewMode =
            entries.firstOrNull { it.storageKey == storageKey } ?: GRID
    }
}
