package org.hdhmc.saki.presentation

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.hdhmc.saki.R
import org.hdhmc.saki.data.remote.EndpointSelector
import org.hdhmc.saki.data.repository.ConfigBackupManager
import org.hdhmc.saki.data.repository.ImportResult
import org.hdhmc.saki.presentation.library.BrowseNavRoute
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlacDecoderMode
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.AppLanguage
import org.hdhmc.saki.domain.model.AppPreferences
import org.hdhmc.saki.domain.model.ThemeMode
import org.hdhmc.saki.domain.model.SakiPaletteStyle
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.belongsToArtistInAlbum
import org.hdhmc.saki.domain.model.CacheStorageSummary
import org.hdhmc.saki.domain.model.CachedArtistDetail
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.CollectionStreamCacheEstimate
import org.hdhmc.saki.domain.model.CollectionStreamCacheTask
import org.hdhmc.saki.domain.model.DEFAULT_SONGS_PAGE_SIZE
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.indexingLocale
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshotSourceType
import org.hdhmc.saki.domain.model.regroupByLocale
import org.hdhmc.saki.domain.model.PlaybackProgressState
import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.PlaybackQueueItem
import org.hdhmc.saki.domain.model.OriginalPlaybackFailureAction
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SongFeedType
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.TextScale
import org.hdhmc.saki.domain.model.withoutUnknownAlbumPlaceholders
import org.hdhmc.saki.domain.model.withVisibleDetailAlbums
import org.hdhmc.saki.domain.repository.AppPreferencesRepository
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.LibraryCacheRepository
import org.hdhmc.saki.domain.repository.LocalPlayQueueRepository
import org.hdhmc.saki.domain.repository.PlaybackManager
import org.hdhmc.saki.playback.LyricsHolder
import org.hdhmc.saki.playback.AlacSystemDecoderSupport
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.ServerConfigRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import org.hdhmc.saki.domain.repository.StreamCacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.icu.text.Collator
import java.util.Locale

private const val ALBUMS_PAGE_SIZE = 36
private const val SORTED_ALBUMS_PAGE_SIZE = 240
private const val RANDOM_SONGS_FEED_SIZE = 200
private const val PLAYLIST_DETAIL_PREFETCH_LIMIT = 12
private const val PLAYLIST_DETAIL_PREFETCH_MAX_SONGS = 500
private const val SONG_METADATA_SYNC_PAGE_SIZE = 500
private const val SONGS_DISPLAY_WINDOW_SIZE = 5_000
private const val DEFERRED_STREAM_CACHE_SUMMARY_REFRESH_MS = 5_000L

