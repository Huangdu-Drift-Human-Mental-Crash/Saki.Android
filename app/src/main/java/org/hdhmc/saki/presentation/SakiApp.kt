package org.hdhmc.saki.presentation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import org.hdhmc.saki.domain.model.PlaybackProgressState
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.presentation.library.BrowseScreen
import org.hdhmc.saki.presentation.library.NowPlayingCapsule
import org.hdhmc.saki.presentation.library.NowPlayingOverlay
import org.hdhmc.saki.presentation.serverconfig.ServerConfigRoute
import org.hdhmc.saki.presentation.settings.SettingsScreen
import org.hdhmc.saki.ui.theme.seedColorForKey
import org.hdhmc.saki.ui.theme.isSystemDynamicSeed
import org.hdhmc.saki.ui.theme.SakiAndroidTheme

@Composable
fun SakiApp(
    modifier: Modifier = Modifier,
    viewModel: SakiAppViewModel = viewModel(),
) {
    val rootUiState by viewModel.rootUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showServerManager by rememberSaveable { mutableStateOf(false) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val appDensity = remember(density, rootUiState.textScale) {
        Density(
            density = density.density,
            fontScale = density.fontScale * rootUiState.textScale.multiplier,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text.asString(context),
                actionLabel = msg.action?.let { context.getString(it.labelRes) },
                duration = msg.duration,
            )
            if (result == SnackbarResult.ActionPerformed && msg.action == SnackbarAction.RESTART) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openNowPlayingRequests.collectLatest {
            showNowPlaying = true
            viewModel.refreshEndpointStatus()
        }
    }

    LaunchedEffect(showSettings) {
        if (showSettings) {
            viewModel.refreshSettingsCacheStorageSummary()
        }
    }

    SakiAndroidTheme(
        seedColor = seedColorForKey(rootUiState.appPreferences.themeSeedKey),
        paletteStyle = rootUiState.appPreferences.paletteStyle,
        useSystemColor = isSystemDynamicSeed(rootUiState.appPreferences.themeSeedKey),
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
        Box(modifier = modifier.fillMaxSize()) {
            when {
                !rootUiState.isAppReady -> {
                    Surface(modifier = Modifier.fillMaxSize()) {}
                }

                else -> {
                    RootShell(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        showSettings = showSettings,
                        showNowPlaying = showNowPlaying,
                        onShowSettingsChange = { showSettings = it },
                        onManageServers = { showServerManager = true },
                        onOpenNowPlaying = { showNowPlaying = true },
                    )
                    NowPlayingOverlayHostRoute(
                        visible = showNowPlaying,
                        viewModel = viewModel,
                        onDismiss = { showNowPlaying = false },
                        onCloseSettings = { showSettings = false },
                    )
                }
            }

            if (showServerManager) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ServerConfigRoute(
                        modifier = Modifier.fillMaxSize().pageEnterMotion(),
                        onCloseManager = { showServerManager = false },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RootShell(
    viewModel: SakiAppViewModel,
    snackbarHostState: SnackbarHostState,
    showSettings: Boolean,
    showNowPlaying: Boolean,
    onShowSettingsChange: (Boolean) -> Unit,
    onManageServers: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val shellBackgroundBrush = rememberBrowseBackgroundBrush()
    val density = LocalDensity.current
    val defaultCapsuleHeightPx = with(density) { 72.dp.roundToPx() }
    var capsuleHeightPx by remember { mutableIntStateOf(defaultCapsuleHeightPx) }
    val capsuleOverlayPadding = with(density) { capsuleHeightPx.toDp() }
    val contentScrolling = remember { mutableStateOf(false) }
    val fastScrollActive = remember { mutableStateOf(false) }
    val onFastScrollActiveChange = remember { { active: Boolean -> fastScrollActive.value = active } }
    val scrollScope = rememberCoroutineScope()
    val contentScrollConnection = remember(scrollScope) {
        object : NestedScrollConnection {
            var idleWatcher: Job? = null
            var lastVerticalScrollNanos: Long = 0L

            fun recordVerticalScroll(offset: Offset) {
                if (offset.y == 0f) return

                lastVerticalScrollNanos = System.nanoTime()
                contentScrolling.value = true
                if (idleWatcher?.isActive == true) return
                idleWatcher = scrollScope.launch {
                    while (true) {
                        delay(MINI_PLAYER_SCROLL_IDLE_DELAY_MS)
                        val idleMillis = (System.nanoTime() - lastVerticalScrollNanos) / 1_000_000L
                        if (idleMillis >= MINI_PLAYER_SCROLL_IDLE_DELAY_MS) {
                            contentScrolling.value = false
                            idleWatcher = null
                            break
                        }
                    }
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                recordVerticalScroll(if (consumed.y != 0f) consumed else available)
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                idleWatcher?.cancel()
                idleWatcher = null
                contentScrolling.value = false
                return Velocity.Zero
            }
        }
    }

    val settingsBackMotion = rememberPredictiveBackMotion(
        enabled = showSettings && !showNowPlaying,
        onBack = { onShowSettingsChange(false) },
        targetAlpha = 0.35f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shellBackgroundBrush)
            .nestedScroll(contentScrollConnection),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            LocalFastScrollActiveChange provides onFastScrollActiveChange,
        ) {
            Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                BrowseRoute(
                    viewModel = viewModel,
                    contentPadding = PaddingValues(),
                    bottomOverlayPadding = capsuleOverlayPadding,
                    backHandlersEnabled = !showSettings && !showNowPlaying,
                    onManageServers = onManageServers,
                    onOpenSettings = { onShowSettingsChange(true) },
                )

                if (showSettings) {
                    Box(modifier = Modifier.fillMaxSize().pageEnterMotion().then(settingsBackMotion.modifier)) {
                        SettingsRoute(
                            viewModel = viewModel,
                            contentPadding = PaddingValues(),
                            bottomOverlayPadding = capsuleOverlayPadding,
                            onClose = { settingsBackMotion.dismiss() },
                            onManageServers = onManageServers,
                        )
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = capsuleOverlayPadding),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coordinates ->
                            val measuredHeight = coordinates.size.height
                            if (capsuleHeightPx != measuredHeight) {
                                capsuleHeightPx = measuredHeight
                            }
                        },
                ) {
                    NowPlayingCapsuleRoute(
                        viewModel = viewModel,
                        onOpenNowPlaying = onOpenNowPlaying,
                        isContentScrolling = contentScrolling.value,
                        isFastScrolling = fastScrollActive.value,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseRoute(
    viewModel: SakiAppViewModel,
    contentPadding: PaddingValues,
    bottomOverlayPadding: Dp,
    backHandlersEnabled: Boolean,
    onManageServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.browseUiState.collectAsStateWithLifecycle()
    val endpointStatus by viewModel.endpointStatus.collectAsStateWithLifecycle()
    BrowseScreen(
        uiState = uiState,
        playbackUiStateFlow = viewModel.browsePlaybackUiState,
        availabilityUiStateFlow = viewModel.browseAvailabilityUiState,
        isOfflineDegraded = endpointStatus.isOfflineDegraded,
        contentPadding = contentPadding,
        bottomOverlayPadding = bottomOverlayPadding,
        backHandlersEnabled = backHandlersEnabled,
        onManageServers = onManageServers,
        onSelectBrowseSection = viewModel::selectBrowseSection,
        onSetSearchActive = viewModel::setSearchActive,
        onUpdateSearchQuery = viewModel::updateSearchQuery,
        onRemoveRecentSearchQuery = viewModel::removeRecentSearchQuery,
        onClearRecentSearchQueries = viewModel::clearRecentSearchQueries,
        onRefreshCurrentTab = viewModel::refreshCurrentTab,
        onSelectAlbumFeed = viewModel::selectAlbumFeed,
        onSelectSongFeed = viewModel::selectSongFeed,
        onLoadMoreAlbums = viewModel::loadMoreAlbums,
        onLoadPreviousSongs = viewModel::loadPreviousSongs,
        onLoadMoreSongs = viewModel::loadMoreSongs,
        onUpdateAlbumViewMode = viewModel::updateAlbumViewMode,
        onOpenArtist = viewModel::openArtist,
        onOpenAlbum = viewModel::openAlbum,
        onOpenPlaylist = viewModel::openPlaylist,
        onPopDetail = viewModel::popBrowseRoute,
        onPlaySongs = viewModel::playSongs,
        onPlayLibrarySongs = viewModel::playLibrarySongs,
        onQueueSong = viewModel::queueSong,
        onPlaySongNext = viewModel::playSongNext,
        onOfflineSongUnavailable = viewModel::showOfflineSongUnavailable,
        onToggleSongDownload = viewModel::toggleSongDownload,
        onEstimateCollectionStreamCache = viewModel::estimateCollectionStreamCache,
        onStartCollectionStreamCache = viewModel::startCollectionStreamCache,
        onCancelCollectionStreamCache = viewModel::cancelCollectionStreamCache,
        onOpenSettings = onOpenSettings,
        onImportConfig = { uri -> viewModel.importConfig(uri) },
    )
}

@Composable
private fun SettingsRoute(
    viewModel: SakiAppViewModel,
    contentPadding: PaddingValues,
    bottomOverlayPadding: Dp,
    onClose: () -> Unit,
    onManageServers: () -> Unit,
) {
    val uiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        bottomOverlayPadding = bottomOverlayPadding,
        onClose = onClose,
        onManageServers = onManageServers,
        onSelectServer = viewModel::selectServer,
        onUpdateStreamQuality = viewModel::updateStreamQuality,
        onUpdateDownloadQuality = viewModel::updateDownloadQuality,
        onUpdateAdaptiveQuality = viewModel::updateAdaptiveQuality,
        onUpdateWifiStreamQuality = viewModel::updateWifiStreamQuality,
        onUpdateMobileStreamQuality = viewModel::updateMobileStreamQuality,
        onUpdateSoundBalancing = viewModel::updateSoundBalancing,
        onUpdateStreamCacheSizeMb = viewModel::updateStreamCacheSizeMb,
        onClearStreamCache = viewModel::clearStreamCache,
        onUpdateImageCacheSizeMb = viewModel::updateImageCacheSizeMb,
        onClearImageCache = viewModel::clearImageCache,
        onUpdateSongMetadata = viewModel::updateAllSongMetadata,
        onUpdateHideMergedArtists = viewModel::updateHideMergedArtists,
        onUpdateTextScale = viewModel::updateTextScale,
        onUpdateLanguage = viewModel::updateLanguage,
        onUpdateThemeMode = viewModel::updateThemeMode,
        onUpdateThemeSeed = viewModel::updateThemeSeed,
        onUpdatePaletteStyle = viewModel::updatePaletteStyle,
        onUpdateDefaultBrowseTab = viewModel::updateDefaultBrowseTab,
        onUpdateDefaultAlbumFeed = viewModel::updateDefaultAlbumFeed,
        onUpdateSongsPageSize = viewModel::updateSongsPageSize,
        onUpdateBluetoothLyrics = viewModel::updateBluetoothLyrics,
        onUpdateBufferStrategy = viewModel::updateBufferStrategy,
        onUpdateCustomBufferSeconds = viewModel::updateCustomBufferSeconds,
        onExportConfig = viewModel::exportConfig,
        onImportConfig = { uri -> viewModel.importConfig(uri) },
        onPlayCachedSong = viewModel::playCachedSong,
        onPlayCachedQueue = viewModel::playCachedQueue,
        onDeleteCachedSong = viewModel::deleteCachedSong,
        onClearCachedSongs = viewModel::clearCachedSongs,
    )
}

@Composable
private fun NowPlayingCapsuleRoute(
    viewModel: SakiAppViewModel,
    onOpenNowPlaying: () -> Unit,
    isContentScrolling: Boolean,
    isFastScrolling: Boolean,
) {
    val uiState by viewModel.capsuleUiState.collectAsStateWithLifecycle()
    NowPlayingCapsule(
        track = uiState.track,
        isPlaying = uiState.isPlaying,
        currentServer = uiState.currentServer,
        onExpand = {
            if (uiState.track != null) onOpenNowPlaying()
        },
        onPlayPause = {
            if (uiState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
        },
        onSkipToPrevious = viewModel::skipToPrevious,
        onSkipToNext = viewModel::skipToNext,
        prewarmDynamicColors = rememberVisualEffectsPolicy().useNowPlayingDynamicArtworkColors,
        isContentScrolling = isContentScrolling,
        isFastScrolling = isFastScrolling,
    )
}

private const val MINI_PLAYER_SCROLL_IDLE_DELAY_MS = 220L

@Composable
private fun NowPlayingOverlayHostRoute(
    visible: Boolean,
    viewModel: SakiAppViewModel,
    onDismiss: () -> Unit,
    onCloseSettings: () -> Unit,
) {
    val uiState by viewModel.nowPlayingUiState.collectAsStateWithLifecycle()
    val endpointStatus by viewModel.endpointStatus.collectAsStateWithLifecycle()
    NowPlayingOverlayHost(
        visible = visible,
        playbackState = uiState.playbackState,
        playbackProgressFlow = viewModel.playbackProgress,
        servers = uiState.servers,
        selectedServerId = uiState.selectedServerId,
        libraryIndexes = uiState.libraryIndexes,
        endpointStatus = endpointStatus,
        lyrics = uiState.currentLyrics,
        onDismiss = onDismiss,
        onCloseSettings = onCloseSettings,
        onOpenArtistFromPlayback = viewModel::openArtistFromPlayback,
        onOpenAlbumFromPlayback = viewModel::openAlbumFromPlayback,
        onPausePlayback = viewModel::pausePlayback,
        onResumePlayback = viewModel::resumePlayback,
        onSkipToNext = viewModel::skipToNext,
        onSkipToPrevious = viewModel::skipToPrevious,
        onSeekTo = viewModel::seekTo,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        onToggleShuffle = viewModel::toggleShuffle,
        onSkipToQueueItem = viewModel::skipToQueueItem,
        onRemoveQueueItem = viewModel::removeQueueItem,
        onReprobeEndpoints = viewModel::reprobeEndpoints,
        onForceEndpoint = viewModel::forceEndpoint,
    )
}

/**
 * Isolates [playbackProgressFlow] collection so progress ticks only recompose
 * the overlay subtree, not [RootShell].
 */
@Composable
private fun NowPlayingOverlayHost(
    visible: Boolean,
    playbackState: PlaybackSessionState,
    playbackProgressFlow: StateFlow<PlaybackProgressState>,
    servers: List<ServerConfig>,
    selectedServerId: Long?,
    libraryIndexes: org.hdhmc.saki.domain.model.LibraryIndexes?,
    endpointStatus: EndpointStatus,
    lyrics: SongLyrics?,
    onDismiss: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenArtistFromPlayback: (Long?, String?) -> Unit,
    onOpenAlbumFromPlayback: (Long?, String?) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onReprobeEndpoints: () -> Unit,
    onForceEndpoint: (Long) -> Unit,
) {
    val hasValidQueuedTrack = playbackState.currentIndex in playbackState.queue.indices
    val activeTrack = playbackState.currentItem
        ?: playbackState.queue.getOrNull(playbackState.currentIndex)
    var stableTrack by remember { mutableStateOf(activeTrack) }
    LaunchedEffect(visible, activeTrack, hasValidQueuedTrack) {
        if (activeTrack != null) {
            stableTrack = activeTrack
        } else if (!visible || !hasValidQueuedTrack) {
            stableTrack = null
        }
    }
    val track = activeTrack ?: stableTrack ?: return
    val visualEffectsPolicy = rememberVisualEffectsPolicy()
    val availableArtistIds = remember(libraryIndexes) {
        libraryIndexes
            ?.let { indexes ->
                (
                    indexes.shortcuts.map { it.id } +
                        indexes.sections.flatMap { section -> section.artists.map { artist -> artist.id } }
                    ).toSet()
            }
            .orEmpty()
    }
    fun canOpenArtist(artistId: String?): Boolean {
        return artistId != null && (
            libraryIndexes == null ||
                track.serverId == null ||
                track.serverId != selectedServerId ||
                artistId in availableArtistIds
            )
    }
    NowPlayingOverlay(
        visible = visible,
        playbackState = playbackState,
        playbackProgressFlow = playbackProgressFlow,
        track = track,
        onDismiss = onDismiss,
        canOpenArtist = ::canOpenArtist,
        onOpenArtist = { artistId ->
            onCloseSettings()
            onOpenArtistFromPlayback(track.serverId, artistId)
            onDismiss()
        },
        onOpenAlbum = {
            onCloseSettings()
            onOpenAlbumFromPlayback(track.serverId, track.albumId)
            onDismiss()
        },
        onPlayPause = {
            if (playbackState.isPlaying) onPausePlayback() else onResumePlayback()
        },
        onSkipToNext = onSkipToNext,
        onSkipToPrevious = onSkipToPrevious,
        onSeekTo = onSeekTo,
        onCycleRepeatMode = onCycleRepeatMode,
        onToggleShuffle = onToggleShuffle,
        onSkipToQueueItem = onSkipToQueueItem,
        onRemoveQueueItem = onRemoveQueueItem,
        currentServer = servers.firstOrNull { it.id == track.serverId },
        servers = servers,
        activeEndpointLabel = endpointStatus.activeEndpointLabel,
        activeEndpointId = endpointStatus.activeEndpointId,
        isEndpointForced = endpointStatus.isForced,
        endpointProbeResults = endpointStatus.probeResults,
        isProbing = endpointStatus.isProbing,
        onReprobeEndpoints = onReprobeEndpoints,
        onForceEndpoint = onForceEndpoint,
        lyrics = lyrics,
        useDynamicArtworkColors = visualEffectsPolicy.useNowPlayingDynamicArtworkColors,
        useGradientBackground = visualEffectsPolicy.useNowPlayingGradientBackground,
        useArtworkMotion = visualEffectsPolicy.useNowPlayingArtworkMotion,
        useArtworkBackdrop = visualEffectsPolicy.useNowPlayingArtworkBackdrop,
        artworkPrewarmRadius = visualEffectsPolicy.nowPlayingArtworkPrewarmRadius,
    )
}
