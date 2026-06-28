package org.hdhmc.saki.presentation.library

import android.icu.text.AlphabeticIndex
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.AlbumViewMode
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SongFeedType
import org.hdhmc.saki.domain.model.isUnknownAlbumPlaceholder
import org.hdhmc.saki.presentation.BrowseSection
import org.hdhmc.saki.presentation.AlbumFeedState
import org.hdhmc.saki.presentation.labelRes
import org.hdhmc.saki.presentation.bottomContentPadding
import org.hdhmc.saki.presentation.predictiveBackMotion
import org.hdhmc.saki.presentation.rememberBrowseBackgroundBrush
import org.hdhmc.saki.presentation.SakiBrowseAvailabilityUiState
import org.hdhmc.saki.presentation.SakiBrowsePlaybackUiState
import org.hdhmc.saki.presentation.SakiBrowseUiState
import org.hdhmc.saki.presentation.asString
import org.hdhmc.saki.ui.theme.SakiChromeIconButton
import org.hdhmc.saki.ui.theme.SakiTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    uiState: SakiBrowseUiState,
    playbackUiStateFlow: StateFlow<SakiBrowsePlaybackUiState>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isOfflineDegraded: Boolean,
    contentPadding: PaddingValues,
    bottomOverlayPadding: Dp = 0.dp,
    backHandlersEnabled: Boolean = true,
    onManageServers: () -> Unit,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (AlbumListType) -> Unit,
    onSelectSongFeed: (SongFeedType) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadPreviousSongs: () -> Unit,
    onLoadMoreSongs: () -> Unit,
    onUpdateAlbumViewMode: (AlbumViewMode) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPopDetail: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onQueueSong: (Song) -> Unit,
    onPlaySongNext: (Song) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onToggleSongDownload: (Song) -> Unit,
    onOpenSettings: () -> Unit,
    onImportConfig: (android.net.Uri) -> Unit,
) {
    val background = rememberBrowseBackgroundBrush()
    val currentServer = uiState.servers.firstOrNull { it.id == uiState.selectedServerId }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var detailSong by remember { mutableStateOf<Song?>(null) }
    val offlineAwarePlaySongs: (List<Song>, Int) -> Unit = { songs, startIndex ->
        val availability = availabilityUiStateFlow.value
        playOfflineAwareSongs(
            songs = songs,
            startIndex = startIndex,
            isOfflineDegraded = isOfflineDegraded,
            cachedSongsBySongId = availability.cachedSongsBySongId(),
            streamCachedSongIds = availability.streamCachedSongIds,
            onPlaySongs = onPlaySongs,
            onUnavailable = onOfflineSongUnavailable,
        )
    }
    val scrollState = rememberBrowseScrollState(uiState.selectedServerId)

    BackHandler(enabled = backHandlersEnabled && uiState.browseStack.size > 1) {
        onPopDetail()
    }
    BackHandler(enabled = backHandlersEnabled && uiState.browseStack.size <= 1 && uiState.isSearchActive) {
        onSetSearchActive(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(contentPadding)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        if (currentServer == null) {
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri -> if (uri != null) onImportConfig(uri) }
            NoServerBrowseState(
                modifier = Modifier.weight(1f),
                onManageServers = onManageServers,
                onImportBackup = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        } else {
            if (isOfflineDegraded) {
                OfflineModeBanner(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                val stack = uiState.browseStack.ifEmpty { listOf(BrowseNavRoute.Root) }
                val top = stack.last()
                val below = stack.getOrNull(stack.size - 2)

                @Composable
                fun BrowsePage(route: BrowseNavRoute, pageModifier: Modifier) {
                    Box(modifier = pageModifier) {
                        when (route) {
                            BrowseNavRoute.Root -> BrowsePager(
                                modifier = Modifier.fillMaxSize(),
                                uiState = uiState,
                                currentServer = currentServer,
                                scrollState = scrollState,
                                playbackUiStateFlow = playbackUiStateFlow,
                                availabilityUiStateFlow = availabilityUiStateFlow,
                                isOfflineDegraded = isOfflineDegraded,
                                bottomOverlayPadding = bottomOverlayPadding,
                                backHandlersEnabled = backHandlersEnabled,
                                onSelectBrowseSection = onSelectBrowseSection,
                                onSetSearchActive = onSetSearchActive,
                                onUpdateSearchQuery = onUpdateSearchQuery,
                                onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
                                onClearRecentSearchQueries = onClearRecentSearchQueries,
                                onRefreshCurrentTab = onRefreshCurrentTab,
                                onSelectAlbumFeed = onSelectAlbumFeed,
                                onSelectSongFeed = onSelectSongFeed,
                                onLoadMoreAlbums = onLoadMoreAlbums,
                                onLoadPreviousSongs = onLoadPreviousSongs,
                                onLoadMoreSongs = onLoadMoreSongs,
                                onUpdateAlbumViewMode = onUpdateAlbumViewMode,
                                onOpenArtist = onOpenArtist,
                                onOpenAlbum = onOpenAlbum,
                                onOpenPlaylist = onOpenPlaylist,
                                onPlaySongs = onPlaySongs,
                                onPlayLibrarySongs = onPlayLibrarySongs,
                                onOfflineSongUnavailable = onOfflineSongUnavailable,
                                onShowSongActions = { actionSong = it },
                                onOpenSettings = onOpenSettings,
                            )

                            is BrowseNavRoute.AlbumDetail -> {
                                val album = uiState.selectedAlbum
                                if (album != null) {
                                    AlbumDetailRoute(
                                        server = currentServer,
                                        album = album,
                                        playbackUiStateFlow = playbackUiStateFlow,
                                        availabilityUiStateFlow = availabilityUiStateFlow,
                                        isLoading = uiState.isAlbumLoading,
                                        error = uiState.albumError?.asString(),
                                        bottomOverlayPadding = bottomOverlayPadding,
                                        isOfflineDegraded = isOfflineDegraded,
                                        onOfflineSongUnavailable = onOfflineSongUnavailable,
                                        onPlaySongs = offlineAwarePlaySongs,
                                        onShowActions = { actionSong = it },
                                        onBack = onPopDetail,
                                    )
                                } else {
                                    BrowseDetailPlaceholder(
                                        loadingLabel = stringResource(R.string.library_loading_album),
                                        error = uiState.albumError?.asString(),
                                        bottomOverlayPadding = bottomOverlayPadding,
                                    )
                                }
                            }

                            is BrowseNavRoute.ArtistDetail -> {
                                val artist = uiState.selectedArtist
                                if (artist != null) {
                                    ArtistDetailRoute(
                                        server = currentServer,
                                        artist = artist,
                                        songs = uiState.selectedArtistSongs,
                                        songsAreTopSongs = uiState.selectedArtistSongsAreTopSongs,
                                        playbackUiStateFlow = playbackUiStateFlow,
                                        availabilityUiStateFlow = availabilityUiStateFlow,
                                        isLoading = uiState.isArtistLoading,
                                        error = uiState.artistError?.asString(),
                                        bottomOverlayPadding = bottomOverlayPadding,
                                        isOfflineDegraded = isOfflineDegraded,
                                        onOpenAlbum = onOpenAlbum,
                                        onPlaySongs = offlineAwarePlaySongs,
                                        onShowActions = { actionSong = it },
                                        onBack = onPopDetail,
                                    )
                                } else {
                                    BrowseDetailPlaceholder(
                                        loadingLabel = stringResource(R.string.library_loading_artist),
                                        error = uiState.artistError?.asString(),
                                        bottomOverlayPadding = bottomOverlayPadding,
                                    )
                                }
                            }

                            is BrowseNavRoute.PlaylistDetail -> {
                                val playlist = uiState.selectedPlaylist
                                if (playlist != null) {
                                    PlaylistDetailRoute(
                                        server = currentServer,
                                        playlist = playlist,
                                        playbackUiStateFlow = playbackUiStateFlow,
                                        availabilityUiStateFlow = availabilityUiStateFlow,
                                        isLoading = uiState.isPlaylistLoading,
                                        error = uiState.playlistError?.asString(),
                                        bottomOverlayPadding = bottomOverlayPadding,
                                        isOfflineDegraded = isOfflineDegraded,
                                        onOfflineSongUnavailable = onOfflineSongUnavailable,
                                        onPlaySongs = offlineAwarePlaySongs,
                                        onShowActions = { actionSong = it },
                                        onBack = onPopDetail,
                                    )
                                } else {
                                    BrowseDetailPlaceholder(
                                        loadingLabel = stringResource(R.string.library_loading_playlist),
                                        error = uiState.playlistError?.asString(),
                                        bottomOverlayPadding = bottomOverlayPadding,
                                    )
                                }
                            }
                        }
                    }
                }

                val pages = listOfNotNull(below, top)
                pages.forEachIndexed { index, route ->
                    val isTop = index == pages.lastIndex
                    key(route) {
                        val pageModifier = when {
                            !isTop && route !is BrowseNavRoute.Root -> Modifier
                                .fillMaxSize()
                                .background(background)
                            !isTop -> Modifier.fillMaxSize()
                            route is BrowseNavRoute.Root -> Modifier.fillMaxSize()
                            else -> Modifier
                                .fillMaxSize()
                                .predictiveBackMotion(
                                    enabled = backHandlersEnabled,
                                    onBack = onPopDetail,
                                )
                                .background(background)
                        }
                        BrowsePage(route, pageModifier)
                    }
                }
            }
        }
    }

    actionSong?.let { song ->
        SongActionsSheetRoute(
            song = song,
            availabilityUiStateFlow = availabilityUiStateFlow,
            isOfflineDegraded = isOfflineDegraded,
            onOfflineSongUnavailable = onOfflineSongUnavailable,
            onDismiss = { actionSong = null },
            onPlayNext = {
                onPlaySongNext(song)
                actionSong = null
            },
            onToggleDownload = {
                onToggleSongDownload(song)
                actionSong = null
            },
            onDetails = {
                detailSong = song
                actionSong = null
            },
            onQueueSong = {
                onQueueSong(song)
                actionSong = null
            },
        )
    }

    detailSong?.let { song ->
        SongDetailsDialog(song = song, onDismiss = { detailSong = null })
    }
}

@Composable
private fun BrowseDetailPlaceholder(
    loadingLabel: String,
    error: String?,
    bottomOverlayPadding: Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        item {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                if (error != null) {
                    ErrorStateCard(error)
                } else {
                    LoadingStateCard(loadingLabel)
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailRoute(
    server: ServerConfig,
    album: Album,
    playbackUiStateFlow: StateFlow<SakiBrowsePlaybackUiState>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    isOfflineDegraded: Boolean,
    onOfflineSongUnavailable: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
    onBack: () -> Unit,
) {
    val playbackUiState by playbackUiStateFlow.collectAsStateWithLifecycle()
    val availabilityUiState by availabilityUiStateFlow.collectAsStateWithLifecycle()
    val cachedSongsBySongId = rememberCachedSongsBySongId(availabilityUiState)
    AlbumDetailScreen(
        server = server,
        album = album,
        cachedSongsBySongId = cachedSongsBySongId,
        streamCachedSongIds = availabilityUiState.streamCachedSongIds,
        downloadingSongIds = availabilityUiState.downloadingSongIds,
        isLoading = isLoading,
        error = error,
        bottomOverlayPadding = bottomOverlayPadding,
        isOfflineDegraded = isOfflineDegraded,
        onOfflineSongUnavailable = onOfflineSongUnavailable,
        currentPlaybackSongId = playbackUiState.currentPlaybackSongId,
        isPlaying = playbackUiState.isPlaying,
        onPlaySongs = onPlaySongs,
        onShowActions = onShowActions,
        onBack = onBack,
    )
}

@Composable
private fun ArtistDetailRoute(
    server: ServerConfig,
    artist: Artist,
    songs: List<Song>,
    songsAreTopSongs: Boolean,
    playbackUiStateFlow: StateFlow<SakiBrowsePlaybackUiState>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    isOfflineDegraded: Boolean,
    onOpenAlbum: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
    onBack: () -> Unit,
) {
    val playbackUiState by playbackUiStateFlow.collectAsStateWithLifecycle()
    val availabilityUiState by availabilityUiStateFlow.collectAsStateWithLifecycle()
    val cachedSongsBySongId = rememberCachedSongsBySongId(availabilityUiState)
    ArtistDetailScreen(
        server = server,
        artist = artist,
        songs = songs,
        songsAreTopSongs = songsAreTopSongs,
        cachedSongsBySongId = cachedSongsBySongId,
        streamCachedSongIds = availabilityUiState.streamCachedSongIds,
        downloadingSongIds = availabilityUiState.downloadingSongIds,
        isLoading = isLoading,
        error = error,
        bottomOverlayPadding = bottomOverlayPadding,
        isOfflineDegraded = isOfflineDegraded,
        onOpenAlbum = onOpenAlbum,
        onPlaySongs = onPlaySongs,
        onShowActions = onShowActions,
        currentPlaybackSongId = playbackUiState.currentPlaybackSongId,
        isPlaying = playbackUiState.isPlaying,
        onBack = onBack,
    )
}

@Composable
private fun PlaylistDetailRoute(
    server: ServerConfig,
    playlist: Playlist,
    playbackUiStateFlow: StateFlow<SakiBrowsePlaybackUiState>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    isOfflineDegraded: Boolean,
    onOfflineSongUnavailable: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
    onBack: () -> Unit,
) {
    val playbackUiState by playbackUiStateFlow.collectAsStateWithLifecycle()
    val availabilityUiState by availabilityUiStateFlow.collectAsStateWithLifecycle()
    val cachedSongsBySongId = rememberCachedSongsBySongId(availabilityUiState)
    PlaylistDetailScreen(
        server = server,
        playlist = playlist,
        cachedSongsBySongId = cachedSongsBySongId,
        streamCachedSongIds = availabilityUiState.streamCachedSongIds,
        downloadingSongIds = availabilityUiState.downloadingSongIds,
        isLoading = isLoading,
        error = error,
        bottomOverlayPadding = bottomOverlayPadding,
        isOfflineDegraded = isOfflineDegraded,
        onOfflineSongUnavailable = onOfflineSongUnavailable,
        onPlaySongs = onPlaySongs,
        onShowActions = onShowActions,
        currentPlaybackSongId = playbackUiState.currentPlaybackSongId,
        isPlaying = playbackUiState.isPlaying,
        onBack = onBack,
    )
}

@Composable
private fun SongActionsSheetRoute(
    song: Song,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isOfflineDegraded: Boolean,
    onOfflineSongUnavailable: () -> Unit,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleDownload: () -> Unit,
    onDetails: () -> Unit,
    onQueueSong: () -> Unit,
) {
    val availabilityUiState by availabilityUiStateFlow.collectAsStateWithLifecycle()
    val cachedSongsBySongId = rememberCachedSongsBySongId(availabilityUiState)
    val isDownloaded = cachedSongsBySongId.containsKey(song.id)
    val isOfflinePlayable = song.isOfflinePlayable(
        cachedSongsBySongId = cachedSongsBySongId,
        streamCachedSongIds = availabilityUiState.streamCachedSongIds,
    )
    SongActionsSheet(
        song = song,
        isDownloaded = isDownloaded,
        isDownloading = song.id in availabilityUiState.downloadingSongIds,
        onDismiss = onDismiss,
        onPlayNext = {
            if (isOfflineDegraded && !isOfflinePlayable) {
                onOfflineSongUnavailable()
                onDismiss()
            } else {
                onPlayNext()
            }
        },
        onToggleDownload = {
            if (isOfflineDegraded && !isDownloaded) {
                onOfflineSongUnavailable()
                onDismiss()
            } else {
                onToggleDownload()
            }
        },
        onDetails = onDetails,
        onQueueSong = {
            if (isOfflineDegraded && !isOfflinePlayable) {
                onOfflineSongUnavailable()
                onDismiss()
            } else {
                onQueueSong()
            }
        },
    )
}

@Composable
private fun rememberCachedSongsBySongId(
    availabilityUiState: SakiBrowseAvailabilityUiState,
): Map<String, CachedSong> {
    return remember(availabilityUiState.cachedSongs, availabilityUiState.selectedServerId) {
        availabilityUiState.cachedSongsBySongId()
    }
}

private fun SakiBrowseAvailabilityUiState.cachedSongsBySongId(): Map<String, CachedSong> {
    return cachedSongs
        .asSequence()
        .filter { cachedSong -> cachedSong.serverId == selectedServerId }
        .associateBy(CachedSong::songId)
}

private class BrowseScrollState(
    val searchResultsPosition: LazyListScrollPosition = LazyListScrollPosition(),
    val recentSearchesPosition: LazyListScrollPosition = LazyListScrollPosition(),
    val artistsPosition: LazyListScrollPosition = LazyListScrollPosition(),
    val albumFeedPositions: Map<AlbumListType, AlbumFeedScrollPosition>,
    val playlistsPosition: LazyListScrollPosition = LazyListScrollPosition(),
    val songsPosition: LazyListScrollPosition = LazyListScrollPosition(),
)

private class AlbumFeedScrollPosition(
    val gridPosition: LazyGridScrollPosition = LazyGridScrollPosition(),
    val listPosition: LazyListScrollPosition = LazyListScrollPosition(),
)

private class LazyListScrollPosition(
    var index: Int = 0,
    var scrollOffset: Int = 0,
)

private class LazyGridScrollPosition(
    var index: Int = 0,
    var scrollOffset: Int = 0,
)

@Composable
private fun rememberBrowseScrollState(serverId: Long?): BrowseScrollState {
    return remember(serverId) {
        BrowseScrollState(
            albumFeedPositions = AlbumListType.defaultBrowseFeeds.associateWith { AlbumFeedScrollPosition() },
        )
    }
}

@Composable
private fun rememberRestoredLazyListState(position: LazyListScrollPosition): LazyListState {
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = position.index,
        initialFirstVisibleItemScrollOffset = position.scrollOffset,
    )
    LaunchedEffect(state, position) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, scrollOffset) ->
                position.index = index
                position.scrollOffset = scrollOffset
            }
    }
    return state
}

@Composable
private fun rememberRestoredLazyGridState(position: LazyGridScrollPosition): LazyGridState {
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = position.index,
        initialFirstVisibleItemScrollOffset = position.scrollOffset,
    )
    LaunchedEffect(state, position) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, scrollOffset) ->
                position.index = index
                position.scrollOffset = scrollOffset
            }
    }
    return state
}