@HiltViewModel
@OptIn(FlowPreview::class)
class SakiAppViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val serverConfigRepository: ServerConfigRepository,
    private val subsonicRepository: SubsonicRepository,
    private val cachedSongRepository: CachedSongRepository,
    private val streamCacheRepository: StreamCacheRepository,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val libraryCacheRepository: LibraryCacheRepository,
    private val localPlayQueueRepository: LocalPlayQueueRepository,
    private val playbackManager: PlaybackManager,
    private val lyricsHolder: LyricsHolder,
    private val endpointSelector: EndpointSelector,
    private val configBackupManager: ConfigBackupManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SakiAppUiState())
    private val snackbarMessages = MutableSharedFlow<SnackbarMessage>(extraBufferCapacity = 1)
    private val openNowPlayingRequestsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val searchQueryFlow = MutableStateFlow("")
    private val systemAlacDecoderSupported = MutableStateFlow<Boolean?>(null)
    private var lastLoadedServerId: Long? = null
    private var appliedDefaultBrowsePreference = false
    private var deferredStreamCacheSummaryJob: Job? = null
    private var lastReportedPlaybackFailureEventId = 0L
    private val sortedAlbumPrefetchJobs = mutableMapOf<AlbumListType, Job>()

    private val mutableEndpointStatus = MutableStateFlow(EndpointStatus())
    val endpointStatus: StateFlow<EndpointStatus> = mutableEndpointStatus.asStateFlow()

    val uiState = mutableUiState.asStateFlow()
    val rootUiState: StateFlow<SakiRootUiState> = mutableUiState
        .map(SakiAppUiState::toRootUiState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableUiState.value.toRootUiState())
    val browseUiState: StateFlow<SakiBrowseUiState> = mutableUiState
        .map(SakiAppUiState::toBrowseUiState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableUiState.value.toBrowseUiState())
    val browsePlaybackUiState: StateFlow<SakiBrowsePlaybackUiState> = mutableUiState
        .map(SakiAppUiState::toBrowsePlaybackUiState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableUiState.value.toBrowsePlaybackUiState())
    val browseAvailabilityUiState: StateFlow<SakiBrowseAvailabilityUiState> = mutableUiState
        .map(SakiAppUiState::toBrowseAvailabilityUiState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableUiState.value.toBrowseAvailabilityUiState())
    val settingsUiState: StateFlow<SakiSettingsUiState> = combine(
        mutableUiState,
        systemAlacDecoderSupported,
    ) { state, isSystemAlacDecoderSupported ->
        state.toSettingsUiState(isSystemAlacDecoderSupported)
    }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            mutableUiState.value.toSettingsUiState(isSystemAlacDecoderSupported = null),
        )
    val capsuleUiState: StateFlow<SakiCapsuleUiState> = mutableUiState
        .map(SakiAppUiState::toCapsuleUiState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableUiState.value.toCapsuleUiState())
    val nowPlayingUiState: StateFlow<SakiNowPlayingUiState> = mutableUiState
        .map(SakiAppUiState::toNowPlayingUiState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableUiState.value.toNowPlayingUiState())
    val playbackProgress: StateFlow<PlaybackProgressState> = playbackManager.playbackProgress
    val messages = snackbarMessages.asSharedFlow()
    val openNowPlayingRequests = openNowPlayingRequestsFlow.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            systemAlacDecoderSupported.value = AlacSystemDecoderSupport.isSupported
        }
        viewModelScope.launch {
            endpointSelector.probeVersion.collectLatest { refreshEndpointStatus() }
        }
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collectLatest { preferences ->
                val previousState = mutableUiState.value
                val shouldApplyDefaultBrowse = !appliedDefaultBrowsePreference
                val shouldRegroupIndexes = previousState.appPreferences.language != preferences.language
                val indexingLocale = preferences.language.indexingLocale()
                val regroupedLibraryIndexes = if (shouldRegroupIndexes) {
                    withContext(Dispatchers.Default) {
                        previousState.libraryIndexes?.regroupByLocale(indexingLocale)
                    }
                } else {
                    previousState.libraryIndexes
                }
                val sortedAlbumFeeds = if (shouldRegroupIndexes) {
                    previousState.albumFeeds.sortAlbumsForLocaleOnDefault(
                        locale = indexingLocale,
                        ignoredArticles = previousState.libraryIndexes?.ignoredArticles,
                    )
                } else {
                    previousState.albumFeeds
                }
                if (shouldApplyDefaultBrowse) {
                    appliedDefaultBrowsePreference = true
                }
                mutableUiState.update { state ->
                    val canApplyRegroupedContent = shouldRegroupIndexes &&
                        state.appPreferences.language == previousState.appPreferences.language &&
                        state.libraryIndexes === previousState.libraryIndexes &&
                        state.albumFeeds === previousState.albumFeeds
                    state.copy(
                        isAppReady = true,
                        textScale = preferences.textScale,
                        appPreferences = preferences,
                        libraryIndexes = if (canApplyRegroupedContent) {
                            regroupedLibraryIndexes
                        } else {
                            state.libraryIndexes
                        },
                        albumFeeds = if (canApplyRegroupedContent) {
                            sortedAlbumFeeds
                        } else {
                            state.albumFeeds
                        },
                        selectedBrowseSection = if (shouldApplyDefaultBrowse) {
                            preferences.defaultBrowseTab.toBrowseSection()
                        } else {
                            state.selectedBrowseSection
                        },
                        selectedAlbumFeed = if (shouldApplyDefaultBrowse) {
                            preferences.defaultAlbumFeed
                        } else {
                            state.selectedAlbumFeed
                        },
                    )
                }
                if (shouldApplyDefaultBrowse) {
                    uiState.value.selectedServerId?.let { serverId ->
                        loadBrowseSectionIfNeeded(serverId, preferences.defaultBrowseTab.toBrowseSection())
                    }
                }
                // Apply saved locale when preference changes (no-ops if already matching)
                if (preferences.language != AppLanguage.SYSTEM) {
                    val locales = androidx.core.os.LocaleListCompat.forLanguageTags(preferences.language.tag)
                    val current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                    if (current != locales) {
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                    }
                }
                // Apply saved theme mode
                val nightMode = preferences.themeMode.toNightMode()
                if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != nightMode) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
        }

        viewModelScope.launch {
            serverConfigRepository.observeServerConfigs().collectLatest { servers ->
                handleServerConfigsChanged(servers)
            }
        }

        viewModelScope.launch {
            cachedSongRepository.observeCachedSongs().collectLatest { songs ->
                mutableUiState.update { state ->
                    state.copy(cachedSongs = songs)
                }
                refreshCacheStorageSummary(uiState.value.selectedServerId)
            }
        }

        viewModelScope.launch {
            streamCacheRepository.observeCacheVersion().collectLatest {
                if (it > 0L) {
                    scheduleStreamCacheStorageSummaryRefresh(uiState.value.selectedServerId, delayMs = 500L)
                }
            }
        }

        viewModelScope.launch {
            streamCacheRepository.observeCollectionCacheTask().collectLatest { task ->
                mutableUiState.update { state ->
                    state.copy(collectionStreamCacheTask = task)
                }
            }
        }

        viewModelScope.launch {
            playbackManager.playbackState.collectLatest { playbackState ->
                mutableUiState.update { state ->
                    state.copy(playbackState = playbackState)
                }
                playbackState.failure
                    ?.takeIf { failure -> failure.eventId > lastReportedPlaybackFailureEventId }
                    ?.let { failure ->
                        lastReportedPlaybackFailureEventId = failure.eventId
                        snackbarMessages.emit(
                            SnackbarMessage(
                                text = failure.toUiText(),
                                duration = SnackbarDuration.Long,
                            ),
                        )
                    }
            }
        }

        // Fetch lyrics when current track changes
        viewModelScope.launch {
            playbackManager.playbackState
                .map { state ->
                    state.currentItem?.let {
                        val sid = it.serverId ?: return@let null
                        sid to it.songId
                    }
                }
                .distinctUntilChanged()
                .collectLatest { pair ->
                    if (pair == null) {
                        mutableUiState.update { it.copy(currentLyrics = null) }
                        lyricsHolder.update(null)
                        return@collectLatest
                    }
                    val (serverId, songId) = pair
                    mutableUiState.update { it.copy(currentLyrics = null) }
                    lyricsHolder.update(null)
                    try {
                        val lyrics = subsonicRepository.getLyrics(serverId, songId).data
                        mutableUiState.update { it.copy(currentLyrics = lyrics) }
                        lyricsHolder.update(lyrics)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Lyrics not available — silently ignore
                    }
                }
        }

        viewModelScope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.streamQuality }
                .distinctUntilChanged()
                .collectLatest {
                    refreshCacheStorageSummary(uiState.value.selectedServerId)
                }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(350)
                .map(String::trim)
                .distinctUntilChanged()
                .collectLatest(::performSearch)
        }
    }

    fun selectBrowseSection(section: BrowseSection) {
        mutableUiState.update { state ->
            state.copy(selectedBrowseSection = section)
        }
        val serverId = uiState.value.selectedServerId ?: return
        loadBrowseSectionIfNeeded(serverId, section)
    }

    private fun loadBrowseSectionIfNeeded(serverId: Long, section: BrowseSection) {
        when (section) {
            BrowseSection.ARTISTS -> if (
                uiState.value.libraryIndexes == null ||
                (!uiState.value.hasLoadedArtistsFromNetwork && !endpointStatus.value.isOfflineDegraded)
            ) {
                loadArtists(serverId)
            }
            BrowseSection.ALBUMS -> if (uiState.value.albums.isEmpty()) loadAlbums(serverId, uiState.value.selectedAlbumFeed)
            BrowseSection.PLAYLISTS -> if (uiState.value.playlists.isEmpty()) loadPlaylists(serverId)
            BrowseSection.SONGS -> if (
                uiState.value.songs.isEmpty() ||
                (!uiState.value.hasLoadedSongsFromNetwork && !endpointStatus.value.isOfflineDegraded)
            ) {
                loadSongs(serverId)
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        if (active) {
            mutableUiState.update { state ->
                state.copy(isSearchActive = true)
            }
            return
        }

        searchQueryFlow.value = ""
        mutableUiState.update { state ->
            state.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = SearchResults(),
                isSearchLoading = false,
                searchError = null,
            )
        }
    }

    fun updateSearchQuery(query: String) {
        mutableUiState.update { state ->
            state.copy(
                isSearchActive = true,
                searchQuery = query,
            )
        }
        searchQueryFlow.value = query
    }

    fun removeRecentSearchQuery(query: String) {
        viewModelScope.launch {
            runCatching { appPreferencesRepository.removeRecentSearchQuery(query) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w("SakiApp", "Failed to remove recent search query", throwable)
                }
        }
    }

    fun clearRecentSearchQueries() {
        viewModelScope.launch {
            runCatching { appPreferencesRepository.clearRecentSearchQueries() }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w("SakiApp", "Failed to clear recent search queries", throwable)
                }
        }
    }

    fun updateTextScale(textScale: TextScale) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateTextScale(textScale)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_text_size_set, UiText.resource(textScale.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_text_size)))
            }
        }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateLanguage(language)
            }.onSuccess {
                val locales = when (language) {
                    AppLanguage.SYSTEM -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                    else -> androidx.core.os.LocaleListCompat.forLanguageTags(language.tag)
                }
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }

    private fun ThemeMode.toNightMode(): Int = when (this) {
        ThemeMode.SYSTEM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
    }

    private fun currentIndexingLocale(): Locale =
        uiState.value.appPreferences.language.indexingLocale()

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.updateThemeMode(themeMode)
            }.onSuccess {
                val nightMode = themeMode.toNightMode()
                if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != nightMode) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
                }
            }
        }
    }

    fun updateThemeSeed(seedKey: String) {
        viewModelScope.launch {
            appPreferencesRepository.updateThemeSeed(seedKey)
        }
    }

    fun updatePaletteStyle(style: SakiPaletteStyle) {
        viewModelScope.launch {
            appPreferencesRepository.updatePaletteStyle(style)
        }
    }

    fun updateAlbumViewMode(mode: AlbumViewMode) {
        viewModelScope.launch {
            appPreferencesRepository.updateAlbumViewMode(mode)
        }
    }

    fun updateDefaultBrowseTab(tab: DefaultBrowseTab) {
        viewModelScope.launch {
            appPreferencesRepository.updateDefaultBrowseTab(tab)
        }
    }

    fun updateDefaultAlbumFeed(feed: AlbumListType) {
        viewModelScope.launch {
            appPreferencesRepository.updateDefaultAlbumFeed(feed)
        }
    }

    fun updateSongsPageSize(pageSize: Int) {
        viewModelScope.launch {
            appPreferencesRepository.updateSongsPageSize(pageSize)
        }
    }

    fun updateHideMergedArtists(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateHideMergedArtists(enabled)
            val serverId = uiState.value.selectedServerId ?: return@launch
            val artists = runCatching {
                libraryCacheRepository.getArtists(serverId, hideMergedArtists = enabled)
            }.getOrNull()
            if (artists != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update {
                    it.copy(libraryIndexes = artists.regroupByLocale(currentIndexingLocale()))
                }
            }
        }
    }

    fun openArtistFromPlayback(
        serverId: Long?,
        artistId: String?,
    ) {
        if (serverId == null || artistId.isNullOrBlank()) return

        if (uiState.value.selectedServerId != serverId) {
            selectServer(serverId)
        }
        mutableUiState.update { it.copy(browseStack = listOf(BrowseNavRoute.Root)) }
        openArtist(artistId)
    }

    fun openAlbumFromPlayback(
        serverId: Long?,
        albumId: String?,
    ) {
        if (serverId == null || albumId.isNullOrBlank()) return

        if (uiState.value.selectedServerId != serverId) {
            selectServer(serverId)
        }
        mutableUiState.update { it.copy(browseStack = listOf(BrowseNavRoute.Root)) }
        openAlbum(albumId)
    }

    fun selectServer(serverId: Long) {
        val previousServerId = uiState.value.selectedServerId
        if (previousServerId == serverId) return
        val server = uiState.value.servers.find { it.id == serverId } ?: return

        sortedAlbumPrefetchJobs.values.forEach { it.cancel() }
        sortedAlbumPrefetchJobs.clear()
        clearSearchState()
        mutableUiState.update { state ->
            state.copy(
                selectedServerId = serverId,
                browseStack = listOf(BrowseNavRoute.Root),
                selectedArtist = null,
                selectedArtistSongs = emptyList(),
                selectedArtistSongsAreTopSongs = true,
                albumFeeds = emptyAlbumFeedStates(),
                selectedAlbum = null,
                selectedPlaylist = null,
                songs = emptyList(),
                songsOffset = 0,
                hasPreviousSongs = false,
                hasMoreSongs = true,
                hasLoadedSongsFromNetwork = false,
                isSongsLoading = false,
                isSongsLoadingPrevious = false,
                isSongsLoadingMore = false,
                songsError = null,
            )
        }
        endpointSelector.registerServer(server)
        refreshEndpointStatus()
        viewModelScope.launch {
            endpointSelector.probe(serverId, server)
            refreshEndpointStatus()
        }
        viewModelScope.launch {
            appPreferencesRepository.updateLastSelectedServerId(serverId)
            refreshCacheStorageSummary(serverId)
        }
        loadServerContent(serverId, forceRefresh = true)
    }

    fun selectAlbumFeed(type: AlbumListType) {
        val serverId = uiState.value.selectedServerId ?: return
        mutableUiState.update { state ->
            state.copy(
                selectedAlbumFeed = type,
                albumFeeds = state.albumFeeds.updateFeed(type) { feedState ->
                    feedState.copy(
                        isLoadingMore = false,
                        error = null,
                    )
                },
            )
        }
        loadAlbums(serverId, type)
    }

    fun loadMoreAlbums() {
        val state = uiState.value
        val serverId = state.selectedServerId ?: return
        val type = state.selectedAlbumFeed
        val feedState = state.albumFeedState(type)
        val pageSize = type.albumFeedPageSize()
        if (
            !type.supportsPagination() ||
            !feedState.hasMore ||
            feedState.isLoading ||
            feedState.isLoadingMore ||
            sortedAlbumPrefetchJobs[type]?.isActive == true
        ) {
            return
        }
        val offset = feedState.offset

        viewModelScope.launch {
            mutableUiState.update { current ->
                current.copy(
                    albumFeeds = current.albumFeeds.updateFeed(type) {
                        it.copy(isLoadingMore = true, error = null)
                    },
                )
            }

            runCatching {
                subsonicRepository.getAlbumList(
                    serverId = serverId,
                    type = type,
                    size = pageSize,
                    offset = offset,
                ).data
            }.onSuccess { page ->
                if (uiState.value.selectedServerId == serverId) {
                    val current = uiState.value
                    val currentFeed = current.albumFeedState(type)
                    val visiblePage = page.withoutUnknownAlbumPlaceholders()
                    val mergedAlbums = (currentFeed.albums + visiblePage)
                        .distinctBy(AlbumSummary::id)
                        .sortedForAlbumFeedOnDefault(
                            type = type,
                            locale = current.appPreferences.language.indexingLocale(),
                            ignoredArticles = current.libraryIndexes?.ignoredArticles,
                        )
                    mutableUiState.update { latest ->
                        latest.copy(
                            albumFeeds = latest.albumFeeds.updateFeed(type) {
                                it.copy(
                                    albums = mergedAlbums,
                                    offset = offset + page.size,
                                    hasMore = page.size >= pageSize,
                                    isLoadingMore = false,
                                    error = null,
                                )
                            },
                        )
                    }
                    runCatching { libraryCacheRepository.saveAlbums(serverId, type, mergedAlbums) }
                        .onFailure { Log.w("SakiApp", "Failed to cache albums", it) }
                    if (type.supportsAlbumFastScroll() && page.size >= pageSize) {
                        startSortedAlbumPrefetch(serverId, type, pageSize)
                    }
                }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { current ->
                        current.copy(
                            albumFeeds = current.albumFeeds.updateFeed(type) {
                                it.copy(
                                    isLoadingMore = false,
                                    error = throwable.localizedOr(R.string.error_load_albums),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    fun loadMoreSongs() {
        val state = uiState.value
        val serverId = state.selectedServerId ?: return
        if (
            !state.hasMoreSongs ||
            state.isSongsLoading ||
            state.isSongsLoadingPrevious ||
            state.isSongsLoadingMore ||
            state.songs.isEmpty()
        ) {
            return
        }
        val pageSize = state.appPreferences.songsPageSize
        val offset = state.songsOffset + state.songs.size

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSongsLoadingMore = true, songsError = null) }

            val cachedAt = System.currentTimeMillis()
            try {
                var loadedFromNetwork = false
                val page = if (endpointStatus.value.isOfflineDegraded) {
                    loadCachedSongsPage(serverId, offset, pageSize)
                } else {
                    try {
                        fetchSongsPage(serverId, offset, cachedAt, pageSize).also {
                            loadedFromNetwork = true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (networkError: Throwable) {
                        val cachedPage = loadCachedSongsPage(serverId, offset, pageSize)
                        if (cachedPage.songs.isEmpty()) throw networkError
                        cachedPage
                    }
                }
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { current ->
                        val existingSongIds = current.songs.mapTo(HashSet(current.songs.size)) { it.id }
                        val newSongs = page.songs.excludingKnownSongIds(existingSongIds)
                        val mergedSongs = if (newSongs.isEmpty()) current.songs else current.songs + newSongs
                        val trimCount = (mergedSongs.size - SONGS_DISPLAY_WINDOW_SIZE).coerceAtLeast(0)
                        val windowSongs = if (trimCount > 0) mergedSongs.drop(trimCount) else mergedSongs
                        val windowOffset = current.songsOffset + trimCount
                        current.copy(
                            songs = windowSongs,
                            songsOffset = windowOffset,
                            hasPreviousSongs = windowOffset > 0,
                            hasMoreSongs = page.hasMore && newSongs.isNotEmpty(),
                            hasLoadedSongsFromNetwork = current.hasLoadedSongsFromNetwork || loadedFromNetwork,
                            isSongsLoadingMore = false,
                            songsError = null,
                        )
                    }
                }
                if (loadedFromNetwork) {
                    refreshCachedArtistIndex(serverId)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(
                            isSongsLoadingMore = false,
                            songsError = throwable.localizedOr(R.string.error_load_songs),
                        )
                    }
                }
            }
        }
    }

    fun loadPreviousSongs() {
        val state = uiState.value
        val serverId = state.selectedServerId ?: return
        if (
            !state.hasPreviousSongs ||
            state.isSongsLoading ||
            state.isSongsLoadingPrevious ||
            state.isSongsLoadingMore ||
            state.songs.isEmpty()
        ) {
            return
        }
        val pageSize = state.appPreferences.songsPageSize
        val loadSize = minOf(pageSize, state.songsOffset)
        if (loadSize <= 0) return
        val offset = state.songsOffset - loadSize

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSongsLoadingPrevious = true, songsError = null) }

            val cachedAt = System.currentTimeMillis()
            try {
                var loadedFromNetwork = false
                val page = if (endpointStatus.value.isOfflineDegraded) {
                    loadCachedSongsPage(serverId, offset, loadSize)
                } else {
                    try {
                        fetchSongsPage(serverId, offset, cachedAt, loadSize).also {
                            loadedFromNetwork = true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (networkError: Throwable) {
                        val cachedPage = loadCachedSongsPage(serverId, offset, loadSize)
                        if (cachedPage.songs.isEmpty()) throw networkError
                        cachedPage
                    }
                }
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { current ->
                        if (page.songs.isEmpty()) {
                            return@update current.copy(
                                hasPreviousSongs = false,
                                isSongsLoadingPrevious = false,
                                songsError = null,
                            )
                        }
                        val existingSongIds = current.songs.mapTo(HashSet(current.songs.size)) { it.id }
                        val previousSongs = page.songs.excludingKnownSongIds(existingSongIds)
                        if (previousSongs.isEmpty()) {
                            return@update current.copy(
                                hasPreviousSongs = false,
                                isSongsLoadingPrevious = false,
                                songsError = null,
                            )
                        }
                        val mergedSongs = previousSongs + current.songs
                        val trimCount = (mergedSongs.size - SONGS_DISPLAY_WINDOW_SIZE).coerceAtLeast(0)
                        val windowSongs = if (trimCount > 0) mergedSongs.dropLast(trimCount) else mergedSongs
                        val windowOffset = (current.songsOffset - previousSongs.size).coerceAtLeast(0)
                        current.copy(
                            songs = windowSongs,
                            songsOffset = windowOffset,
                            hasPreviousSongs = windowOffset > 0,
                            hasMoreSongs = current.hasMoreSongs || trimCount > 0,
                            hasLoadedSongsFromNetwork = current.hasLoadedSongsFromNetwork || loadedFromNetwork,
                            isSongsLoadingPrevious = false,
                            songsError = null,
                        )
                    }
                }
                if (loadedFromNetwork) {
                    refreshCachedArtistIndex(serverId)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(
                            isSongsLoadingPrevious = false,
                            songsError = throwable.localizedOr(R.string.error_load_songs),
                        )
                    }
                }
            }
        }
    }

    fun updateAllSongMetadata() {
        val serverId = uiState.value.selectedServerId ?: return
        if (uiState.value.isSongMetadataSyncing) return
        if (endpointStatus.value.isOfflineDegraded) {
            snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.error_update_song_metadata)))
            return
        }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isSongMetadataSyncing = true,
                    songMetadataSyncCount = 0,
                )
            }
            try {
                val syncedCount = syncAllSongMetadata(serverId)
                refreshCachedArtistIndex(serverId)
                mutableUiState.update {
                    it.copy(
                        isSongMetadataSyncing = false,
                        songMetadataSyncCount = syncedCount,
                    )
                }
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_song_metadata_updated, syncedCount)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("SakiApp", "Failed to update song metadata", e)
                mutableUiState.update {
                    it.copy(isSongMetadataSyncing = false)
                }
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_update_song_metadata)))
            }
        }
    }

    fun refreshCurrentTab() {
        val serverId = uiState.value.selectedServerId ?: return
        when (uiState.value.selectedBrowseSection) {
            BrowseSection.ARTISTS -> loadArtists(serverId, forceRefresh = true)
            BrowseSection.ALBUMS -> loadAlbums(serverId, uiState.value.selectedAlbumFeed, forceRefresh = true)
            BrowseSection.PLAYLISTS -> loadPlaylists(serverId, forceRefresh = true)
            BrowseSection.SONGS -> if (uiState.value.selectedSongFeed == SongFeedType.RANDOM) {
                refreshRandomSongs()
            } else {
                loadSongs(serverId, forceRefresh = true)
            }
        }
    }

    fun openArtist(artistId: String) {
        val serverId = uiState.value.selectedServerId ?: return
        val fallbackArtist = uiState.value.findArtistSummary(artistId)?.toArtist()
        mutableUiState.update { state ->
            state.copy(
                selectedArtist = fallbackArtist,
                selectedArtistSongs = emptyList(),
                selectedArtistSongsAreTopSongs = true,
                isArtistLoading = true,
                artistError = null,
                selectedAlbum = null,
                browseStack = state.browseStack.pushed(BrowseNavRoute.ArtistDetail(artistId)),
            )
        }

        viewModelScope.launch {
            val cached = runCatching { libraryCacheRepository.getArtistDetail(serverId, artistId) }.getOrNull()
            if (cached != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { state ->
                    state.copy(
                        selectedArtist = cached.artist,
                        selectedArtistSongs = cached.songs,
                        selectedArtistSongsAreTopSongs = cached.songsAreTopSongs,
                        isArtistLoading = !endpointStatus.value.isOfflineDegraded,
                        artistError = null,
                    )
                }
            }
            if (endpointStatus.value.isOfflineDegraded) {
                if (cached == null && fallbackArtist == null) {
                    snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_cached_detail_unavailable)))
                } else if (cached == null) {
                    mutableUiState.update { state ->
                        state.copy(artistError = UiText.resource(R.string.error_cached_detail_unavailable))
                    }
                }
                mutableUiState.update { state -> state.copy(isArtistLoading = false) }
                return@launch
            }
            runCatching {
                val artist = subsonicRepository.getArtist(serverId, artistId).data
                val topSongs = buildArtistTopSongs(serverId, artist)
                val relationshipDetail = libraryCacheRepository.getArtistDetail(serverId, artistId)
                val relationshipSongs = relationshipDetail?.songs.orEmpty()
                val displayArtist = (relationshipDetail?.artist?.let { localArtist ->
                    artist.copy(
                        name = localArtist.name,
                        coverArtId = artist.coverArtId ?: localArtist.coverArtId,
                        artistImageUrl = artist.artistImageUrl ?: localArtist.artistImageUrl,
                        albumCount = artist.albumCount ?: localArtist.albumCount,
                        // Union the server's albums with relationship-derived albums (e.g. albums
                        // the server credits to a combined artist but whose tracks credit this
                        // split artist) so the network refresh does not drop them.
                        albums = (artist.albums + localArtist.albums).distinctBy(AlbumSummary::id),
                    )
                } ?: artist).withVisibleDetailAlbums()
                val songs = (topSongs + relationshipSongs).distinctBy(Song::id)
                Triple(displayArtist, songs, false)
            }.onSuccess { (artist, songs, songsAreTopSongs) ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            selectedArtist = artist,
                            selectedArtistSongs = songs,
                            selectedArtistSongsAreTopSongs = songsAreTopSongs,
                            isArtistLoading = false,
                            artistError = null,
                        )
                    }
                }
                runCatching {
                    libraryCacheRepository.saveArtistDetail(
                        serverId = serverId,
                        detail = CachedArtistDetail(artist = artist, songs = songs, songsAreTopSongs = songsAreTopSongs),
                    )
                    refreshCachedArtistIndex(serverId)
                }.onFailure { Log.w("SakiApp", "Failed to cache artist detail", it) }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            isArtistLoading = false,
                            artistError = if (cached == null && fallbackArtist == null) {
                                throwable.localizedOr(R.string.error_load_artist_details)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    private fun List<BrowseNavRoute>.pushed(route: BrowseNavRoute): List<BrowseNavRoute> =
        if (lastOrNull() == route) this else this + route

    /** Pops the top Browse route and clears that page's detail data. No-op at the root. */
    fun popBrowseRoute() {
        mutableUiState.update { state ->
            if (state.browseStack.size <= 1) return@update state
            val newStack = state.browseStack.dropLast(1)
            when (state.browseStack.last()) {
                is BrowseNavRoute.AlbumDetail -> state.copy(
                    browseStack = newStack,
                    selectedAlbum = null,
                    albumError = null,
                )
                is BrowseNavRoute.ArtistDetail -> state.copy(
                    browseStack = newStack,
                    selectedArtist = null,
                    selectedArtistSongs = emptyList(),
                    selectedArtistSongsAreTopSongs = true,
                    selectedAlbum = null,
                    artistError = null,
                    albumError = null,
                )
                is BrowseNavRoute.PlaylistDetail -> state.copy(
                    browseStack = newStack,
                    selectedPlaylist = null,
                    playlistError = null,
                )
                BrowseNavRoute.Root -> state.copy(browseStack = newStack)
            }
        }
    }

    fun openAlbum(albumId: String) {
        val serverId = uiState.value.selectedServerId ?: return
        val fallbackAlbum = uiState.value.findAlbumSummary(albumId)?.toAlbum()
        mutableUiState.update { state ->
            state.copy(
                selectedAlbum = fallbackAlbum,
                isAlbumLoading = true,
                albumError = null,
                browseStack = state.browseStack.pushed(BrowseNavRoute.AlbumDetail(albumId)),
            )
        }

        viewModelScope.launch {
            val cached = runCatching { libraryCacheRepository.getAlbumDetail(serverId, albumId) }.getOrNull()
            if (cached != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { state ->
                    state.copy(
                        selectedAlbum = cached,
                        isAlbumLoading = !endpointStatus.value.isOfflineDegraded,
                        albumError = null,
                    )
                }
            }
            if (endpointStatus.value.isOfflineDegraded) {
                if (cached == null && fallbackAlbum == null) {
                    snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_cached_detail_unavailable)))
                } else if (cached == null) {
                    mutableUiState.update { state ->
                        state.copy(albumError = UiText.resource(R.string.error_cached_detail_unavailable))
                    }
                }
                mutableUiState.update { state -> state.copy(isAlbumLoading = false) }
                return@launch
            }
            runCatching {
                subsonicRepository.getAlbum(serverId, albumId).data
            }.onSuccess { album ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            selectedAlbum = album,
                            isAlbumLoading = false,
                            albumError = null,
                        )
                    }
                }
                runCatching { libraryCacheRepository.saveAlbumDetail(serverId, album) }
                    .onFailure { Log.w("SakiApp", "Failed to cache album detail", it) }
                refreshCachedArtistIndex(serverId)
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            isAlbumLoading = false,
                            albumError = if (cached == null && fallbackAlbum == null) {
                                throwable.localizedOr(R.string.error_load_album_details)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun openPlaylist(playlistId: String) {
        val serverId = uiState.value.selectedServerId ?: return
        val fallbackPlaylist = uiState.value.findPlaylistSummary(playlistId)?.toPlaylist()
        mutableUiState.update { state ->
            state.copy(
                selectedPlaylist = fallbackPlaylist,
                isPlaylistLoading = true,
                playlistError = null,
                browseStack = state.browseStack.pushed(BrowseNavRoute.PlaylistDetail(playlistId)),
            )
        }

        viewModelScope.launch {
            val cached = runCatching { libraryCacheRepository.getPlaylistDetail(serverId, playlistId) }.getOrNull()
            if (cached != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { state ->
                    state.copy(
                        selectedPlaylist = cached,
                        isPlaylistLoading = !endpointStatus.value.isOfflineDegraded,
                        playlistError = null,
                    )
                }
            }
            if (endpointStatus.value.isOfflineDegraded) {
                if (cached == null && fallbackPlaylist == null) {
                    snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.error_cached_detail_unavailable)))
                } else if (cached == null) {
                    mutableUiState.update { state ->
                        state.copy(playlistError = UiText.resource(R.string.error_cached_detail_unavailable))
                    }
                }
                mutableUiState.update { state -> state.copy(isPlaylistLoading = false) }
                return@launch
            }
            runCatching {
                subsonicRepository.getPlaylist(serverId, playlistId).data
            }.onSuccess { playlist ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            selectedPlaylist = playlist,
                            isPlaylistLoading = false,
                            playlistError = null,
                        )
                    }
                }
                runCatching { libraryCacheRepository.savePlaylistDetail(serverId, playlist) }
                    .onFailure { Log.w("SakiApp", "Failed to cache playlist detail", it) }
                refreshCachedArtistIndex(serverId)
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            isPlaylistLoading = false,
                            playlistError = if (cached == null && fallbackPlaylist == null) {
                                throwable.localizedOr(R.string.error_load_playlist)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun playSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.playSong(serverId, song)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_playback)))
            }
        }
    }

    fun playSongs(
        songs: List<Song>,
        startIndex: Int = 0,
    ) {
        val serverId = uiState.value.selectedServerId ?: return
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playbackManager.playQueue(serverId, songs, startIndex)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_playback)))
            }
        }
    }

    fun playLibrarySongs(startIndex: Int = 0) {
        val state = uiState.value
        val serverId = state.selectedServerId ?: return
        val songs = state.songs
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playbackManager.playLibraryQueue(
                    serverId = serverId,
                    songs = songs,
                    startIndex = startIndex,
                    libraryOffset = state.songsOffset,
                )
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_playback)))
            }
        }
    }

    fun selectSongFeed(feed: SongFeedType) {
        val serverId = uiState.value.selectedServerId ?: return
        mutableUiState.update { it.copy(selectedSongFeed = feed) }
        if (
            feed == SongFeedType.RANDOM &&
            uiState.value.randomSongs.isEmpty() &&
            !uiState.value.isRandomSongsLoading
        ) {
            loadRandomSongs(serverId)
        }
    }

    fun refreshRandomSongs() {
        val serverId = uiState.value.selectedServerId ?: return
        if (uiState.value.isRandomSongsLoading) return
        loadRandomSongs(serverId)
    }

    private fun loadRandomSongs(serverId: Long) {
        mutableUiState.update { it.copy(isRandomSongsLoading = true, randomSongsError = null) }
        viewModelScope.launch {
            val songs = runCatching {
                subsonicRepository.getRandomSongs(serverId, size = RANDOM_SONGS_FEED_SIZE).data
            }.getOrElse { emptyList() }
                .distinctBy(Song::id)
                .ifEmpty {
                    runCatching { libraryCacheRepository.getSongs(serverId) }
                        .getOrDefault(emptyList())
                        .shuffled()
                        .take(RANDOM_SONGS_FEED_SIZE)
                }
            if (uiState.value.selectedServerId != serverId) return@launch
            mutableUiState.update {
                it.copy(
                    randomSongs = songs,
                    isRandomSongsLoading = false,
                    randomSongsError = if (songs.isEmpty()) UiText.resource(R.string.browse_no_songs) else null,
                )
            }
        }
    }

    fun queueSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.addToQueue(serverId, listOf(song))
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_added_to_queue, song.title)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_queue_song)))
            }
        }
    }

    fun playSongNext(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        viewModelScope.launch {
            runCatching {
                playbackManager.playNext(serverId, song)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_play_next, song.title)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_reorder_queue)))
            }
        }
    }

    fun toggleSongDownload(song: Song) {
        val selectedServerId = uiState.value.selectedServerId ?: return
        val cachedSong = uiState.value.cachedSongs.firstOrNull { cached ->
            cached.serverId == selectedServerId && cached.songId == song.id
        }
        if (cachedSong != null) {
            deleteCachedSong(cachedSong.cacheId)
            return
        }
        downloadSong(song)
    }

    fun downloadSong(song: Song) {
        val serverId = uiState.value.selectedServerId ?: return
        mutableUiState.update { state ->
            state.copy(downloadingSongIds = state.downloadingSongIds + song.id)
        }

        viewModelScope.launch {
            runCatching {
                cachedSongRepository.cacheSong(serverId, song)
            }.onSuccess { cachedSong ->
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(
                        R.string.message_saved_offline,
                        cachedSong.title,
                        UiText.resource(cachedSong.quality.labelRes()),
                    )),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_cache_song)))
            }
            mutableUiState.update { state ->
                state.copy(downloadingSongIds = state.downloadingSongIds - song.id)
            }
        }
    }

    suspend fun estimateCollectionStreamCache(
        songs: List<Song>,
    ): CollectionStreamCacheEstimate? {
        val serverId = uiState.value.selectedServerId ?: return null
        return streamCacheRepository.estimateCollectionCache(serverId, songs)
    }

    fun startCollectionStreamCache(
        sourceKey: String,
        title: String,
        songs: List<Song>,
        estimate: CollectionStreamCacheEstimate,
    ) {
        val serverId = uiState.value.selectedServerId ?: return
        streamCacheRepository.startCollectionCache(
            sourceKey = sourceKey,
            title = title,
            serverId = serverId,
            songs = songs,
            estimate = estimate,
        )
    }

    fun cancelCollectionStreamCache() {
        streamCacheRepository.cancelCollectionCache()
    }

    fun playCachedSong(song: CachedSong) {
        viewModelScope.launch {
            runCatching {
                playbackManager.playCachedSong(song)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_play_cached_song)))
            }
        }
    }

    fun playCachedQueue(
        songs: List<CachedSong>,
        startIndex: Int = 0,
    ) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                playbackManager.playCachedQueue(songs, startIndex)
            }.onSuccess {
                openNowPlayingRequestsFlow.emit(Unit)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_start_offline_playback)))
            }
        }
    }

    fun showOfflineSongUnavailable() {
        snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_song_unavailable_offline)))
    }

    fun deleteCachedSong(cacheId: String) {
        viewModelScope.launch {
            runCatching {
                cachedSongRepository.deleteCachedSong(cacheId)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_removed_cached_file)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_remove_cached_file)))
            }
        }
    }

    fun clearCachedSongs() {
        val targetServerId = uiState.value.selectedServerId
        viewModelScope.launch {
            runCatching {
                cachedSongRepository.clearCachedSongs(targetServerId)
            }.onSuccess { removed ->
                snackbarMessages.emit(
                    SnackbarMessage(if (removed > 0) {
                        UiText.plural(R.plurals.message_cleared_download_count, removed, removed)
                    } else {
                        UiText.resource(R.string.message_no_downloads_to_clear)
                    }),
                )
                refreshCacheStorageSummary(targetServerId)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_clear_downloads)))
            }
        }
    }

    fun clearStreamCache() {
        val targetServerId = uiState.value.selectedServerId
        viewModelScope.launch {
            runCatching {
                streamCacheRepository.clearStreamCache(targetServerId)
            }.onSuccess { removed ->
                snackbarMessages.emit(
                    SnackbarMessage(if (removed > 0) {
                        UiText.plural(R.plurals.message_cleared_stream_cache_entry_count, removed, removed)
                    } else {
                        UiText.resource(R.string.message_no_stream_cache_to_clear)
                    }),
                )
                refreshCacheStorageSummary(targetServerId, includeStreamCacheSummary = true)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_clear_stream_cache)))
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = appContext.cacheDir.resolve("image_cache")
                    dir.deleteRecursively()
                    dir.mkdirs()
                }
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_cover_art_cache_cleared)))
                refreshCacheStorageSummary(uiState.value.selectedServerId, includeImageCacheBytes = true)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_clear_cover_art_cache)))
            }
        }
    }

    fun refreshSettingsCacheStorageSummary() {
        viewModelScope.launch {
            refreshCacheStorageSummary(
                serverId = uiState.value.selectedServerId,
                includeStreamCacheSummary = true,
                includeImageCacheBytes = true,
            )
        }
    }

    fun updateStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateStreamQuality(quality)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_stream_quality_set, UiText.resource(quality.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_stream_quality)))
            }
        }
    }

    fun updateDownloadQuality(quality: StreamQuality) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateDownloadQuality(quality)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_download_quality_set, UiText.resource(quality.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_download_quality)))
            }
        }
    }

    fun updateAdaptiveQuality(enabled: Boolean) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateAdaptiveQuality(enabled)
        }
    }

    fun updateWifiStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateWifiStreamQuality(quality)
        }
    }

    fun updateMobileStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateMobileStreamQuality(quality)
        }
    }

    fun updateOriginalPlaybackFailureAction(action: OriginalPlaybackFailureAction) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateOriginalPlaybackFailureAction(action)
        }
    }

    fun updateAlacDecoderMode(mode: AlacDecoderMode) {
        viewModelScope.launch {
            playbackPreferencesRepository.updateAlacDecoderMode(mode)
        }
    }

    fun updateSoundBalancing(mode: SoundBalancingMode) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateSoundBalancing(mode)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(UiText.resource(R.string.message_sound_balancing_set, UiText.resource(mode.labelRes()))),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_sound_balancing)))
            }
        }
    }

    fun updateStreamCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateStreamCacheSizeMb(sizeMb)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(UiText.resource(R.string.message_stream_cache_limit_updated)))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_stream_cache_size)))
            }
        }
    }

    fun updateBluetoothLyrics(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateBluetoothLyrics(enabled)
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_bluetooth_lyrics)))
            }
        }
    }

    fun updateBluetoothLyricsOffsetMs(offsetMs: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateBluetoothLyricsOffsetMs(offsetMs)
            }.onFailure { throwable ->
                snackbarMessages.emit(
                    SnackbarMessage(throwable.localizedOr(R.string.error_update_bluetooth_lyrics_offset)),
                )
            }
        }
    }

    fun updateBufferStrategy(strategy: org.hdhmc.saki.domain.model.BufferStrategy) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateBufferStrategy(strategy)
            }.onSuccess {
                snackbarMessages.emit(
                    SnackbarMessage(
                        text = UiText.resource(R.string.message_buffer_strategy_set, UiText.resource(strategy.labelRes())),
                        action = SnackbarAction.RESTART,
                    ),
                )
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_buffer_strategy)))
            }
        }
    }

    fun updateCustomBufferSeconds(seconds: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateCustomBufferSeconds(seconds)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(
                    text = UiText.resource(R.string.message_custom_buffer_set, seconds),
                    action = SnackbarAction.RESTART,
                ))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_custom_buffer)))
            }
        }
    }

    fun updateImageCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch {
            runCatching {
                playbackPreferencesRepository.updateImageCacheSizeMb(sizeMb)
            }.onSuccess {
                snackbarMessages.emit(SnackbarMessage(
                    text = UiText.resource(R.string.message_cover_art_cache_limit_updated),
                    action = SnackbarAction.RESTART,
                ))
            }.onFailure { throwable ->
                snackbarMessages.emit(SnackbarMessage(throwable.localizedOr(R.string.error_update_cover_art_cache_size)))
            }
        }
    }

    fun pausePlayback() {
        viewModelScope.launch {
            playbackManager.pause()
        }
    }

    fun resumePlayback() {
        viewModelScope.launch {
            playbackManager.resume()
        }
    }

    fun skipToNext() {
        viewModelScope.launch {
            playbackManager.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            playbackManager.skipToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playbackManager.seekTo(positionMs)
        }
    }

    fun cycleRepeatMode() {
        viewModelScope.launch {
            playbackManager.cycleRepeatMode()
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            playbackManager.toggleShuffle()
        }
    }

    fun skipToQueueItem(index: Int) {
        viewModelScope.launch {
            playbackManager.skipToQueueItem(index)
        }
    }

    fun removeQueueItem(index: Int) {
        viewModelScope.launch {
            playbackManager.removeQueueItem(index)
        }
    }

    private fun handleServerConfigsChanged(servers: List<ServerConfig>) {
        val previousServerId = uiState.value.selectedServerId
            ?: uiState.value.appPreferences.lastSelectedServerId
        val selectedServerId = when {
            servers.isEmpty() -> null
            previousServerId != null && servers.any { it.id == previousServerId } -> previousServerId
            else -> servers.first().id
        }
        val serverChanged = previousServerId != selectedServerId

        if (serverChanged) {
            clearSearchState()
        }
        mutableUiState.update { state ->
            state.copy(
                servers = servers,
                selectedServerId = selectedServerId,
                browseStack = if (serverChanged) listOf(BrowseNavRoute.Root) else state.browseStack,
                selectedArtist = if (serverChanged) null else state.selectedArtist,
                selectedArtistSongs = if (serverChanged) emptyList() else state.selectedArtistSongs,
                selectedArtistSongsAreTopSongs = if (serverChanged) true else state.selectedArtistSongsAreTopSongs,
                hasLoadedArtistsFromNetwork = if (serverChanged) false else state.hasLoadedArtistsFromNetwork,
                albumFeeds = if (serverChanged) emptyAlbumFeedStates() else state.albumFeeds,
                selectedAlbum = if (serverChanged) null else state.selectedAlbum,
                selectedPlaylist = if (serverChanged) null else state.selectedPlaylist,
                songs = if (serverChanged) emptyList() else state.songs,
                songsOffset = if (serverChanged) 0 else state.songsOffset,
                hasPreviousSongs = if (serverChanged) false else state.hasPreviousSongs,
                hasMoreSongs = if (serverChanged) true else state.hasMoreSongs,
                hasLoadedSongsFromNetwork = if (serverChanged) false else state.hasLoadedSongsFromNetwork,
                isSongsLoadingPrevious = if (serverChanged) false else state.isSongsLoadingPrevious,
                isSongsLoadingMore = if (serverChanged) false else state.isSongsLoadingMore,
                selectedSongFeed = if (serverChanged) SongFeedType.DEFAULT else state.selectedSongFeed,
                randomSongs = if (serverChanged) emptyList() else state.randomSongs,
                isRandomSongsLoading = if (serverChanged) false else state.isRandomSongsLoading,
                randomSongsError = if (serverChanged) null else state.randomSongsError,
                cacheStorageSummary = if (serverChanged) {
                    state.cacheStorageSummary.copy(
                        streamCachedSongCount = 0,
                        streamCacheBytes = 0,
                        hasStreamingCache = false,
                    )
                } else {
                    state.cacheStorageSummary
                },
                streamCachedSongIds = if (serverChanged) emptySet() else state.streamCachedSongIds,
            )
        }
        if (serverChanged) {
            refreshEndpointStatus()
        }

        viewModelScope.launch {
            refreshCacheStorageSummary(selectedServerId)
        }
        scheduleStreamCacheStorageSummaryRefresh(selectedServerId)

        if (selectedServerId != null && (serverChanged || lastLoadedServerId != selectedServerId)) {
            // Show cached content immediately, then probe + network refresh
            loadCachedContent(selectedServerId)
            viewModelScope.launch {
                val probedEndpoint = servers.find { it.id == selectedServerId }?.let { server ->
                    endpointSelector.probe(selectedServerId, server)
                }
                refreshEndpointStatus()
                val hasReachableEndpoint = (probedEndpoint != null || endpointSelector.getActiveEndpointId(selectedServerId) != null) &&
                    !endpointStatus.value.isOfflineDegraded
                if (uiState.value.playbackState.currentItem == null) {
                    if (hasReachableEndpoint) {
                        restorePlayQueue(selectedServerId)
                    } else {
                        restoreLocalPlayQueue(selectedServerId, offlineOnly = true)
                    }
                }
                if (hasReachableEndpoint) {
                    refreshServerContent(selectedServerId, forceRefresh = serverChanged)
                }
            }
        } else if (selectedServerId != null) {
            // Server didn't change but config may have (e.g. endpoints added/removed) — re-probe
            viewModelScope.launch {
                servers.find { it.id == selectedServerId }?.let { server ->
                    endpointSelector.registerServer(server)
                    endpointSelector.probe(selectedServerId, server)
                }
            }
        }
    }

    private suspend fun refreshCacheStorageSummary(
        serverId: Long?,
        includeStreamCacheSummary: Boolean = false,
        includeImageCacheBytes: Boolean = false,
    ) {
        val downloadSummary = cachedSongRepository.getCacheStorageSummary(serverId)
        val fullStreamSummary = if (includeStreamCacheSummary) {
            streamCacheRepository.getStreamCacheSummary(serverId)
        } else {
            null
        }
        val imageCacheBytes = if (includeImageCacheBytes) {
            withContext(Dispatchers.IO) {
                val imageCacheDir = appContext.cacheDir.resolve("image_cache")
                imageCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        } else {
            null
        }
        mutableUiState.update { state ->
            if (state.selectedServerId == serverId) {
                val currentSummary = state.cacheStorageSummary
                val currentImageCacheBytes = imageCacheBytes ?: state.cacheStorageSummary.imageCacheBytes
                state.copy(
                    cacheStorageSummary = downloadSummary.copy(
                        streamCachedSongCount = fullStreamSummary?.cachedSongIds?.size
                            ?: currentSummary.streamCachedSongCount,
                        streamCacheBytes = fullStreamSummary?.bytes
                            ?: currentSummary.streamCacheBytes,
                        hasStreamingCache = fullStreamSummary != null || currentSummary.hasStreamingCache,
                        imageCacheBytes = currentImageCacheBytes,
                    ),
                    streamCachedSongIds = fullStreamSummary?.cachedSongIds ?: state.streamCachedSongIds,
                )
            } else {
                state
            }
        }
    }

    private fun scheduleStreamCacheStorageSummaryRefresh(
        serverId: Long?,
        delayMs: Long = DEFERRED_STREAM_CACHE_SUMMARY_REFRESH_MS,
    ) {
        deferredStreamCacheSummaryJob?.cancel()
        if (serverId == null) return
        deferredStreamCacheSummaryJob = viewModelScope.launch {
            delay(delayMs)
            if (uiState.value.selectedServerId == serverId) {
                refreshCacheStorageSummary(serverId, includeStreamCacheSummary = true)
            }
        }
    }

    private fun restorePlayQueue(serverId: Long) {
        viewModelScope.launch {
            val offlineOnly = endpointStatus.value.isOfflineDegraded
            val localSnapshot = runCatching { localPlayQueueRepository.get(serverId) }.getOrNull()
            if (!offlineOnly && localSnapshot?.hasLibrarySongsSource() == true) {
                restoreLocalPlayQueueSnapshot(serverId, offlineOnly = false)
                return@launch
            }
            runCatching {
                subsonicRepository.getPlayQueue(serverId).data
            }.onSuccess { savedQueue ->
                if (savedQueue.songs.isEmpty()) return@onSuccess
                if (uiState.value.playbackState.queue.isNotEmpty()) return@onSuccess
                val startIndex = if (savedQueue.currentSongId != null) {
                    savedQueue.songs.indexOfFirst { it.id == savedQueue.currentSongId }.coerceAtLeast(0)
                } else {
                    0
                }
                playbackManager.restoreQueue(
                    serverId = serverId,
                    songs = savedQueue.songs,
                    startIndex = startIndex,
                    positionMs = savedQueue.positionMs,
                )
            }.onFailure { e ->
                if (e is CancellationException) throw e
                restoreLocalPlayQueueSnapshot(
                    serverId = serverId,
                    offlineOnly = offlineOnly,
                )
            }
        }
    }

    private fun restoreLocalPlayQueue(
        serverId: Long,
        offlineOnly: Boolean,
    ) {
        viewModelScope.launch {
            restoreLocalPlayQueueSnapshot(serverId, offlineOnly)
        }
    }

    private suspend fun restoreLocalPlayQueueSnapshot(
        serverId: Long,
        offlineOnly: Boolean,
    ) {
        if (uiState.value.playbackState.currentItem != null || uiState.value.playbackState.queue.isNotEmpty()) {
            return
        }
        val snapshot = localPlayQueueRepository.get(serverId) ?: return
        if (snapshot.songs.isEmpty()) return

        val source = snapshot.source
        if (!offlineOnly && source?.type == LocalPlayQueueSnapshotSourceType.LIBRARY_SONGS) {
            playbackManager.restoreLibraryQueue(
                serverId = serverId,
                songs = snapshot.songs,
                currentLibraryIndex = source.currentIndex,
                libraryOffset = source.windowOffset,
                positionMs = snapshot.positionMs,
            )
            return
        }

        val restored = if (offlineOnly) {
            snapshot.offlinePlayableRestorePlan(serverId) ?: return
        } else {
            val rawStartIndex = snapshot.songs.indexOfFirst { song -> song.id == snapshot.currentSongId }
            LocalPlayQueueRestorePlan(
                songs = snapshot.songs,
                startIndex = rawStartIndex.coerceAtLeast(0),
                positionMs = if (rawStartIndex >= 0) snapshot.positionMs else 0L,
            )
        }
        playbackManager.restoreQueue(
            serverId = serverId,
            songs = restored.songs,
            startIndex = restored.startIndex,
            positionMs = restored.positionMs,
        )
    }

    private fun LocalPlayQueueSnapshot.hasLibrarySongsSource(): Boolean {
        return source?.type == LocalPlayQueueSnapshotSourceType.LIBRARY_SONGS
    }

    private suspend fun LocalPlayQueueSnapshot.offlinePlayableRestorePlan(
        serverId: Long,
    ): LocalPlayQueueRestorePlan? {
        val localCacheQuality = StreamQuality.entries.last()
        val downloadedSongIds = cachedSongRepository.getPlayableCachedSongs(serverId, localCacheQuality).keys
        val streamCachedSongIds = songs.asSequence()
            .map(Song::id)
            .filter { songId -> streamCacheRepository.findCachedQualityKey(serverId, songId, localCacheQuality) != null }
            .toSet()
        val playableSongIds = downloadedSongIds + streamCachedSongIds
        val originalStartIndex = songs.indexOfFirst { song -> song.id == currentSongId }
            .coerceAtLeast(0)
        val playableSongs = songs.withIndex()
            .filter { (_, song) -> song.id in playableSongIds }
        if (playableSongs.isEmpty()) return null

        val startItem = playableSongs.firstOrNull { (index, _) -> index >= originalStartIndex }
            ?: playableSongs.first()
        val startIndex = playableSongs.indexOfFirst { (index, _) -> index == startItem.index }
        return LocalPlayQueueRestorePlan(
            songs = playableSongs.map { (_, song) -> song },
            startIndex = startIndex.coerceAtLeast(0),
            positionMs = if (startItem.value.id == currentSongId) positionMs else 0L,
        )
    }

    private fun loadCachedContent(serverId: Long) {
        lastLoadedServerId = serverId
        viewModelScope.launch {
            suspend fun <T> loadCachedOrNull(block: suspend () -> T): T? = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }

            val artists = loadCachedOrNull { libraryCacheRepository.getArtists(serverId, hideMergedArtists = uiState.value.appPreferences.hideMergedArtists) }
            if (artists != null && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(libraryIndexes = artists.regroupByLocale(currentIndexingLocale())) }
            }
            AlbumListType.entries.forEach { albumFeed ->
                val albums = loadCachedOrNull { libraryCacheRepository.getAlbums(serverId, albumFeed) }
                if (!albums.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    val current = uiState.value
                    val displayAlbums = albums
                        .withoutUnknownAlbumPlaceholders()
                        .sortedForAlbumFeedOnDefault(
                            type = albumFeed,
                            locale = current.appPreferences.language.indexingLocale(),
                            ignoredArticles = current.libraryIndexes?.ignoredArticles,
                        )
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(albumFeed) {
                                it.copy(
                                    albums = displayAlbums,
                                    offset = displayAlbums.size,
                                    hasMore = albumFeed.supportsPagination(),
                                )
                            },
                        )
                    }
                }
            }
            val playlists = loadCachedOrNull { libraryCacheRepository.getPlaylists(serverId) }
            if (!playlists.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(playlists = playlists) }
            }
            val songsPageSize = uiState.value.appPreferences.songsPageSize
            val songs = loadCachedOrNull { libraryCacheRepository.getSongsPage(serverId, songsPageSize, 0) }
            if (!songs.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                mutableUiState.update {
                    it.copy(
                        songs = songs,
                        songsOffset = 0,
                        hasPreviousSongs = false,
                        hasMoreSongs = songs.size >= songsPageSize,
                    )
                }
            }
        }
    }

    private fun refreshServerContent(serverId: Long, forceRefresh: Boolean = false) {
        loadServerContent(serverId, forceRefresh)
    }

    private fun loadServerContent(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        lastLoadedServerId = serverId
        loadArtists(serverId, forceRefresh)
        loadAlbums(serverId, uiState.value.selectedAlbumFeed, forceRefresh)
        loadPlaylists(serverId, forceRefresh)
        if (uiState.value.selectedBrowseSection == BrowseSection.SONGS) {
            loadSongs(serverId, forceRefresh)
        }
    }

    private fun loadArtists(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        if (
            !forceRefresh &&
            uiState.value.libraryIndexes != null &&
            (uiState.value.hasLoadedArtistsFromNetwork || endpointStatus.value.isOfflineDegraded)
        ) {
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isArtistsLoading = true, artistsError = null) }

            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getArtists(serverId, hideMergedArtists = uiState.value.appPreferences.hideMergedArtists) }.getOrNull()
                if (cached != null && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(libraryIndexes = cached.regroupByLocale(currentIndexingLocale())) }
                }
            }

            runCatching {
                subsonicRepository.getIndexes(serverId).data
            }.onSuccess { indexes ->
                runCatching { libraryCacheRepository.saveArtists(serverId, indexes) }
                    .onFailure { Log.w("SakiApp", "Failed to cache artists", it) }
                val mergedIndexes = runCatching {
                    libraryCacheRepository.getArtists(serverId, hideMergedArtists = uiState.value.appPreferences.hideMergedArtists)
                }.getOrNull() ?: indexes
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(
                            libraryIndexes = mergedIndexes.regroupByLocale(currentIndexingLocale()),
                            hasLoadedArtistsFromNetwork = true,
                            isArtistsLoading = false,
                            artistsError = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(isArtistsLoading = false, artistsError = throwable.localizedOr(R.string.error_load_artists))
                }
            }
        }
    }

    private fun loadAlbums(
        serverId: Long,
        type: AlbumListType,
        forceRefresh: Boolean = false,
    ) {
        if (forceRefresh && type.supportsAlbumFastScroll()) {
            sortedAlbumPrefetchJobs.remove(type)?.cancel()
        }
        val currentFeed = uiState.value.albumFeedState(type)
        val pageSize = type.albumFeedPageSize()
        if (!forceRefresh && (currentFeed.isLoading || currentFeed.hasLoadedFromNetwork)) {
            return
        }

        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getAlbums(serverId, type) }.getOrNull()
                val visibleCached = cached?.withoutUnknownAlbumPlaceholders().orEmpty()
                if (visibleCached.isNotEmpty() && uiState.value.selectedServerId == serverId) {
                    val current = uiState.value
                    val displayCached = visibleCached.sortedForAlbumFeedOnDefault(
                        type = type,
                        locale = current.appPreferences.language.indexingLocale(),
                        ignoredArticles = current.libraryIndexes?.ignoredArticles,
                    )
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(type) {
                                it.copy(
                                    albums = displayCached,
                                    offset = displayCached.size,
                                    hasMore = type.supportsPagination(),
                                    error = null,
                                )
                            },
                        )
                    }
                }
            }

            mutableUiState.update { state ->
                state.copy(
                    albumFeeds = state.albumFeeds.updateFeed(type) {
                        it.copy(
                            isLoading = true,
                            isLoadingMore = false,
                            hasMore = type.supportsPagination(),
                            error = null,
                        )
                    },
                )
            }

            runCatching {
                subsonicRepository.getAlbumList(
                    serverId = serverId,
                    type = type,
                    size = pageSize,
                    offset = 0,
                ).data
            }.onSuccess { albums ->
                val uniqueAlbums = albums
                    .withoutUnknownAlbumPlaceholders()
                    .distinctBy(AlbumSummary::id)
                val current = uiState.value
                val displayAlbums = uniqueAlbums.sortedForAlbumFeedOnDefault(
                    type = type,
                    locale = current.appPreferences.language.indexingLocale(),
                    ignoredArticles = current.libraryIndexes?.ignoredArticles,
                )
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(type) {
                                it.copy(
                                    albums = displayAlbums,
                                    offset = albums.size,
                                    hasMore = type.supportsPagination() && albums.size >= pageSize,
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = null,
                                    hasLoadedFromNetwork = true,
                                )
                            },
                        )
                    }
                }
                runCatching { libraryCacheRepository.saveAlbums(serverId, type, displayAlbums) }
                    .onFailure { Log.w("SakiApp", "Failed to cache albums", it) }
                if (type.supportsAlbumFastScroll() && albums.size >= pageSize) {
                    startSortedAlbumPrefetch(serverId, type, pageSize)
                }
            }.onFailure { throwable ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { state ->
                        state.copy(
                            albumFeeds = state.albumFeeds.updateFeed(type) {
                                it.copy(
                                    isLoading = false,
                                    isLoadingMore = false,
                                    error = throwable.localizedOr(R.string.error_load_albums),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun startSortedAlbumPrefetch(
        serverId: Long,
        type: AlbumListType,
        pageSize: Int,
    ) {
        if (!type.supportsAlbumFastScroll()) return
        if (sortedAlbumPrefetchJobs[type]?.isActive == true) return

        val job = viewModelScope.launch {
            loadRemainingSortedAlbumPages(serverId, type, pageSize)
        }
        sortedAlbumPrefetchJobs[type] = job
        job.invokeOnCompletion {
            viewModelScope.launch {
                if (sortedAlbumPrefetchJobs[type] === job) {
                    sortedAlbumPrefetchJobs.remove(type)
                }
            }
        }
    }

    private suspend fun loadRemainingSortedAlbumPages(
        serverId: Long,
        type: AlbumListType,
        pageSize: Int,
    ) {
        if (!type.supportsAlbumFastScroll()) return

        val initialState = uiState.value
        val initialFeed = initialState.albumFeedState(type)
        if (!initialFeed.hasMore || initialFeed.isLoadingMore) {
            cacheSortedAlbumPrefetch(serverId, type, initialFeed.albums)
            return
        }

        var rawOffset = initialFeed.offset
        var mergedAlbums = initialFeed.albums
        var shouldContinue = true

        mutableUiState.update { current ->
            current.copy(
                albumFeeds = current.albumFeeds.updateFeed(type) {
                    it.copy(isLoadingMore = true, error = null)
                },
            )
        }

        while (uiState.value.selectedServerId == serverId && shouldContinue) {
            val page = try {
                subsonicRepository.getAlbumList(
                    serverId = serverId,
                    type = type,
                    size = pageSize,
                    offset = rawOffset,
                ).data
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { current ->
                        current.copy(
                            albumFeeds = current.albumFeeds.updateFeed(type) {
                                it.copy(isLoadingMore = false)
                            },
                        )
                    }
                    Log.w("SakiApp", "Failed to prefetch sorted album page", throwable)
                }
                val current = uiState.value
                val cachedPartialAlbums = mergedAlbums.sortedForAlbumFeedOnDefault(
                    type = type,
                    locale = current.appPreferences.language.indexingLocale(),
                    ignoredArticles = current.libraryIndexes?.ignoredArticles,
                )
                cacheSortedAlbumPrefetch(serverId, type, cachedPartialAlbums)
                return
            }

            if (uiState.value.selectedServerId != serverId) return

            val visiblePage = page.withoutUnknownAlbumPlaceholders()
            mergedAlbums = withContext(Dispatchers.Default) {
                (mergedAlbums + visiblePage).distinctBy(AlbumSummary::id)
            }
            rawOffset += page.size
            shouldContinue = page.size >= pageSize
        }

        if (uiState.value.selectedServerId != serverId) return

        val current = uiState.value
        val displayAlbums = mergedAlbums.sortedForAlbumFeedOnDefault(
            type = type,
            locale = current.appPreferences.language.indexingLocale(),
            ignoredArticles = current.libraryIndexes?.ignoredArticles,
        )
        mutableUiState.update { latest ->
            latest.copy(
                albumFeeds = latest.albumFeeds.updateFeed(type) {
                    it.copy(
                        albums = displayAlbums,
                        offset = rawOffset,
                        hasMore = shouldContinue,
                        isLoadingMore = false,
                        error = null,
                    )
                },
            )
        }
        cacheSortedAlbumPrefetch(serverId, type, displayAlbums)
    }

    private suspend fun cacheSortedAlbumPrefetch(
        serverId: Long,
        type: AlbumListType,
        albums: List<AlbumSummary>?,
    ) {
        if (albums == null) return
        try {
            libraryCacheRepository.saveAlbums(serverId, type, albums)
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            Log.w("SakiApp", "Failed to cache prefetched albums", throwable)
        }
    }

    private fun loadPlaylists(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && uiState.value.playlists.isNotEmpty()) return

        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = runCatching { libraryCacheRepository.getPlaylists(serverId) }.getOrNull()
                if (!cached.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(playlists = cached) }
                }
            }

            mutableUiState.update { it.copy(isPlaylistsLoading = true, playlistsError = null) }

            runCatching {
                subsonicRepository.getPlaylists(serverId).data
            }.onSuccess { playlists ->
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update { it.copy(playlists = playlists, isPlaylistsLoading = false, playlistsError = null) }
                }
                runCatching { libraryCacheRepository.savePlaylists(serverId, playlists) }
                    .onFailure { Log.w("SakiApp", "Failed to cache playlists", it) }
                prefetchPlaylistDetails(serverId, playlists)
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(isPlaylistsLoading = false, playlistsError = throwable.localizedOr(R.string.error_load_playlists))
                }
            }
        }
    }

    private suspend fun prefetchPlaylistDetails(
        serverId: Long,
        playlists: List<PlaylistSummary>,
    ) {
        val candidates = playlists.asSequence()
            .filter { playlist -> (playlist.songCount ?: Int.MAX_VALUE) <= PLAYLIST_DETAIL_PREFETCH_MAX_SONGS }
            .take(PLAYLIST_DETAIL_PREFETCH_LIMIT)
            .toList()
        if (candidates.isEmpty()) return

        val cachedIds = try {
            libraryCacheRepository.getCachedPlaylistDetailIds(serverId, candidates.map(PlaylistSummary::id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("SakiApp", "Failed to read cached playlist detail ids", e)
            emptySet()
        }

        candidates.filterNot { playlist -> playlist.id in cachedIds }.forEach { playlistSummary ->
            if (uiState.value.selectedServerId != serverId || endpointStatus.value.isOfflineDegraded) return
            try {
                val playlist = subsonicRepository.getPlaylist(serverId, playlistSummary.id).data
                try {
                    libraryCacheRepository.savePlaylistDetail(serverId, playlist)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w("SakiApp", "Failed to prefetch playlist detail", e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("SakiApp", "Failed to prefetch playlist detail", e)
            }
        }
    }

    private fun loadSongs(
        serverId: Long,
        forceRefresh: Boolean = false,
    ) {
        val currentState = uiState.value
        if (
            !forceRefresh &&
            currentState.songs.isNotEmpty() &&
            (currentState.hasLoadedSongsFromNetwork || endpointStatus.value.isOfflineDegraded)
        ) {
            return
        }

        viewModelScope.launch {
            val songsPageSize = uiState.value.appPreferences.songsPageSize
            if (!forceRefresh || endpointStatus.value.isOfflineDegraded) {
                val cached = runCatching { libraryCacheRepository.getSongsPage(serverId, songsPageSize, 0) }.getOrNull()
                if (!cached.isNullOrEmpty() && uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(
                            songs = cached,
                            songsOffset = 0,
                            hasPreviousSongs = false,
                            hasMoreSongs = cached.size >= songsPageSize,
                        )
                    }
                }
            }

            if (endpointStatus.value.isOfflineDegraded) {
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(
                            isSongsLoading = false,
                            isSongsLoadingPrevious = false,
                            isSongsLoadingMore = false,
                            songsError = null,
                        )
                    }
                }
                return@launch
            }

            if (uiState.value.selectedServerId == serverId) {
                mutableUiState.update {
                    it.copy(
                        isSongsLoading = true,
                        isSongsLoadingPrevious = false,
                        isSongsLoadingMore = false,
                        songsError = null,
                    )
                }
            }

            try {
                val cachedAt = System.currentTimeMillis()
                val result = fetchSongsPage(serverId, offset = 0, cachedAt = cachedAt, limit = songsPageSize)
                if (uiState.value.selectedServerId == serverId) {
                    mutableUiState.update {
                        it.copy(
                            songs = result.songs,
                            songsOffset = 0,
                            hasPreviousSongs = false,
                            hasMoreSongs = result.hasMore,
                            hasLoadedSongsFromNetwork = true,
                            isSongsLoading = false,
                            isSongsLoadingPrevious = false,
                            isSongsLoadingMore = false,
                            songsError = null,
                        )
                    }
                }
                libraryCacheRepository.saveSongsWindow(
                    serverId = serverId,
                    songs = result.songs.take(SONGS_DISPLAY_WINDOW_SIZE),
                    cachedAt = cachedAt,
                    startOrder = 0,
                )
                if (!result.hasMore) {
                    libraryCacheRepository.pruneSongMetadataBefore(serverId, cachedAt)
                }
                refreshCachedArtistIndex(serverId)
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                if (uiState.value.selectedServerId == serverId) {
                    val cached = runCatching { libraryCacheRepository.getSongsPage(serverId, songsPageSize, 0) }.getOrNull()
                    mutableUiState.update {
                        if (!cached.isNullOrEmpty()) {
                            it.copy(
                                songs = cached,
                                songsOffset = 0,
                                hasPreviousSongs = false,
                                hasMoreSongs = cached.size >= songsPageSize,
                                hasLoadedSongsFromNetwork = false,
                                isSongsLoading = false,
                                isSongsLoadingPrevious = false,
                                isSongsLoadingMore = false,
                                songsError = null,
                            )
                        } else {
                            it.copy(
                                isSongsLoading = false,
                                isSongsLoadingPrevious = false,
                                isSongsLoadingMore = false,
                                songsError = throwable.localizedOr(R.string.error_load_songs),
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadCachedSongsPage(
        serverId: Long,
        offset: Int,
        limit: Int = DEFAULT_SONGS_PAGE_SIZE,
    ): SongsPageResult {
        val songs = libraryCacheRepository.getSongsPage(serverId, limit, offset)
        return SongsPageResult(
            songs = songs,
            hasMore = songs.size >= limit,
        )
    }

    private suspend fun refreshCachedArtistIndex(serverId: Long) {
        val artists = runCatching { libraryCacheRepository.getArtists(serverId, hideMergedArtists = uiState.value.appPreferences.hideMergedArtists) }.getOrNull() ?: return
        if (uiState.value.selectedServerId == serverId) {
            mutableUiState.update {
                it.copy(libraryIndexes = artists.regroupByLocale(currentIndexingLocale()))
            }
        }
    }

    // Navidrome supports empty query in search3 to return all songs.
    // This is not standard Subsonic behavior and may not work on other servers.
    private suspend fun fetchSongsPage(
        serverId: Long,
        offset: Int,
        cachedAt: Long,
        limit: Int = DEFAULT_SONGS_PAGE_SIZE,
    ): SongsPageResult = withContext(Dispatchers.IO) {
        val songs = subsonicRepository.search(
            serverId = serverId,
            query = "",
            artistCount = 0,
            albumCount = 0,
            songCount = limit,
            songOffset = offset,
        ).data.songs
        libraryCacheRepository.saveSongMetadataPage(serverId, songs, cachedAt, startOrder = offset)
        SongsPageResult(
            songs = songs,
            hasMore = songs.size >= limit,
        )
    }

    private suspend fun syncAllSongMetadata(
        serverId: Long,
    ): Int = withContext(Dispatchers.IO) {
        val cachedAt = System.currentTimeMillis()
        val displaySongs = mutableListOf<Song>()
        var syncedCount = 0
        var offset = 0
        while (true) {
            val songs = subsonicRepository.search(
                serverId = serverId,
                query = "",
                artistCount = 0,
                albumCount = 0,
                songCount = SONG_METADATA_SYNC_PAGE_SIZE,
                songOffset = offset,
            ).data.songs
            if (songs.isEmpty()) break
            libraryCacheRepository.saveSongMetadataPage(serverId, songs, cachedAt, startOrder = offset)
            syncedCount += songs.size
            if (displaySongs.size < SONGS_DISPLAY_WINDOW_SIZE) {
                displaySongs.addAll(songs.take(SONGS_DISPLAY_WINDOW_SIZE - displaySongs.size))
            }
            if (uiState.value.selectedServerId == serverId) {
                mutableUiState.update { it.copy(songMetadataSyncCount = syncedCount) }
            }
            if (songs.size < SONG_METADATA_SYNC_PAGE_SIZE) break
            offset += songs.size
        }
        if (syncedCount > 0) {
            libraryCacheRepository.saveSongsWindow(serverId, displaySongs, cachedAt, startOrder = 0)
            libraryCacheRepository.pruneSongMetadataBefore(serverId, cachedAt)
        }
        syncedCount
    }

    private suspend fun buildArtistTopSongs(
        serverId: Long,
        artist: Artist,
    ): List<Song> = coroutineScope {
        artist.albums
            .chunked(6)
            .flatMap { batch ->
                batch.map { album -> async { subsonicRepository.getAlbum(serverId, album.id).data } }
                    .map { it.await() }
            }
            .flatMap { album ->
                album.songs
                    .filter { song -> song.belongsToArtistInAlbum(artist, album) }
                    .map { song -> song.withFallbackAlbumMetadata(album) }
            }
            .distinctBy(Song::id)
    }

    private suspend fun performSearch(query: String) {
        val serverId = uiState.value.selectedServerId
        if (!uiState.value.isSearchActive || serverId == null || query.isBlank()) {
            mutableUiState.update { state ->
                state.copy(
                    searchResults = SearchResults(),
                    isSearchLoading = false,
                    searchError = null,
                )
            }
            return
        }

        mutableUiState.update { state ->
            state.copy(
                isSearchLoading = true,
                searchError = null,
            )
        }

        try {
            val results = if (endpointStatus.value.isOfflineDegraded) {
                libraryCacheRepository.searchCached(
                    serverId = serverId,
                    query = query,
                    artistCount = 8,
                    albumCount = 10,
                    songCount = 20,
                )
            } else {
                subsonicRepository.search(
                    serverId = serverId,
                    query = query,
                    artistCount = 8,
                    albumCount = 10,
                    songCount = 20,
                ).data
            }
            if (
                uiState.value.selectedServerId == serverId &&
                uiState.value.isSearchActive &&
                uiState.value.searchQuery.trim() == query
            ) {
                mutableUiState.update { state ->
                    state.copy(
                        searchResults = results,
                        isSearchLoading = false,
                        searchError = null,
                    )
                }
                saveRecentSearchQuery(query)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            if (
                uiState.value.selectedServerId == serverId &&
                uiState.value.isSearchActive &&
                uiState.value.searchQuery.trim() == query
            ) {
                val cachedResults = runCatching {
                    libraryCacheRepository.searchCached(
                        serverId = serverId,
                        query = query,
                        artistCount = 8,
                        albumCount = 10,
                        songCount = 20,
                    )
                }.getOrNull()
                if (cachedResults != null && !cachedResults.isEmpty()) {
                    mutableUiState.update { state ->
                        state.copy(
                            searchResults = cachedResults,
                            isSearchLoading = false,
                            searchError = null,
                        )
                    }
                    saveRecentSearchQuery(query)
                } else {
                    mutableUiState.update { state ->
                        state.copy(
                            searchResults = SearchResults(),
                            isSearchLoading = false,
                            searchError = throwable.localizedOr(R.string.error_search_server),
                        )
                    }
                }
            }
        }
    }

    private suspend fun saveRecentSearchQuery(query: String) {
        runCatching { appPreferencesRepository.addRecentSearchQuery(query) }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w("SakiApp", "Failed to save recent search query", throwable)
            }
    }

    private fun clearSearchState() {
        searchQueryFlow.value = ""
        mutableUiState.update { state ->
            state.copy(
                isSearchActive = false,
                searchQuery = "",
                searchResults = SearchResults(),
                isSearchLoading = false,
                searchError = null,
            )
        }
    }

    fun refreshEndpointStatus() {
        val serverId = uiState.value.selectedServerId
        if (serverId == null) {
            mutableEndpointStatus.value = EndpointStatus()
            return
        }
        val results = endpointSelector.getLastProbeResults(serverId)
        val activeEndpoint = endpointSelector.getActiveEndpoint(serverId)
        mutableEndpointStatus.update { current ->
            current.copy(
                activeEndpointLabel = activeEndpoint?.label,
                activeEndpointId = activeEndpoint?.id,
                isForced = endpointSelector.isForced(serverId),
                isProbeComplete = endpointSelector.hasCompletedProbe(serverId),
                isProbing = endpointSelector.isProbeInProgress(serverId),
                probeResults = results.map { r ->
                    EndpointProbeInfo(
                        id = r.endpoint.id,
                        label = r.endpoint.label,
                        baseUrl = r.endpoint.baseUrl,
                        latencyMs = r.latencyMs,
                        reachable = r.reachable,
                    )
                },
            )
        }
    }

    fun forceEndpoint(endpointId: Long) {
        val serverId = uiState.value.selectedServerId ?: return
        if (endpointSelector.getActiveEndpointId(serverId) == endpointId && endpointSelector.isForced(serverId)) {
            endpointSelector.clearForce(serverId)
        } else {
            endpointSelector.forceEndpoint(serverId, endpointId)
        }
    }

    fun reprobeEndpoints() {
        val serverId = uiState.value.selectedServerId ?: return
        val server = uiState.value.servers.find { it.id == serverId } ?: return
        mutableEndpointStatus.update { it.copy(isProbing = true) }
        viewModelScope.launch {
            try {
                endpointSelector.probe(serverId, server)
                refreshEndpointStatus()
            } finally {
                refreshEndpointStatus()
            }
        }
    }

    fun exportConfig(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val json = configBackupManager.exportToJson()
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                    ?: error("Cannot open output stream")
            }
                .onSuccess { snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_backup_exported))) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    snackbarMessages.tryEmit(
                        SnackbarMessage(UiText.resource(R.string.message_export_failed, e.message.orEmpty())),
                    )
                }
        }
    }

    fun importConfig(uri: android.net.Uri, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val json = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                } ?: run {
                    snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_cannot_read_backup)))
                    return@launch
                }
                when (val result = configBackupManager.importFromJson(json)) {
                    is ImportResult.Success -> {
                        val settingsSuffix = if (result.settingsRestored) {
                            UiText.resource(R.string.message_import_settings_suffix)
                        } else {
                            ""
                        }
                        snackbarMessages.tryEmit(
                            SnackbarMessage(UiText.plural(
                                R.plurals.message_import_success,
                                result.serversImported,
                                result.serversImported,
                                settingsSuffix,
                            )),
                        )
                        onSuccess?.invoke()
                    }
                    is ImportResult.InvalidFormat -> snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_invalid_backup)))
                    is ImportResult.UnsupportedVersion -> snackbarMessages.tryEmit(
                        SnackbarMessage(UiText.resource(R.string.message_unsupported_backup_version, result.version)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarMessages.tryEmit(SnackbarMessage(UiText.resource(R.string.message_import_failed, e.message.orEmpty())))
            }
        }
    }
}

