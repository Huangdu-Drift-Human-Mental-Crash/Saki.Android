package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.SakiPaletteStyle
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.domain.model.ThemeStyle
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun getPreferences(): AppPreferences

    suspend fun updateTextScale(textScale: TextScale)

    suspend fun updateLanguage(language: AppLanguage)

    suspend fun updateThemeMode(themeMode: ThemeMode)

    suspend fun updateThemeStyle(themeStyle: ThemeStyle)

    suspend fun updateThemeSeed(seedKey: String)

    suspend fun updatePaletteStyle(style: SakiPaletteStyle)

    suspend fun updateAlbumViewMode(mode: AlbumViewMode)

    suspend fun updateDefaultBrowseTab(tab: DefaultBrowseTab)

    suspend fun updateDefaultAlbumFeed(feed: AlbumListType)

    suspend fun updateSongsPageSize(pageSize: Int)

    suspend fun updateHideMergedArtists(enabled: Boolean)

    suspend fun updateLastSelectedServerId(serverId: Long?)

    suspend fun addRecentSearchQuery(query: String)

    suspend fun removeRecentSearchQuery(query: String)

    suspend fun clearRecentSearchQueries()
}