@Composable
private fun OfflineModeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.browse_offline_degraded_banner),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowsePager(
    modifier: Modifier,
    uiState: SakiBrowseUiState,
    currentServer: ServerConfig,
    scrollState: BrowseScrollState,
    playbackUiStateFlow: StateFlow<SakiBrowsePlaybackUiState>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isOfflineDegraded: Boolean,
    bottomOverlayPadding: Dp,
    backHandlersEnabled: Boolean,
    onSelectBrowseSection: (BrowseSection) -> Unit,
    onSetSearchActive: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onRefreshCurrentTab: () -> Unit,
    onSelectAlbumFeed: (AlbumListType) -> Unit,
    onSelectSongFeed: (SongFeedType) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadPreviousSongs: () -> Unit,
    onLoadMoreSongs: () -> Unit,
    onUpdateAlbumViewMode: (AlbumViewMode) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val background = rememberBrowseBackgroundBrush()
    val sections = BrowseSection.entries
    val selectedSectionState = rememberUpdatedState(uiState.selectedBrowseSection)
    val pagerState = rememberPagerState(
        initialPage = sections.indexOf(uiState.selectedBrowseSection).coerceAtLeast(0),
        pageCount = { sections.size },
    )
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    LaunchedEffect(uiState.selectedBrowseSection) {
        val targetPage = sections.indexOf(uiState.selectedBrowseSection).coerceAtLeast(0)
        if (pagerState.settledPage != targetPage && pagerState.targetPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .map { sections[it] }
            .filter { it != selectedSectionState.value }
            .collect { onSelectBrowseSection(it) }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowseHeroCard(
                currentServer = currentServer,
                isSearchActive = false,
                searchQuery = "",
                onSearchActiveChange = onSetSearchActive,
                onSearchQueryChange = onUpdateSearchQuery,
                onOpenSettings = onOpenSettings,
            )
            Box(modifier = Modifier.weight(1f)) {
                val isRefreshing = uiState.isArtistsLoading || uiState.isAlbumsLoading ||
                    uiState.isPlaylistsLoading || uiState.isSongsLoading || uiState.isRandomSongsLoading
                val pullState = rememberPullToRefreshState()
                val haptic = LocalHapticFeedback.current
                val isOverThreshold = !isRefreshing && pullState.distanceFraction >= 1f
                LaunchedEffect(isOverThreshold) {
                    if (isOverThreshold) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefreshCurrentTab,
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        if (SakiTheme.visuals.useExpressiveLoadingIndicator) {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = pullState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .size(SakiTheme.visuals.pullRefreshLoadingIndicatorSize),
                            )
                        } else {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    },
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sections) { section ->
                                BrowseSectionChip(
                                    section = section,
                                    selected = uiState.selectedBrowseSection == section,
                                    onClick = { onSelectBrowseSection(section) },
                                )
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            flingBehavior = pagerFlingBehavior,
                        ) { page ->
                            when (sections[page]) {
                                BrowseSection.ARTISTS -> ArtistsPage(
                                    indexes = uiState.libraryIndexes,
                                    server = currentServer,
                                    isLoading = uiState.isArtistsLoading,
                                    error = uiState.artistsError?.asString(),
                                    bottomOverlayPadding = bottomOverlayPadding,
                                    scrollPosition = scrollState.artistsPosition,
                                    onOpenArtist = onOpenArtist,
                                )

                                BrowseSection.ALBUMS -> AlbumsPage(
                                    albumFeeds = uiState.albumFeeds,
                                    browsePagerState = pagerState,
                                    server = currentServer,
                                    selectedFeed = uiState.selectedAlbumFeed,
                                    viewMode = uiState.appPreferences.albumViewMode,
                                    ignoredArticles = uiState.libraryIndexes?.ignoredArticles,
                                    scrollPositions = scrollState.albumFeedPositions,
                                    onSelectFeed = onSelectAlbumFeed,
                                    onLoadMore = onLoadMoreAlbums,
                                    onUpdateViewMode = onUpdateAlbumViewMode,
                                    onOpenAlbum = onOpenAlbum,
                                    bottomOverlayPadding = bottomOverlayPadding,
                                )

                                BrowseSection.PLAYLISTS -> PlaylistsPage(
                                    playlists = uiState.playlists,
                                    server = currentServer,
                                    isLoading = uiState.isPlaylistsLoading,
                                    error = uiState.playlistsError?.asString(),
                                    bottomOverlayPadding = bottomOverlayPadding,
                                    scrollPosition = scrollState.playlistsPosition,
                                    onOpenPlaylist = onOpenPlaylist,
                                )

                                BrowseSection.SONGS -> SongsPageRoute(
                                    browsePagerState = pagerState,
                                    songs = uiState.songs,
                                    songsOffset = uiState.songsOffset,
                                    hasPrevious = uiState.hasPreviousSongs,
                                    hasMore = uiState.hasMoreSongs,
                                    server = currentServer,
                                    playbackUiStateFlow = playbackUiStateFlow,
                                    availabilityUiStateFlow = availabilityUiStateFlow,
                                    isOfflineDegraded = isOfflineDegraded,
                                    isLoading = uiState.isSongsLoading,
                                    isLoadingPrevious = uiState.isSongsLoadingPrevious,
                                    isLoadingMore = uiState.isSongsLoadingMore,
                                    error = uiState.songsError?.asString(),
                                    bottomOverlayPadding = bottomOverlayPadding,
                                    scrollPosition = scrollState.songsPosition,
                                    onLoadPrevious = onLoadPreviousSongs,
                                    onLoadMore = onLoadMoreSongs,
                                    onPlaySongs = onPlaySongs,
                                    onPlayLibrarySongs = onPlayLibrarySongs,
                                    onOfflineSongUnavailable = onOfflineSongUnavailable,
                                    onShowSongActions = onShowSongActions,
                                    selectedSongFeed = uiState.selectedSongFeed,
                                    randomSongs = uiState.randomSongs,
                                    isRandomSongsLoading = uiState.isRandomSongsLoading,
                                    randomSongsError = uiState.randomSongsError?.asString(),
                                    onSelectSongFeed = onSelectSongFeed,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isSearchActive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .predictiveBackMotion(
                        enabled = backHandlersEnabled,
                        onBack = { onSetSearchActive(false) },
                        maxScaleReduction = 0.02f,
                        maxHorizontalShiftFraction = 0.12f,
                        horizontalShiftInset = 0.dp,
                        maxVerticalShiftFraction = 0f,
                        verticalShiftInset = 0.dp,
                        maxCornerRadius = 18.dp,
                        targetAlpha = 0f,
                    )
                    .background(background),
            ) {
                BrowseHeroCard(
                    currentServer = currentServer,
                    isSearchActive = true,
                    searchQuery = uiState.searchQuery,
                    onSearchActiveChange = onSetSearchActive,
                    onSearchQueryChange = onUpdateSearchQuery,
                    onOpenSettings = onOpenSettings,
                )
                SearchResultsRoute(
                    modifier = Modifier.weight(1f),
                    currentServer = currentServer,
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isLoading = uiState.isSearchLoading,
                    error = uiState.searchError?.asString(),
                    recentSearchQueries = uiState.recentSearchQueries,
                    availabilityUiStateFlow = availabilityUiStateFlow,
                    isOfflineDegraded = isOfflineDegraded,
                    bottomOverlayPadding = bottomOverlayPadding,
                    resultsPosition = scrollState.searchResultsPosition,
                    recentSearchesPosition = scrollState.recentSearchesPosition,
                    onSearchQuery = onUpdateSearchQuery,
                    onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
                    onClearRecentSearchQueries = onClearRecentSearchQueries,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onPlaySongs = onPlaySongs,
                    onOfflineSongUnavailable = onOfflineSongUnavailable,
                    onShowSongActions = onShowSongActions,
                )
            }
        }
    }
}

@Composable
private fun BrowseHeroCard(
    currentServer: ServerConfig,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSearchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        text = stringResource(R.string.browse_search_placeholder, currentServer.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { onSearchActiveChange(false) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.browse_close_search))
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
            )
        } else {
            Text(
                text = currentServer.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            SakiChromeIconButton(
                onClick = { onSearchActiveChange(true) },
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.browse_search_server),
            )
            Spacer(modifier = Modifier.width(8.dp))
            SakiChromeIconButton(
                onClick = onOpenSettings,
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.browse_settings),
            )
        }
    }
}