data class EndpointStatus(
    val activeEndpointLabel: String? = null,
    val activeEndpointId: Long? = null,
    val isForced: Boolean = false,
    val probeResults: List<EndpointProbeInfo> = emptyList(),
    val isProbing: Boolean = false,
    val isProbeComplete: Boolean = false,
) {
    val isOfflineDegraded: Boolean
        get() = activeEndpointId == null &&
            isProbeComplete &&
            probeResults.isNotEmpty() &&
            probeResults.none { it.reachable }
}

data class EndpointProbeInfo(
    val id: Long,
    val label: String,
    val baseUrl: String,
    val latencyMs: Long?,
    val reachable: Boolean,
)

private data class LocalPlayQueueRestorePlan(
    val songs: List<Song>,
    val startIndex: Int,
    val positionMs: Long,
)

private data class SongsPageResult(
    val songs: List<Song>,
    val hasMore: Boolean,
)

private fun List<Song>.excludingKnownSongIds(knownSongIds: Set<String>): List<Song> {
    val seenSongIds = knownSongIds.toHashSet()
    return filter { song -> seenSongIds.add(song.id) }
}

private fun SearchResults.isEmpty(): Boolean = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()

enum class BrowseSection {
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    SONGS,
}

private fun DefaultBrowseTab.toBrowseSection(): BrowseSection = when (this) {
    DefaultBrowseTab.ARTISTS -> BrowseSection.ARTISTS
    DefaultBrowseTab.ALBUMS -> BrowseSection.ALBUMS
    DefaultBrowseTab.PLAYLISTS -> BrowseSection.PLAYLISTS
    DefaultBrowseTab.SONGS -> BrowseSection.SONGS
}

