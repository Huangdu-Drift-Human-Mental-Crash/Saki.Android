package org.hdhmc.saki.domain.model

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
    val albumViewMode: AlbumViewMode = AlbumViewMode.GRID,
    val defaultBrowseTab: DefaultBrowseTab = DefaultBrowseTab.ARTISTS,
    val defaultAlbumFeed: AlbumListType = AlbumListType.NEWEST,
    val songsPageSize: Int = DEFAULT_SONGS_PAGE_SIZE,
    val lastSelectedServerId: Long? = null,
    val recentSearchQueries: List<String> = emptyList(),
)

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
    SAKI("saki");

    companion object {
        fun fromStorageKey(storageKey: String?): ThemeStyle =
            entries.firstOrNull { it.storageKey == storageKey } ?: SAKI
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