@Composable
private fun BrowseSectionChip(
    section: BrowseSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val visuals = SakiTheme.visuals
    if (visuals.browseSectionChipSelectedContainerAlpha <= 0f) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = section.localizedLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            },
        )
    } else {
        val chipShape = RoundedCornerShape(visuals.browseSectionChipCornerRadius)
        Surface(
            modifier = Modifier
                .clip(chipShape)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                ),
            shape = chipShape,
            color = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = if (selected || visuals.browseSectionChipOutlineAlpha <= 0f) {
                null
            } else {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = visuals.browseSectionChipOutlineAlpha,
                    ),
                )
            },
        ) {
            Text(
                text = section.localizedLabel(),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SearchResultsRoute(
    modifier: Modifier,
    currentServer: ServerConfig,
    query: String,
    results: SearchResults,
    isLoading: Boolean,
    error: String?,
    recentSearchQueries: List<String>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isOfflineDegraded: Boolean,
    bottomOverlayPadding: Dp,
    resultsPosition: LazyListScrollPosition,
    recentSearchesPosition: LazyListScrollPosition,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val availabilityUiState by availabilityUiStateFlow.collectAsStateWithLifecycle()
    val cachedSongsBySongId = rememberCachedSongsBySongId(availabilityUiState)
    SearchResultsPage(
        modifier = modifier,
        currentServer = currentServer,
        query = query,
        results = results,
        isLoading = isLoading,
        error = error,
        recentSearchQueries = recentSearchQueries,
        cachedSongsBySongId = cachedSongsBySongId,
        streamCachedSongIds = availabilityUiState.streamCachedSongIds,
        downloadingSongIds = availabilityUiState.downloadingSongIds,
        isOfflineDegraded = isOfflineDegraded,
        bottomOverlayPadding = bottomOverlayPadding,
        resultsPosition = resultsPosition,
        recentSearchesPosition = recentSearchesPosition,
        onSearchQuery = onSearchQuery,
        onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
        onClearRecentSearchQueries = onClearRecentSearchQueries,
        onOpenArtist = onOpenArtist,
        onOpenAlbum = onOpenAlbum,
        onPlaySongs = onPlaySongs,
        onOfflineSongUnavailable = onOfflineSongUnavailable,
        onShowSongActions = onShowSongActions,
    )
}

@Composable
private fun SearchResultsPage(
    modifier: Modifier,
    currentServer: ServerConfig,
    query: String,
    results: SearchResults,
    isLoading: Boolean,
    error: String?,
    recentSearchQueries: List<String>,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    bottomOverlayPadding: Dp,
    resultsPosition: LazyListScrollPosition,
    recentSearchesPosition: LazyListScrollPosition,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val trimmedQuery = query.trim()
    when {
        trimmedQuery.isBlank() -> RecentSearchesPage(
            modifier = modifier,
            currentServer = currentServer,
            recentSearchQueries = recentSearchQueries,
            bottomOverlayPadding = bottomOverlayPadding,
            scrollPosition = recentSearchesPosition,
            onSearchQuery = onSearchQuery,
            onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
            onClearRecentSearchQueries = onClearRecentSearchQueries,
        )

        isLoading -> Box(modifier = modifier) {
            LoadingStateCard(stringResource(R.string.browse_searching_server, currentServer.name))
        }

        error != null -> Box(modifier = modifier) {
            ErrorStateCard(error)
        }

        results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty() -> Box(modifier = modifier) {
            EmptyStateCard(
                title = stringResource(R.string.browse_no_results),
                body = stringResource(R.string.browse_no_results_body, trimmedQuery, currentServer.name),
            )
        }

        else -> {
            var artistsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
            var albumsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
            var songsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
            val visibleArtists = if (artistsExpanded) {
                results.artists
            } else {
                results.artists.take(SearchResultPreviewCount)
            }
            val visibleAlbums = if (albumsExpanded) {
                results.albums
            } else {
                results.albums.take(SearchResultPreviewCount)
            }
            val visibleSongs = if (songsExpanded) {
                results.songs
            } else {
                results.songs.take(SearchResultPreviewCount)
            }

            val listState = rememberRestoredLazyListState(resultsPosition)
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
                if (results.artists.isNotEmpty()) {
                    item(key = "artists-header") {
                        SearchResultSectionHeader(
                            title = stringResource(R.string.browse_artists),
                            subtitle = matchCountText(results.artists.size),
                            expanded = artistsExpanded,
                            canToggle = results.artists.size > SearchResultPreviewCount,
                            onToggle = { artistsExpanded = !artistsExpanded },
                        )
                    }
                    items(visibleArtists, key = { "artist-${it.id}" }) { artist ->
                        ArtistRow(artist = artist, onOpenArtist = onOpenArtist)
                    }
                }

                if (results.albums.isNotEmpty()) {
                    item(key = "albums-header") {
                        SearchResultSectionHeader(
                            title = stringResource(R.string.library_albums),
                            subtitle = matchCountText(results.albums.size),
                            expanded = albumsExpanded,
                            canToggle = results.albums.size > SearchResultPreviewCount,
                            onToggle = { albumsExpanded = !albumsExpanded },
                        )
                    }
                    items(visibleAlbums, key = { "album-${it.id}" }) { album ->
                        AlbumRow(album = album, server = currentServer, onOpenAlbum = onOpenAlbum)
                    }
                }

                if (results.songs.isNotEmpty()) {
                    item(key = "songs-header") {
                        SearchResultSectionHeader(
                            title = stringResource(R.string.browse_songs),
                            subtitle = matchCountText(results.songs.size),
                            expanded = songsExpanded,
                            canToggle = results.songs.size > SearchResultPreviewCount,
                            onToggle = { songsExpanded = !songsExpanded },
                        )
                    }
                    itemsIndexed(visibleSongs, key = { _, s -> "song-${s.id}" }) { index, song ->
                        val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
                        SongRow(
                            song = song,
                            server = currentServer,
                            cachedSong = cachedSongsBySongId[song.id],
                            isStreamCached = song.id in streamCachedSongIds,
                            isDownloading = song.id in downloadingSongIds,
                            isOfflineDegraded = isOfflineDegraded,
                            isOfflinePlayable = isOfflinePlayable,
                            onClick = {
                                playOfflineAwareSongs(
                                    songs = results.songs,
                                    startIndex = index,
                                    isOfflineDegraded = isOfflineDegraded,
                                    cachedSongsBySongId = cachedSongsBySongId,
                                    streamCachedSongIds = streamCachedSongIds,
                                    onPlaySongs = onPlaySongs,
                                    onUnavailable = onOfflineSongUnavailable,
                                )
                            },
                            onMore = { onShowSongActions(song) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSearchesPage(
    modifier: Modifier,
    currentServer: ServerConfig,
    recentSearchQueries: List<String>,
    bottomOverlayPadding: Dp,
    scrollPosition: LazyListScrollPosition,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
    onClearRecentSearchQueries: () -> Unit,
) {
    if (recentSearchQueries.isEmpty()) {
        Box(modifier = modifier) {
            EmptyStateCard(
                title = stringResource(R.string.browse_search_server),
                body = stringResource(R.string.browse_search_server_body, currentServer.name),
            )
        }
        return
    }

    val listState = rememberRestoredLazyListState(scrollPosition)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        item(key = "recent-searches-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.browse_recent_searches), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClearRecentSearchQueries, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.browse_clear_search_history))
                }
            }
        }

        items(recentSearchQueries, key = { it }) { query ->
            RecentSearchRow(
                query = query,
                onSearchQuery = onSearchQuery,
                onRemoveRecentSearchQuery = onRemoveRecentSearchQuery,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentSearchRow(
    query: String,
    onSearchQuery: (String) -> Unit,
    onRemoveRecentSearchQuery: (String) -> Unit,
) {
    val searchLabel = stringResource(R.string.browse_run_recent_search, query)
    val removeLabel = stringResource(R.string.browse_remove_recent_search, query)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { onSearchQuery(query) },
                onClickLabel = searchLabel,
                onLongClick = { onRemoveRecentSearchQuery(query) },
                onLongClickLabel = removeLabel,
                role = Role.Button,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = query,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { onRemoveRecentSearchQuery(query) }) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = removeLabel,
            )
        }
    }
}

@Composable
private fun SearchResultSectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
) {
    val actionLabel = stringResource(
        if (expanded) R.string.browse_collapse_results else R.string.browse_show_all_results,
    )
    val headerModifier = if (canToggle) {
        Modifier.clickable(onClick = onToggle)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(headerModifier)
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (canToggle) {
                TextButton(onClick = onToggle, shape = MaterialTheme.shapes.small) {
                    Text(actionLabel)
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
            }
        }
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val SearchResultPreviewCount = 5

@Composable
private fun ArtistsPage(
    indexes: org.hdhmc.saki.domain.model.LibraryIndexes?,
    server: ServerConfig,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    scrollPosition: LazyListScrollPosition,
    onOpenArtist: (String) -> Unit,
) {
    if (isLoading && indexes == null) {
        LoadingStateCard(stringResource(R.string.browse_loading_artists))
        return
    }
    if (error != null && indexes == null) {
        ErrorStateCard(error)
        return
    }
    if (indexes == null) {
        EmptyStateCard(
            stringResource(R.string.browse_no_artists),
            stringResource(R.string.browse_no_artists_body),
        )
        return
    }

    // Build section-to-item-index mapping for scroll bar
    val nonEmptySections = remember(indexes) { indexes.sections.filter { it.artists.isNotEmpty() } }
    // Scroll bar: # A-Z, plus … for any non-Latin sections
    val scrollBarMapping = remember(nonEmptySections) {
        val result = mutableListOf<Pair<String, Int>>()
        var hasOther = false
        var firstOtherIdx = -1
        nonEmptySections.forEachIndexed { idx, section ->
            val name = section.name
            when {
                name.length == 1 && name[0] in 'A'..'Z' -> result.add(name to idx)
                name == "#" -> result.add(0, "#" to idx) // # always first
                else -> {
                    if (!hasOther) { firstOtherIdx = idx; hasOther = true }
                }
            }
        }
        if (hasOther) result.add("…" to firstOtherIdx)
        result
    }
    val visibleScrollLabels = remember(scrollBarMapping) { scrollBarMapping.map { it.first } }
    val scrollLabelToSection = remember(scrollBarMapping) { scrollBarMapping.toMap() }
    val sectionItemIndices = remember(indexes, nonEmptySections) {
        val map = mutableMapOf<Int, Int>()
        var itemIndex = if (indexes.shortcuts.isNotEmpty()) 2 else 0 // shortcuts title + row
        nonEmptySections.forEachIndexed { sectionIdx, section ->
            map[sectionIdx] = itemIndex
            itemIndex += 1 + section.artists.size // section title + artists
        }
        map
    }

    val listState = rememberRestoredLazyListState(scrollPosition)
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + bottomOverlayPadding, end = 24.dp),
        ) {
            if (indexes.shortcuts.isNotEmpty()) {
                item {
                    LazyRow {
                        items(indexes.shortcuts, key = { it.id }) { artist ->
                            ArtistShortcutCard(artist = artist, onOpenArtist = onOpenArtist)
                        }
                    }
                }
            }
            nonEmptySections.forEach { section ->
                item { SectionTitle(section.name, artistCountText(section.artists.size)) }
                items(section.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onOpenArtist = onOpenArtist)
                }
            }
        }

        var showScrollBar by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(500)
            showScrollBar = true
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showScrollBar && visibleScrollLabels.size > 1,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 8.dp, bottom = 8.dp + bottomOverlayPadding),
        ) {
            AlphabetScrollBar(
                labels = visibleScrollLabels,
                onScrollTo = { idx ->
                    val label = visibleScrollLabels.getOrNull(idx) ?: return@AlphabetScrollBar
                    val sectionIdx = scrollLabelToSection[label] ?: return@AlphabetScrollBar
                    val itemIdx = sectionItemIndices[sectionIdx] ?: return@AlphabetScrollBar
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch { listState.scrollToItem(itemIdx) }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsPage(
    albumFeeds: Map<AlbumListType, AlbumFeedState>,
    browsePagerState: PagerState,
    server: ServerConfig,
    selectedFeed: AlbumListType,
    viewMode: AlbumViewMode,
    ignoredArticles: String?,
    scrollPositions: Map<AlbumListType, AlbumFeedScrollPosition>,
    onSelectFeed: (AlbumListType) -> Unit,
    onLoadMore: () -> Unit,
    onUpdateViewMode: (AlbumViewMode) -> Unit,
    onOpenAlbum: (String) -> Unit,
    bottomOverlayPadding: Dp,
) {
    val feeds = AlbumListType.defaultBrowseFeeds
    val selectedFeedState = rememberUpdatedState(selectedFeed)
    val feedPagerState = rememberPagerState(
        initialPage = feeds.indexOf(selectedFeed).coerceAtLeast(0),
        pageCount = { feeds.size },
    )
    val feedPagerFlingBehavior = PagerDefaults.flingBehavior(
        state = feedPagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    val browsePagerFlingBehavior = PagerDefaults.flingBehavior(
        state = browsePagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    val feedBoundaryHandoffConnection = rememberFeedBoundaryHandoffConnection(
        feedPagerState = feedPagerState,
        browsePagerState = browsePagerState,
        browsePagerFlingBehavior = browsePagerFlingBehavior,
    )
    val coroutineScope = rememberCoroutineScope()
    val highlightedFeed = feeds[feedPagerState.targetPage.coerceIn(0, feeds.lastIndex)]

    LaunchedEffect(selectedFeed) {
        val targetPage = feeds.indexOf(selectedFeed).coerceAtLeast(0)
        if (feedPagerState.settledPage != targetPage && feedPagerState.targetPage != targetPage) {
            feedPagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(feedPagerState) {
        snapshotFlow { feedPagerState.settledPage }
            .distinctUntilChanged()
            .map { feeds[it] }
            .filter { it != selectedFeedState.value }
            .collect { onSelectFeed(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AlbumFeedControls(
            feeds = feeds,
            selectedFeed = highlightedFeed,
            viewMode = viewMode,
            onSelectFeed = { feed ->
                val targetPage = feeds.indexOf(feed)
                if (targetPage >= 0 && feedPagerState.targetPage != targetPage) {
                    coroutineScope.launch { feedPagerState.animateScrollToPage(targetPage) }
                }
            },
            onUpdateViewMode = onUpdateViewMode,
        )

        HorizontalPager(
            state = feedPagerState,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(feedBoundaryHandoffConnection),
            flingBehavior = feedPagerFlingBehavior,
        ) { page ->
            val feed = feeds[page]
            val feedState = albumFeeds[feed] ?: AlbumFeedState()
            val scrollPosition = scrollPositions.getValue(feed)
            AlbumFeedPageContent(
                feed = feed,
                albums = feedState.albums,
                server = server,
                viewMode = viewMode,
                ignoredArticles = ignoredArticles,
                scrollPosition = scrollPosition,
                isLoading = feedState.isLoading,
                hasMore = feedState.hasMore,
                isLoadingMore = feedState.isLoadingMore,
                error = feedState.error?.asString(),
                canLoadMore = feed == selectedFeed,
                onLoadMore = onLoadMore,
                onOpenAlbum = onOpenAlbum,
                bottomOverlayPadding = bottomOverlayPadding,
            )
        }
    }
}

@Composable
private fun rememberFeedBoundaryHandoffConnection(
    feedPagerState: PagerState,
    browsePagerState: PagerState,
    browsePagerFlingBehavior: TargetedFlingBehavior,
): NestedScrollConnection {
    return remember(feedPagerState, browsePagerState, browsePagerFlingBehavior) {
        object : NestedScrollConnection {
            // Tracks whether the current gesture has started driving the browse
            // pager, so the matching fling is only forwarded for that gesture.
            private var handedOffGesture = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) {
                    return Offset.Zero
                }

                val scrollDelta = -available.x
                if (!feedPagerState.shouldHandOffFeedDelta(scrollDelta)) {
                    handedOffGesture = false
                    return Offset.Zero
                }

                // The inner pager is settled at its boundary, so forward the raw
                // drag 1:1 to the browse pager. The inner pager already consumed
                // touch slop, so no extra dead zone is applied here -- this makes
                // dragging across tabs feel identical to dragging the browse pager
                // directly.
                handedOffGesture = true
                val consumed = browsePagerState.dispatchRawDelta(scrollDelta)
                return Offset(x = -consumed, y = 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!handedOffGesture) {
                    return Velocity.Zero
                }
                handedOffGesture = false

                // Settle the browse pager with its own fling behavior so the
                // page-switch threshold (velocity + position) matches a normal
                // browse-pager swipe, instead of a custom velocity threshold.
                val flingVelocity = -available.x
                var remainingVelocity = flingVelocity
                browsePagerState.scroll {
                    with(browsePagerFlingBehavior) {
                        remainingVelocity = performFling(flingVelocity)
                    }
                }
                val consumedVelocity = flingVelocity - remainingVelocity
                return Velocity(x = -consumedVelocity, y = 0f)
            }
        }
    }
}

private fun PagerState.shouldHandOffFeedDelta(scrollDelta: Float): Boolean {
    val isSettledAtFeedBoundary = currentPage == settledPage &&
        currentPage == targetPage &&
        abs(currentPageOffsetFraction) <= PagerOffsetSettlingEpsilon
    if (!isSettledAtFeedBoundary) {
        return false
    }
    return when {
        scrollDelta > 0f -> settledPage >= pageCount - 1
        scrollDelta < 0f -> settledPage <= 0
        else -> false
    }
}

private const val PagerOffsetSettlingEpsilon = 0.001f

@Composable
private fun AlbumFeedPageContent(
    feed: AlbumListType,
    albums: List<AlbumSummary>,
    server: ServerConfig,
    viewMode: AlbumViewMode,
    ignoredArticles: String?,
    scrollPosition: AlbumFeedScrollPosition,
    isLoading: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    bottomOverlayPadding: Dp,
) {
    val fastScrollIndex = remember(feed, albums, ignoredArticles) {
        albums.albumFastScrollIndex(feed, ignoredArticles)
    }
    val contentPadding = albumFeedContentPadding(
        bottomOverlayPadding = bottomOverlayPadding,
        hasFastScroll = fastScrollIndex != null,
    )
    val coroutineScope = rememberCoroutineScope()
    when (viewMode) {
        AlbumViewMode.GRID -> {
            val gridState = rememberRestoredLazyGridState(scrollPosition.gridPosition)
            LaunchedEffect(gridState, canLoadMore, hasMore, isLoading, isLoadingMore, albums.size) {
                snapshotFlow {
                    if (!canLoadMore || !hasMore || isLoading || isLoadingMore || albums.isEmpty()) {
                        false
                    } else {
                        val layoutInfo = gridState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisibleIndex >= layoutInfo.totalItemsCount - 5
                    }
                }
                    .distinctUntilChanged()
                    .filter { shouldLoad -> shouldLoad }
                    .collect { onLoadMore() }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(2),
                    contentPadding = contentPadding,
                ) {
                    when {
                        isLoading && albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            LoadingStateCard(stringResource(R.string.browse_loading_albums))
                        }

                        error != null && albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorStateCard(error)
                        }

                        albums.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateCard(
                                stringResource(R.string.browse_no_albums),
                                stringResource(R.string.browse_no_albums_body),
                            )
                        }

                        else -> items(albums, key = { it.id }) { album ->
                            AlbumCard(album = album, server = server, onOpenAlbum = onOpenAlbum)
                        }
                    }
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LoadingStateCard(stringResource(R.string.browse_loading_albums))
                        }
                    }
                }
                AlbumFastScrollOverlay(
                    index = fastScrollIndex,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onScrollToAlbumIndex = { itemIndex ->
                        coroutineScope.launch { gridState.scrollToItem(itemIndex) }
                    },
                )
            }
        }

        AlbumViewMode.LIST -> {
            val listState = rememberRestoredLazyListState(scrollPosition.listPosition)
            LaunchedEffect(listState, canLoadMore, hasMore, isLoading, isLoadingMore, albums.size) {
                snapshotFlow {
                    if (!canLoadMore || !hasMore || isLoading || isLoadingMore || albums.isEmpty()) {
                        false
                    } else {
                        val layoutInfo = listState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisibleIndex >= layoutInfo.totalItemsCount - 5
                    }
                }
                    .distinctUntilChanged()
                    .filter { shouldLoad -> shouldLoad }
                    .collect { onLoadMore() }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    when {
                        isLoading && albums.isEmpty() -> item {
                            LoadingStateCard(stringResource(R.string.browse_loading_albums))
                        }

                        error != null && albums.isEmpty() -> item {
                            ErrorStateCard(error)
                        }

                        albums.isEmpty() -> item {
                            EmptyStateCard(
                                stringResource(R.string.browse_no_albums),
                                stringResource(R.string.browse_no_albums_body),
                            )
                        }

                        else -> items(albums, key = { it.id }) { album ->
                            AlbumRow(album = album, server = server, onOpenAlbum = onOpenAlbum)
                        }
                    }
                    if (isLoadingMore) {
                        item {
                            LoadingStateCard(stringResource(R.string.browse_loading_albums))
                        }
                    }
                }
                AlbumFastScrollOverlay(
                    index = fastScrollIndex,
                    bottomOverlayPadding = bottomOverlayPadding,
                    onScrollToAlbumIndex = { itemIndex ->
                        coroutineScope.launch { listState.scrollToItem(itemIndex) }
                    },
                )
            }
        }
    }
}

private fun albumFeedContentPadding(
    bottomOverlayPadding: Dp,
    hasFastScroll: Boolean,
): PaddingValues {
    return PaddingValues(
        end = if (hasFastScroll) AlbumFastScrollContentEndPadding else 0.dp,
        bottom = 24.dp + bottomOverlayPadding,
    )
}

private val AlbumFastScrollContentEndPadding = 24.dp

@Composable
private fun AlbumFastScrollOverlay(
    index: AlbumFastScrollIndex?,
    bottomOverlayPadding: Dp,
    onScrollToAlbumIndex: (Int) -> Unit,
) {
    val labels = index?.labels.orEmpty()
    if (index == null || labels.size <= 1) return

    val haptic = LocalHapticFeedback.current
    var showScrollBar by remember(labels) { mutableStateOf(false) }
    LaunchedEffect(labels) {
        kotlinx.coroutines.delay(500)
        showScrollBar = true
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showScrollBar,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 8.dp + bottomOverlayPadding),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AlphabetScrollBar(
                labels = labels,
                onScrollTo = { labelIndex ->
                    val label = labels.getOrNull(labelIndex) ?: return@AlphabetScrollBar
                    val albumIndex = index.targetAlbumIndices[label] ?: return@AlphabetScrollBar
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScrollToAlbumIndex(albumIndex)
                },
            )
        }
    }
}

private data class AlbumFastScrollIndex(
    val labels: List<String>,
    val targetAlbumIndices: Map<String, Int>,
)

private fun List<AlbumSummary>.albumFastScrollIndex(
    feed: AlbumListType,
    ignoredArticles: String?,
): AlbumFastScrollIndex? {
    if (!feed.supportsAlbumFastScroll() || size < 2) return null

    val articles = ignoredArticles.toIgnoredArticleList()
    val locale = Locale.getDefault()
    val index = AlphabeticIndex<Nothing>(locale)
        .addLabels(Locale.ENGLISH)
        .addLabels(Locale.JAPANESE)
        .addLabels(Locale.CHINESE)
        .addLabels(Locale.KOREAN)
        .setOverflowLabel("#")
        .setUnderflowLabel("#")
        .buildImmutableIndex()
    val firstAlbumIndexByBucket = linkedMapOf<Int, AlbumFastScrollBucket>()
    forEachIndexed { albumIndex, album ->
        if (album.isUnknownAlbumPlaceholder()) return@forEachIndexed
        val sortValue = album.fastScrollSortValue(feed).stripIgnoredArticles(articles)
        val bucketIndex = index.getBucketIndex(sortValue)
        firstAlbumIndexByBucket.putIfAbsent(
            bucketIndex,
            AlbumFastScrollBucket(
                label = index.getBucket(bucketIndex).label,
                albumIndex = albumIndex,
            ),
        )
    }

    val scrollBarMapping = firstAlbumIndexByBucket.albumScrollBarMapping()
    val labels = scrollBarMapping.map { it.first }
    val targets = scrollBarMapping.toMap()
    return if (labels.size > 1) AlbumFastScrollIndex(labels, targets) else null
}

private data class AlbumFastScrollBucket(
    val label: String,
    val albumIndex: Int,
)

private fun Map<Int, AlbumFastScrollBucket>.albumScrollBarMapping(): List<Pair<String, Int>> {
    val result = mutableListOf<Pair<String, Int>>()
    var otherTarget: Int? = null
    entries.sortedBy { it.key }.forEach { (_, bucket) ->
        val label = bucket.label
        val albumIndex = bucket.albumIndex
        when {
            label.length == 1 && label[0] in 'A'..'Z' -> {
                if (result.none { it.first == label }) {
                    result.add(label to albumIndex)
                }
            }
            label == "#" -> {
                if (result.none { it.first == "#" }) {
                    result.add(0, "#" to albumIndex)
                }
            }
            otherTarget == null -> otherTarget = albumIndex
        }
    }
    otherTarget?.let { result.add("…" to it) }
    return result
}

private fun AlbumSummary.fastScrollSortValue(feed: AlbumListType): String {
    return when (feed) {
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

private fun AlbumListType.supportsAlbumFastScroll(): Boolean {
    return this == AlbumListType.ALPHABETICAL_BY_NAME ||
        this == AlbumListType.ALPHABETICAL_BY_ARTIST
}

@Composable
private fun AlbumFeedControls(
    feeds: List<AlbumListType>,
    selectedFeed: AlbumListType,
    viewMode: AlbumViewMode,
    onSelectFeed: (AlbumListType) -> Unit,
    onUpdateViewMode: (AlbumViewMode) -> Unit,
) {
    val nextMode = when (viewMode) {
        AlbumViewMode.GRID -> AlbumViewMode.LIST
        AlbumViewMode.LIST -> AlbumViewMode.GRID
    }
    val contentDescription = when (nextMode) {
        AlbumViewMode.GRID -> stringResource(R.string.browse_show_album_grid)
        AlbumViewMode.LIST -> stringResource(R.string.browse_show_album_list)
    }

    val selectedIndex = feeds.indexOf(selectedFeed).coerceAtLeast(0)
    val lazyRowState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        lazyRowState.animateScrollToItem(selectedIndex)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = lazyRowState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            items(feeds) { feed ->
                AlbumFeedChip(
                    feed = feed,
                    selected = selectedFeed == feed,
                    onClick = { onSelectFeed(feed) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = { onUpdateViewMode(nextMode) }) {
            Icon(
                imageVector = when (nextMode) {
                    AlbumViewMode.GRID -> Icons.Rounded.GridView
                    AlbumViewMode.LIST -> Icons.AutoMirrored.Rounded.ViewList
                },
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun AlbumFeedChip(
    feed: AlbumListType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visuals = SakiTheme.visuals
    val label = stringResource(feed.labelRes())
    if (visuals.browseSectionChipSelectedContainerAlpha <= 0f) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            modifier = modifier,
        )
    } else {
        val chipShape = RoundedCornerShape(visuals.browseSectionChipCornerRadius)
        Surface(
            modifier = modifier
                .clip(chipShape)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                ),
            shape = chipShape,
            color = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = if (selected || visuals.browseSectionChipOutlineAlpha <= 0f) {
                null
            } else {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = visuals.browseSectionChipOutlineAlpha,
                    ),
                )
            },
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlaylistsPage(
    playlists: List<org.hdhmc.saki.domain.model.PlaylistSummary>,
    server: ServerConfig,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    scrollPosition: LazyListScrollPosition,
    onOpenPlaylist: (String) -> Unit,
) {
    if (isLoading && playlists.isEmpty()) {
        LoadingStateCard(stringResource(R.string.browse_loading_playlists))
        return
    }
    if (error != null && playlists.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (playlists.isEmpty()) {
        EmptyStateCard(
            stringResource(R.string.browse_no_playlists),
            stringResource(R.string.browse_no_playlists_body),
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
        )
        return
    }
    val listState = rememberRestoredLazyListState(scrollPosition)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist = playlist, server = server, onOpenPlaylist = onOpenPlaylist)
        }
    }
}

@Composable
private fun SongsPageRoute(
    browsePagerState: PagerState,
    songs: List<Song>,
    songsOffset: Int,
    hasPrevious: Boolean,
    hasMore: Boolean,
    server: ServerConfig,
    playbackUiStateFlow: StateFlow<SakiBrowsePlaybackUiState>,
    availabilityUiStateFlow: StateFlow<SakiBrowseAvailabilityUiState>,
    isOfflineDegraded: Boolean,
    isLoading: Boolean,
    isLoadingPrevious: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    scrollPosition: LazyListScrollPosition,
    onLoadPrevious: () -> Unit,
    onLoadMore: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
    selectedSongFeed: SongFeedType,
    randomSongs: List<Song>,
    isRandomSongsLoading: Boolean,
    randomSongsError: String?,
    onSelectSongFeed: (SongFeedType) -> Unit,
) {
    val playbackUiState by playbackUiStateFlow.collectAsStateWithLifecycle()
    val availabilityUiState by availabilityUiStateFlow.collectAsStateWithLifecycle()
    val cachedSongsBySongId = rememberCachedSongsBySongId(availabilityUiState)

    val feeds = SongFeedType.entries
    val selectedSongFeedState = rememberUpdatedState(selectedSongFeed)
    val feedPagerState = rememberPagerState(
        initialPage = feeds.indexOf(selectedSongFeed).coerceAtLeast(0),
        pageCount = { feeds.size },
    )
    val feedPagerFlingBehavior = PagerDefaults.flingBehavior(
        state = feedPagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    val browsePagerFlingBehavior = PagerDefaults.flingBehavior(
        state = browsePagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )
    val feedBoundaryHandoffConnection = rememberFeedBoundaryHandoffConnection(
        feedPagerState = feedPagerState,
        browsePagerState = browsePagerState,
        browsePagerFlingBehavior = browsePagerFlingBehavior,
    )
    val coroutineScope = rememberCoroutineScope()
    val highlightedFeed = feeds[feedPagerState.targetPage.coerceIn(0, feeds.lastIndex)]

    LaunchedEffect(selectedSongFeed) {
        val targetPage = feeds.indexOf(selectedSongFeed).coerceAtLeast(0)
        if (feedPagerState.settledPage != targetPage && feedPagerState.targetPage != targetPage) {
            feedPagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(feedPagerState) {
        snapshotFlow { feedPagerState.settledPage }
            .distinctUntilChanged()
            .map { feeds[it] }
            .filter { it != selectedSongFeedState.value }
            .collect { onSelectSongFeed(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SongFeedControls(
            selected = highlightedFeed,
            onSelect = { feed ->
                val targetPage = feeds.indexOf(feed)
                if (targetPage >= 0 && feedPagerState.targetPage != targetPage) {
                    coroutineScope.launch { feedPagerState.animateScrollToPage(targetPage) }
                }
            },
        )
        HorizontalPager(
            state = feedPagerState,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(feedBoundaryHandoffConnection),
            flingBehavior = feedPagerFlingBehavior,
        ) { page ->
            when (feeds[page]) {
                SongFeedType.DEFAULT -> DefaultSongsContent(
                    songs = songs,
                    songsOffset = songsOffset,
                    hasPrevious = hasPrevious,
                    hasMore = hasMore,
                    server = server,
                    cachedSongsBySongId = cachedSongsBySongId,
                    streamCachedSongIds = availabilityUiState.streamCachedSongIds,
                    downloadingSongIds = availabilityUiState.downloadingSongIds,
                    isOfflineDegraded = isOfflineDegraded,
                    isLoading = isLoading,
                    isLoadingPrevious = isLoadingPrevious,
                    isLoadingMore = isLoadingMore,
                    error = error,
                    bottomOverlayPadding = bottomOverlayPadding,
                    scrollPosition = scrollPosition,
                    currentPlaybackSongId = playbackUiState.currentPlaybackSongId,
                    isPlaying = playbackUiState.isPlaying,
                    onLoadPrevious = onLoadPrevious,
                    onLoadMore = onLoadMore,
                    onPlaySongs = onPlaySongs,
                    onPlayLibrarySongs = onPlayLibrarySongs,
                    onOfflineSongUnavailable = onOfflineSongUnavailable,
                    onShowSongActions = onShowSongActions,
                )
                SongFeedType.RANDOM -> RandomSongsContent(
                    songs = randomSongs,
                    server = server,
                    cachedSongsBySongId = cachedSongsBySongId,
                    streamCachedSongIds = availabilityUiState.streamCachedSongIds,
                    downloadingSongIds = availabilityUiState.downloadingSongIds,
                    isOfflineDegraded = isOfflineDegraded,
                    isLoading = isRandomSongsLoading,
                    error = randomSongsError,
                    bottomOverlayPadding = bottomOverlayPadding,
                    currentPlaybackSongId = playbackUiState.currentPlaybackSongId,
                    isPlaying = playbackUiState.isPlaying,
                    onPlaySongs = onPlaySongs,
                    onOfflineSongUnavailable = onOfflineSongUnavailable,
                    onShowSongActions = onShowSongActions,
                )
            }
        }
    }
}

@Composable
private fun DefaultSongsContent(
    songs: List<Song>,
    songsOffset: Int,
    hasPrevious: Boolean,
    hasMore: Boolean,
    server: ServerConfig,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    isLoading: Boolean,
    isLoadingPrevious: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    scrollPosition: LazyListScrollPosition,
    currentPlaybackSongId: String? = null,
    isPlaying: Boolean = false,
    onLoadPrevious: () -> Unit,
    onLoadMore: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onPlayLibrarySongs: (Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    val listState = rememberRestoredLazyListState(scrollPosition)
    var wasLoadingPrevious by remember { mutableStateOf(isLoadingPrevious) }
    var previousLoadAnchorIndex by remember { mutableStateOf<Int?>(null) }
    var previousLoadAnchorScrollOffset by remember { mutableStateOf(0) }
    LaunchedEffect(songsOffset, isLoadingPrevious, songs.size) {
        if (!wasLoadingPrevious && isLoadingPrevious) {
            previousLoadAnchorIndex = songsOffset + listState.firstVisibleItemIndex
            previousLoadAnchorScrollOffset = listState.firstVisibleItemScrollOffset
        }

        val finishedPreviousLoad = wasLoadingPrevious && !isLoadingPrevious
        val anchorIndex = previousLoadAnchorIndex
        if (finishedPreviousLoad && anchorIndex != null && songs.isNotEmpty()) {
            val targetIndex = anchorIndex - songsOffset
            if (
                targetIndex in songs.indices &&
                (
                    listState.firstVisibleItemIndex != targetIndex ||
                        listState.firstVisibleItemScrollOffset != previousLoadAnchorScrollOffset
                    )
            ) {
                listState.scrollToItem(
                    index = targetIndex,
                    scrollOffset = previousLoadAnchorScrollOffset,
                )
            }
            previousLoadAnchorIndex = null
        }
        wasLoadingPrevious = isLoadingPrevious
    }
    LaunchedEffect(listState, hasPrevious, isLoading, isLoadingPrevious, isLoadingMore, songsOffset, songs.size) {
        snapshotFlow {
            if (!hasPrevious || isLoading || isLoadingPrevious || isLoadingMore || songs.isEmpty()) {
                false
            } else {
                val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                firstVisibleIndex <= 8
            }
        }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .collect { onLoadPrevious() }
    }
    LaunchedEffect(listState, hasMore, isLoading, isLoadingPrevious, isLoadingMore, songsOffset, songs.size) {
        snapshotFlow {
            if (!hasMore || isLoading || isLoadingPrevious || isLoadingMore || songs.isEmpty()) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex >= layoutInfo.totalItemsCount - 8
            }
        }
            .distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad }
            .collect { onLoadMore() }
    }

    if (isLoading && songs.isEmpty()) {
        LoadingStateCard(stringResource(R.string.browse_loading_songs))
        return
    }
    if (error != null && songs.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (songs.isEmpty()) {
        EmptyStateCard(
            stringResource(R.string.browse_no_songs),
            stringResource(R.string.browse_no_songs_body),
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        itemsIndexed(songs, key = { index, s -> "${songsOffset + index}-${s.id}" }) { index, song ->
            val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
            AlbumTrackRow(
                song = song,
                index = index,
                useTrackNumbers = false,
                albumArtistLabel = null,
                cachedSong = cachedSongsBySongId[song.id],
                isStreamCached = song.id in streamCachedSongIds,
                isDownloading = song.id in downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                isOfflinePlayable = isOfflinePlayable,
                isCurrent = currentPlaybackSongId == song.id,
                isPlaying = isPlaying,
                accentColor = MaterialTheme.colorScheme.primary,
                artworkModel = resolveArtworkModel(server, song.coverArtId, cachedSongsBySongId[song.id]),
                onClick = {
                    if (isOfflineDegraded) {
                        playOfflineAwareSongs(
                            songs = songs,
                            startIndex = index,
                            isOfflineDegraded = true,
                            cachedSongsBySongId = cachedSongsBySongId,
                            streamCachedSongIds = streamCachedSongIds,
                            onPlaySongs = onPlaySongs,
                            onUnavailable = onOfflineSongUnavailable,
                        )
                    } else {
                        onPlayLibrarySongs(index)
                    }
                },
                onMore = { onShowSongActions(song) },
            )
        }
        if (isLoadingMore) {
            item {
                LoadingStateCard(stringResource(R.string.browse_loading_songs))
            }
        }
    }
}

@Composable
private fun SongFeedControls(
    selected: SongFeedType,
    onSelect: (SongFeedType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == SongFeedType.DEFAULT,
            onClick = { onSelect(SongFeedType.DEFAULT) },
            label = { Text(stringResource(R.string.browse_song_feed_default)) },
        )
        FilterChip(
            selected = selected == SongFeedType.RANDOM,
            onClick = { onSelect(SongFeedType.RANDOM) },
            label = { Text(stringResource(R.string.browse_song_feed_random)) },
        )
    }
}

@Composable
private fun RandomSongsContent(
    songs: List<Song>,
    server: ServerConfig,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp,
    currentPlaybackSongId: String? = null,
    isPlaying: Boolean = false,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOfflineSongUnavailable: () -> Unit,
    onShowSongActions: (Song) -> Unit,
) {
    if (isLoading && songs.isEmpty()) {
        LoadingStateCard(stringResource(R.string.browse_loading_songs))
        return
    }
    if (error != null && songs.isEmpty()) {
        ErrorStateCard(error)
        return
    }
    if (songs.isEmpty()) {
        EmptyStateCard(
            stringResource(R.string.browse_no_songs),
            stringResource(R.string.browse_no_songs_body),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        itemsIndexed(songs, key = { index, s -> "random-$index-${s.id}" }) { index, song ->
            AlbumTrackRow(
                song = song,
                index = index,
                useTrackNumbers = false,
                albumArtistLabel = null,
                cachedSong = cachedSongsBySongId[song.id],
                isStreamCached = song.id in streamCachedSongIds,
                isDownloading = song.id in downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds),
                isCurrent = currentPlaybackSongId == song.id,
                isPlaying = isPlaying,
                accentColor = MaterialTheme.colorScheme.primary,
                artworkModel = resolveArtworkModel(server, song.coverArtId, cachedSongsBySongId[song.id]),
                onClick = {
                    playOfflineAwareSongs(
                        songs = songs,
                        startIndex = index,
                        isOfflineDegraded = isOfflineDegraded,
                        cachedSongsBySongId = cachedSongsBySongId,
                        streamCachedSongIds = streamCachedSongIds,
                        onPlaySongs = onPlaySongs,
                        onUnavailable = onOfflineSongUnavailable,
                    )
                },
                onMore = { onShowSongActions(song) },
            )
        }
    }
}

private fun playOfflineAwareSongs(
    songs: List<Song>,
    startIndex: Int,
    isOfflineDegraded: Boolean,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onUnavailable: () -> Unit,
) {
    if (!isOfflineDegraded) {
        onPlaySongs(songs, startIndex)
        return
    }
    val startSong = songs.getOrNull(startIndex)
    if (startSong == null || !startSong.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)) {
        onUnavailable()
        return
    }

    val playableSongs = songs.filter { song -> song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds) }
    val playableStartIndex = songs.take(startIndex).count { song ->
        song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
    }
    if (playableSongs.isEmpty()) {
        onUnavailable()
    } else {
        onPlaySongs(playableSongs, playableStartIndex)
    }
}

@Composable
private fun BrowseSection.localizedLabel(): String = when (this) {
    BrowseSection.ARTISTS -> stringResource(R.string.browse_artists)
    BrowseSection.ALBUMS -> stringResource(R.string.library_albums)
    BrowseSection.PLAYLISTS -> stringResource(R.string.browse_playlists)
    BrowseSection.SONGS -> stringResource(R.string.browse_songs)
}

@Composable
private fun artistCountText(count: Int): String =
    pluralStringResource(R.plurals.browse_artist_count, count, count)

@Composable
private fun matchCountText(count: Int): String =
    pluralStringResource(R.plurals.browse_match_count, count, count)

@Composable
private fun AlphabetScrollBar(
    labels: List<String>,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(labels) {
                detectTapGestures { offset ->
                    val idx = (offset.y / (size.height.toFloat() / labels.size))
                        .toInt()
                        .coerceIn(0, labels.lastIndex)
                    onScrollTo(idx)
                }
            }
            .pointerInput(labels) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val idx = (offset.y / (size.height.toFloat() / labels.size))
                            .toInt()
                            .coerceIn(0, labels.lastIndex)
                        activeIndex = idx
                        onScrollTo(idx)
                    },
                    onDragEnd = { activeIndex = -1 },
                    onDragCancel = { activeIndex = -1 },
                    onVerticalDrag = { change, _ ->
                        val idx = (change.position.y / (size.height.toFloat() / labels.size))
                            .toInt()
                            .coerceIn(0, labels.lastIndex)
                        if (idx != activeIndex) {
                            activeIndex = idx
                            onScrollTo(idx)
                        }
                    },
                )
            }
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fontSize = if (labels.size > 30) 8.sp else 10.sp
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                fontSize = fontSize,
                lineHeight = fontSize,
                color = if (index == activeIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