data class AlbumFeedState(
    val albums: List<AlbumSummary> = emptyList(),
    val isLoading: Boolean = false,
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
    val hasLoadedFromNetwork: Boolean = false,
)

data class SakiAppUiState(
    val isAppReady: Boolean = false,
    val textScale: TextScale = TextScale.DEFAULT,
    val appPreferences: AppPreferences = AppPreferences(),
    val selectedBrowseSection: BrowseSection = BrowseSection.ARTISTS,
    val browseStack: List<BrowseNavRoute> = listOf(BrowseNavRoute.Root),
    val servers: List<ServerConfig> = emptyList(),
    val selectedServerId: Long? = null,
    val selectedAlbumFeed: AlbumListType = AlbumListType.NEWEST,
    val libraryIndexes: LibraryIndexes? = null,
    val hasLoadedArtistsFromNetwork: Boolean = false,
    val isArtistsLoading: Boolean = false,
    val artistsError: UiText? = null,
    val selectedArtist: Artist? = null,
    val selectedArtistSongs: List<Song> = emptyList(),
    val selectedArtistSongsAreTopSongs: Boolean = true,
    val isArtistLoading: Boolean = false,
    val artistError: UiText? = null,
    val albumFeeds: Map<AlbumListType, AlbumFeedState> = emptyAlbumFeedStates(),
    val selectedAlbum: Album? = null,
    val isAlbumLoading: Boolean = false,
    val albumError: UiText? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val playlistsError: UiText? = null,
    val selectedPlaylist: Playlist? = null,
    val isPlaylistLoading: Boolean = false,
    val playlistError: UiText? = null,
    val songs: List<Song> = emptyList(),
    val songsOffset: Int = 0,
    val hasPreviousSongs: Boolean = false,
    val hasMoreSongs: Boolean = true,
    val hasLoadedSongsFromNetwork: Boolean = false,
    val isSongsLoading: Boolean = false,
    val isSongsLoadingPrevious: Boolean = false,
    val isSongsLoadingMore: Boolean = false,
    val songsError: UiText? = null,
    val selectedSongFeed: SongFeedType = SongFeedType.DEFAULT,
    val randomSongs: List<Song> = emptyList(),
    val isRandomSongsLoading: Boolean = false,
    val randomSongsError: UiText? = null,
    val isSongMetadataSyncing: Boolean = false,
    val songMetadataSyncCount: Int = 0,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchLoading: Boolean = false,
    val searchError: UiText? = null,
    val cachedSongs: List<CachedSong> = emptyList(),
    val cacheStorageSummary: CacheStorageSummary = CacheStorageSummary(),
    val streamCachedSongIds: Set<String> = emptySet(),
    val downloadingSongIds: Set<String> = emptySet(),
    val collectionStreamCacheTask: CollectionStreamCacheTask? = null,
    val playbackState: PlaybackSessionState = PlaybackSessionState(),
    val currentLyrics: SongLyrics? = null,
) {
    fun albumFeedState(type: AlbumListType): AlbumFeedState {
        return albumFeeds[type] ?: AlbumFeedState(hasMore = type.supportsPagination())
    }

    val selectedAlbumFeedState: AlbumFeedState
        get() = albumFeedState(selectedAlbumFeed)
    val albums: List<AlbumSummary>
        get() = selectedAlbumFeedState.albums
    val isAlbumsLoading: Boolean
        get() = selectedAlbumFeedState.isLoading
    val hasMoreAlbums: Boolean
        get() = selectedAlbumFeedState.hasMore
    val isLoadingMoreAlbums: Boolean
        get() = selectedAlbumFeedState.isLoadingMore
    val albumsError: UiText?
        get() = selectedAlbumFeedState.error
}

data class SakiRootUiState(
    val isAppReady: Boolean = false,
    val textScale: TextScale = TextScale.DEFAULT,
    val appPreferences: AppPreferences = AppPreferences(),
)

data class SakiCapsuleUiState(
    val track: PlaybackQueueItem? = null,
    val isPlaying: Boolean = false,
    val currentServer: ServerConfig? = null,
)

data class SakiNowPlayingUiState(
    val playbackState: PlaybackSessionState = PlaybackSessionState(),
    val servers: List<ServerConfig> = emptyList(),
    val selectedServerId: Long? = null,
    val libraryIndexes: LibraryIndexes? = null,
    val currentLyrics: SongLyrics? = null,
)

data class SakiBrowsePlaybackUiState(
    val currentPlaybackSongId: String? = null,
    val isPlaying: Boolean = false,
)

data class SakiBrowseAvailabilityUiState(
    val selectedServerId: Long? = null,
    val cachedSongs: List<CachedSong> = emptyList(),
    val streamCachedSongIds: Set<String> = emptySet(),
    val downloadingSongIds: Set<String> = emptySet(),
    val collectionStreamCacheTask: CollectionStreamCacheTask? = null,
)

data class SakiBrowseUiState(
    val appPreferences: AppPreferences = AppPreferences(),
    val selectedBrowseSection: BrowseSection = BrowseSection.ARTISTS,
    val browseStack: List<BrowseNavRoute> = listOf(BrowseNavRoute.Root),
    val servers: List<ServerConfig> = emptyList(),
    val selectedServerId: Long? = null,
    val selectedAlbumFeed: AlbumListType = AlbumListType.NEWEST,
    val libraryIndexes: LibraryIndexes? = null,
    val isArtistsLoading: Boolean = false,
    val artistsError: UiText? = null,
    val selectedArtist: Artist? = null,
    val selectedArtistSongs: List<Song> = emptyList(),
    val selectedArtistSongsAreTopSongs: Boolean = true,
    val isArtistLoading: Boolean = false,
    val artistError: UiText? = null,
    val albumFeeds: Map<AlbumListType, AlbumFeedState> = emptyAlbumFeedStates(),
    val selectedAlbum: Album? = null,
    val isAlbumLoading: Boolean = false,
    val albumError: UiText? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val playlistsError: UiText? = null,
    val selectedPlaylist: Playlist? = null,
    val isPlaylistLoading: Boolean = false,
    val playlistError: UiText? = null,
    val songs: List<Song> = emptyList(),
    val songsOffset: Int = 0,
    val hasPreviousSongs: Boolean = false,
    val hasMoreSongs: Boolean = true,
    val isSongsLoading: Boolean = false,
    val isSongsLoadingPrevious: Boolean = false,
    val isSongsLoadingMore: Boolean = false,
    val songsError: UiText? = null,
    val selectedSongFeed: SongFeedType = SongFeedType.DEFAULT,
    val randomSongs: List<Song> = emptyList(),
    val isRandomSongsLoading: Boolean = false,
    val randomSongsError: UiText? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: SearchResults = SearchResults(),
    val isSearchLoading: Boolean = false,
    val searchError: UiText? = null,
    val recentSearchQueries: List<String> = emptyList(),
) {
    fun albumFeedState(type: AlbumListType): AlbumFeedState {
        return albumFeeds[type] ?: AlbumFeedState(hasMore = type.supportsPagination())
    }

    val selectedAlbumFeedState: AlbumFeedState
        get() = albumFeedState(selectedAlbumFeed)
    val albums: List<AlbumSummary>
        get() = selectedAlbumFeedState.albums
    val isAlbumsLoading: Boolean
        get() = selectedAlbumFeedState.isLoading
    val hasMoreAlbums: Boolean
        get() = selectedAlbumFeedState.hasMore
    val isLoadingMoreAlbums: Boolean
        get() = selectedAlbumFeedState.isLoadingMore
    val albumsError: UiText?
        get() = selectedAlbumFeedState.error
}

data class SakiSettingsUiState(
    val appPreferences: AppPreferences = AppPreferences(),
    val textScale: TextScale = TextScale.DEFAULT,
    val servers: List<ServerConfig> = emptyList(),
    val selectedServerId: Long? = null,
    val cachedSongs: List<CachedSong> = emptyList(),
    val cacheStorageSummary: CacheStorageSummary = CacheStorageSummary(),
    val playbackPreferences: PlaybackPreferences = PlaybackPreferences(),
    val isSystemAlacDecoderSupported: Boolean? = null,
    val isSongMetadataSyncing: Boolean = false,
    val songMetadataSyncCount: Int = 0,
)

private fun SakiAppUiState.toRootUiState(): SakiRootUiState = SakiRootUiState(
    isAppReady = isAppReady,
    textScale = textScale,
    appPreferences = appPreferences,
)

private fun SakiAppUiState.toCapsuleUiState(): SakiCapsuleUiState {
    val track = playbackState.currentItem ?: playbackState.queue.firstOrNull()
    return SakiCapsuleUiState(
        track = track,
        isPlaying = playbackState.isPlaying,
        currentServer = track?.serverId?.let { sid -> servers.firstOrNull { it.id == sid } },
    )
}

private fun SakiAppUiState.toNowPlayingUiState(): SakiNowPlayingUiState = SakiNowPlayingUiState(
    playbackState = playbackState,
    servers = servers,
    selectedServerId = selectedServerId,
    libraryIndexes = libraryIndexes,
    currentLyrics = currentLyrics,
)

private fun SakiAppUiState.toBrowsePlaybackUiState(): SakiBrowsePlaybackUiState {
    val currentPlaybackSongId = playbackState.currentItem?.songId
        ?: playbackState.queue.getOrNull(playbackState.currentIndex)?.songId
    return SakiBrowsePlaybackUiState(
        currentPlaybackSongId = currentPlaybackSongId,
        isPlaying = playbackState.isPlaying,
    )
}

private fun SakiAppUiState.toBrowseAvailabilityUiState(): SakiBrowseAvailabilityUiState =
    SakiBrowseAvailabilityUiState(
        selectedServerId = selectedServerId,
        cachedSongs = cachedSongs,
        streamCachedSongIds = streamCachedSongIds,
        downloadingSongIds = downloadingSongIds,
        collectionStreamCacheTask = collectionStreamCacheTask,
    )

private fun SakiAppUiState.toBrowseUiState(): SakiBrowseUiState {
    return SakiBrowseUiState(
        appPreferences = appPreferences,
        selectedBrowseSection = selectedBrowseSection,
        browseStack = browseStack,
        servers = servers,
        selectedServerId = selectedServerId,
        selectedAlbumFeed = selectedAlbumFeed,
        libraryIndexes = libraryIndexes,
        isArtistsLoading = isArtistsLoading,
        artistsError = artistsError,
        selectedArtist = selectedArtist,
        selectedArtistSongs = selectedArtistSongs,
        selectedArtistSongsAreTopSongs = selectedArtistSongsAreTopSongs,
        isArtistLoading = isArtistLoading,
        artistError = artistError,
        albumFeeds = albumFeeds,
        selectedAlbum = selectedAlbum,
        isAlbumLoading = isAlbumLoading,
        albumError = albumError,
        playlists = playlists,
        isPlaylistsLoading = isPlaylistsLoading,
        playlistsError = playlistsError,
        selectedPlaylist = selectedPlaylist,
        isPlaylistLoading = isPlaylistLoading,
        playlistError = playlistError,
        songs = songs,
        songsOffset = songsOffset,
        hasPreviousSongs = hasPreviousSongs,
        hasMoreSongs = hasMoreSongs,
        isSongsLoading = isSongsLoading,
        isSongsLoadingPrevious = isSongsLoadingPrevious,
        isSongsLoadingMore = isSongsLoadingMore,
        songsError = songsError,
        selectedSongFeed = selectedSongFeed,
        randomSongs = randomSongs,
        isRandomSongsLoading = isRandomSongsLoading,
        randomSongsError = randomSongsError,
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        searchResults = searchResults,
        isSearchLoading = isSearchLoading,
        searchError = searchError,
        recentSearchQueries = appPreferences.recentSearchQueries,
    )
}

private fun SakiAppUiState.toSettingsUiState(
    isSystemAlacDecoderSupported: Boolean?,
): SakiSettingsUiState = SakiSettingsUiState(
    appPreferences = appPreferences,
    textScale = textScale,
    servers = servers,
    selectedServerId = selectedServerId,
    cachedSongs = cachedSongs,
    cacheStorageSummary = cacheStorageSummary,
    playbackPreferences = playbackState.preferences,
    isSystemAlacDecoderSupported = isSystemAlacDecoderSupported,
    isSongMetadataSyncing = isSongMetadataSyncing,
    songMetadataSyncCount = songMetadataSyncCount,
)

private fun SakiAppUiState.findArtistSummary(artistId: String): ArtistSummary? {
    val indexes = libraryIndexes ?: return null
    return indexes.shortcuts.firstOrNull { it.id == artistId }
        ?: indexes.sections.asSequence()
            .flatMap { section -> section.artists.asSequence() }
            .firstOrNull { it.id == artistId }
}

private fun SakiAppUiState.findAlbumSummary(albumId: String): AlbumSummary? {
    return albumFeeds.values.asSequence()
        .flatMap { feed -> feed.albums.asSequence() }
        .firstOrNull { it.id == albumId }
        ?: selectedArtist?.albums?.firstOrNull { it.id == albumId }
}

private fun SakiAppUiState.findPlaylistSummary(playlistId: String): PlaylistSummary? {
    return playlists.firstOrNull { it.id == playlistId }
}

private fun ArtistSummary.toArtist() = Artist(
    id = id,
    name = name,
    coverArtId = coverArtId,
    artistImageUrl = artistImageUrl,
    albumCount = albumCount,
    albums = emptyList(),
)

private fun AlbumSummary.toAlbum() = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    artists = artists,
    coverArtId = coverArtId,
    songCount = songCount,
    durationSeconds = durationSeconds,
    year = year,
    genre = genre,
    created = created,
    songs = emptyList(),
)

private fun PlaylistSummary.toPlaylist() = Playlist(
    id = id,
    name = name,
    owner = owner,
    isPublic = isPublic,
    songCount = songCount,
    durationSeconds = durationSeconds,
    coverArtId = coverArtId,
    created = created,
    changed = changed,
    songs = emptyList(),
)

private fun emptyAlbumFeedStates(): Map<AlbumListType, AlbumFeedState> {
    return AlbumListType.entries.associateWith { AlbumFeedState(hasMore = it.supportsPagination()) }
}

private fun Map<AlbumListType, AlbumFeedState>.updateFeed(
    type: AlbumListType,
    transform: (AlbumFeedState) -> AlbumFeedState,
): Map<AlbumListType, AlbumFeedState> {
    val current = this[type] ?: AlbumFeedState(hasMore = type.supportsPagination())
    return this + (type to transform(current))
}

private fun Map<AlbumListType, AlbumFeedState>.sortAlbumsForLocale(
    locale: Locale,
    ignoredArticles: String?,
): Map<AlbumListType, AlbumFeedState> {
    return mapValues { (type, feedState) ->
        if (feedState.albums.size < 2 || !type.supportsAlbumFastScroll()) {
            feedState
        } else {
            feedState.copy(
                albums = feedState.albums.sortedForAlbumFeed(
                    type = type,
                    locale = locale,
                    ignoredArticles = ignoredArticles,
                ),
            )
        }
    }
}

private suspend fun Map<AlbumListType, AlbumFeedState>.sortAlbumsForLocaleOnDefault(
    locale: Locale,
    ignoredArticles: String?,
): Map<AlbumListType, AlbumFeedState> {
    if (none { (type, feedState) -> type.supportsAlbumFastScroll() && feedState.albums.size >= 2 }) return this
    return withContext(Dispatchers.Default) {
        sortAlbumsForLocale(locale, ignoredArticles)
    }
}

private suspend fun List<AlbumSummary>.sortedForAlbumFeedOnDefault(
    type: AlbumListType,
    locale: Locale,
    ignoredArticles: String?,
): List<AlbumSummary> {
    if (size < 2 || !type.supportsAlbumFastScroll()) return this
    return withContext(Dispatchers.Default) {
        sortedForAlbumFeed(type, locale, ignoredArticles)
    }
}

private fun List<AlbumSummary>.sortedForAlbumFeed(
    type: AlbumListType,
    locale: Locale,
    ignoredArticles: String?,
): List<AlbumSummary> {
    if (size < 2 || !type.supportsAlbumFastScroll()) return this

    val articles = ignoredArticles.toIgnoredArticleList()
    val collator = Collator.getInstance(locale)
    val stringComparator = Comparator<String> { left, right -> collator.compare(left, right) }
    return sortedWith(
        compareBy<AlbumSummary, String>(stringComparator) {
            it.albumFeedSortValue(type).stripIgnoredArticles(articles)
        }
            .thenBy(stringComparator) { it.name.stripIgnoredArticles(articles) }
            .thenBy { it.id },
    )
}

private fun AlbumSummary.albumFeedSortValue(type: AlbumListType): String {
    return when (type) {
        AlbumListType.ALPHABETICAL_BY_ARTIST -> artist
            ?: artists.firstOrNull()?.name
            ?: name

        else -> name
    }.trim()
}

private fun String?.toIgnoredArticleList(): List<String> {
    return this?.split(' ')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: emptyList()
}

private fun String.stripIgnoredArticles(articles: List<String>): String {
    val value = trim()
    for (article in articles) {
        if (value.startsWith(article, ignoreCase = true) &&
            value.length > article.length &&
            value[article.length].isWhitespace()
        ) {
            return value.substring(article.length + 1).trimStart()
        }
    }
    return value
}

private fun Song.withFallbackAlbumMetadata(album: Album): Song {
    return copy(
        album = this.album ?: album.name,
        albumId = albumId ?: album.id,
        coverArtId = coverArtId ?: album.coverArtId,
    )
}

private fun AlbumListType.supportsPagination(): Boolean {
    return this != AlbumListType.RANDOM && this != AlbumListType.STARRED
}

private fun AlbumListType.supportsAlbumFastScroll(): Boolean {
    return this == AlbumListType.ALPHABETICAL_BY_NAME ||
        this == AlbumListType.ALPHABETICAL_BY_ARTIST
}

private fun AlbumListType.albumFeedPageSize(): Int {
    return if (supportsAlbumFastScroll()) SORTED_ALBUMS_PAGE_SIZE else ALBUMS_PAGE_SIZE
}
