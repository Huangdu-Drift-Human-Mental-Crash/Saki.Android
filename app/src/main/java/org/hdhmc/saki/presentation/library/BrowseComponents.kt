package org.hdhmc.saki.presentation.library

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.hdhmc.saki.R
import org.hdhmc.saki.presentation.bottomContentPadding
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistRef
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.visibleDetailAlbums
import org.hdhmc.saki.ui.theme.sakiCardContainerColor
import org.hdhmc.saki.ui.theme.sakiSubtleCardContainerColor
import org.hdhmc.saki.ui.theme.sakiTonalContainerColor
import org.hdhmc.saki.ui.theme.SakiTheme

private val LibraryDetailWideHeroMinWidth = 720.dp
private val LibraryDetailWideHeroArtworkWidth = 320.dp
private val LibraryDetailWideHeroCompactArtworkWidth = 220.dp
private val LibraryDetailWideHeroCompactMaxHeight = 520.dp
private val LibraryDetailTwoPaneMinWidth = 720.dp
private val LibraryDetailTwoPaneMinHeight = 320.dp
private val LibraryDetailTwoPaneInfoMinWidth = 264.dp
private val LibraryDetailTwoPaneInfoMaxWidth = 320.dp
private val LibraryDetailTwoPaneContentMaxWidth = 640.dp
private val LibraryDetailTwoPaneGap = 20.dp
private val ArtistDetailAlbumGridMinCellWidth = 128.dp
private val LibraryDetailHeroOuterVerticalPadding = 26.dp

internal data class LibraryDetailTwoPaneMetrics(
    val infoPaneWidth: Dp,
    val contentPaneWidth: Dp,
) {
    val stageWidth: Dp
        get() = infoPaneWidth + LibraryDetailTwoPaneGap + contentPaneWidth
}

internal fun supportsLibraryDetailTwoPane(width: Dp, height: Dp): Boolean =
    width > height &&
        width >= LibraryDetailTwoPaneMinWidth &&
        height >= LibraryDetailTwoPaneMinHeight

internal fun calculateLibraryDetailTwoPaneMetrics(width: Dp): LibraryDetailTwoPaneMetrics {
    val infoPaneWidth = (width * 0.34f).coerceIn(
        LibraryDetailTwoPaneInfoMinWidth,
        LibraryDetailTwoPaneInfoMaxWidth,
    )
    val contentPaneWidth = minOf(
        LibraryDetailTwoPaneContentMaxWidth,
        (width - infoPaneWidth - LibraryDetailTwoPaneGap).coerceAtLeast(0.dp),
    )
    return LibraryDetailTwoPaneMetrics(
        infoPaneWidth = infoPaneWidth,
        contentPaneWidth = contentPaneWidth,
    )
}

internal fun calculateLibraryDetailHeroWidth(paneWidth: Dp, usableHeight: Dp): Dp = minOf(
    paneWidth,
    (usableHeight - LibraryDetailHeroOuterVerticalPadding).coerceAtLeast(0.dp),
)

internal fun shouldUseCompactArtistDetailHeader(availableHeight: Dp): Boolean =
    availableHeight < LibraryDetailWideHeroCompactMaxHeight

@Composable
private fun AdaptiveLibraryDetailLayout(
    modifier: Modifier = Modifier,
    singlePane: @Composable () -> Unit,
    infoPane: @Composable (Modifier) -> Unit,
    contentPane: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        if (!supportsLibraryDetailTwoPane(maxWidth, maxHeight)) {
            singlePane()
            return@BoxWithConstraints
        }

        val metrics = calculateLibraryDetailTwoPaneMetrics(maxWidth)
        Row(
            modifier = Modifier
                .width(metrics.stageWidth)
                .fillMaxHeight()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(LibraryDetailTwoPaneGap),
        ) {
            infoPane(
                Modifier
                    .width(metrics.infoPaneWidth)
                    .fillMaxHeight(),
            )
            contentPane(
                Modifier
                    .width(metrics.contentPaneWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun LibraryDetailCenteredInfoPane(
    modifier: Modifier,
    bottomOverlayPadding: Dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.padding(bottom = 24.dp + bottomOverlayPadding),
        contentAlignment = Alignment.Center,
    ) {
        val heroWidth = calculateLibraryDetailHeroWidth(
            paneWidth = maxWidth,
            usableHeight = maxHeight,
        )
        Box(modifier = Modifier.width(heroWidth)) {
            content()
        }
    }
}

@Composable
fun ArtistDetailScreen(
    server: ServerConfig,
    artist: Artist,
    songs: List<Song>,
    songsAreTopSongs: Boolean,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp = 0.dp,
    isOfflineDegraded: Boolean = false,
    onOpenAlbum: (String) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
    currentPlaybackSongId: String? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit = {},
) {
    val visibleAlbums = artist.visibleDetailAlbums()
    val albumCount = when {
        artist.albumCount != null -> visibleAlbums.size
        visibleAlbums.isNotEmpty() -> visibleAlbums.size
        else -> null
    }
    if (!SakiTheme.visuals.useExpressiveSurfaceContainers) {
        LibraryDetailScaffold(
            title = artist.name,
            subtitle = if (albumCount != null) albumCountText(albumCount) else null,
            artwork = null,
            bottomOverlayPadding = bottomOverlayPadding,
        ) {
            when {
                isLoading && songs.isEmpty() -> item { LoadingStateCard(stringResource(R.string.library_loading_artist)) }
                error != null && songs.isEmpty() -> item { ErrorStateCard(error) }
                else -> {
                    if (songs.isNotEmpty()) {
                        item {
                            SectionTitle(
                                stringResource(R.string.library_artist_songs),
                                stringResource(R.string.library_artist_songs_subtitle, artist.name),
                            )
                        }
                        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                            val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
                            SongRow(
                                song = song,
                                server = server,
                                cachedSong = cachedSongsBySongId[song.id],
                                isStreamCached = song.id in streamCachedSongIds,
                                isDownloading = song.id in downloadingSongIds,
                                isOfflineDegraded = isOfflineDegraded,
                                isOfflinePlayable = isOfflinePlayable,
                                onClick = { onPlaySongs(songs, index) },
                                onMore = { onShowActions(song) },
                            )
                        }
                    }
                    if (visibleAlbums.isNotEmpty()) {
                        item {
                            SectionTitle(
                                stringResource(R.string.library_albums),
                                stringResource(R.string.library_albums_subtitle_full_release),
                            )
                        }
                        item {
                            LazyRow {
                                items(visibleAlbums, key = { it.id }) { album ->
                                    AlbumMiniCard(album = album, server = server, onOpenAlbum = onOpenAlbum)
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val heroArtwork = visibleAlbums.firstOrNull()?.let { resolveArtworkModel(server, it.coverArtId, null) }
        ?: songs.firstOrNull()?.let { resolveArtworkModel(server, it.coverArtId, cachedSongsBySongId[it.id]) }
    val accent = animateColorAsState(
        rememberArtworkAccentColor(
            heroArtwork,
            fallback = MaterialTheme.colorScheme.secondaryContainer,
            harmonizeTarget = MaterialTheme.colorScheme.primary,
        ),
        label = "artistAccent",
    ).value
    val trackAccent = accent.ensureContrast(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary,
    )
    val artistMetaItems = listOfNotNull(
        albumCount?.let { albumCountText(it) },
        songs.size.takeIf { it > 0 }?.let { songCountText(it) },
    )
    AdaptiveLibraryDetailLayout(
        modifier = Modifier.fillMaxSize(),
        singlePane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
                item {
                    ArtistDetailHeader(
                        title = artist.name,
                        artwork = heroArtwork,
                        accentColor = accent,
                        metaItems = artistMetaItems,
                        canPlay = songs.isNotEmpty(),
                        onPlay = { if (songs.isNotEmpty()) onPlaySongs(songs, 0) },
                        onBack = onBack,
                    )
                }
                when {
                    isLoading && songs.isEmpty() -> item {
                        LoadingStateCard(stringResource(R.string.library_loading_artist))
                    }
                    error != null && songs.isEmpty() -> item { ErrorStateCard(error) }
                    else -> {
                        if (songs.isNotEmpty()) {
                            item {
                                AlbumTrackListCard(
                                    songs = songs,
                                    cachedSongsBySongId = cachedSongsBySongId,
                                    streamCachedSongIds = streamCachedSongIds,
                                    downloadingSongIds = downloadingSongIds,
                                    isOfflineDegraded = isOfflineDegraded,
                                    currentPlaybackSongId = currentPlaybackSongId,
                                    isPlaying = isPlaying,
                                    accentColor = trackAccent,
                                    albumArtistLabel = artist.name,
                                    collapsedCount = 5,
                                    useSequentialNumbers = true,
                                    onPlaySongs = onPlaySongs,
                                    onShowActions = onShowActions,
                                )
                            }
                        }
                        if (visibleAlbums.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    stringResource(R.string.library_albums),
                                    stringResource(R.string.library_albums_subtitle_full_release),
                                )
                            }
                            item {
                                LazyRow {
                                    items(visibleAlbums, key = { it.id }) { album ->
                                        AlbumMiniCard(
                                            album = album,
                                            server = server,
                                            onOpenAlbum = onOpenAlbum,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        infoPane = { infoModifier ->
            BoxWithConstraints(modifier = infoModifier) {
                val useCompactHeader = shouldUseCompactArtistDetailHeader(maxHeight)
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(ArtistDetailAlbumGridMinCellWidth),
                    contentPadding = bottomContentPadding(bottomOverlayPadding),
                ) {
                    item(
                        key = "artist-detail-header",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        ArtistDetailHeader(
                            title = artist.name,
                            artwork = heroArtwork,
                            accentColor = accent,
                            metaItems = artistMetaItems,
                            canPlay = songs.isNotEmpty(),
                            onPlay = { if (songs.isNotEmpty()) onPlaySongs(songs, 0) },
                            onBack = onBack,
                            useContainer = true,
                            compact = useCompactHeader,
                        )
                    }
                    if (visibleAlbums.isNotEmpty()) {
                        item(
                            key = "artist-detail-albums-heading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            SectionTitle(
                                stringResource(R.string.library_albums),
                                stringResource(R.string.library_albums_subtitle_full_release),
                            )
                        }
                        items(visibleAlbums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                server = server,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }
                }
            }
        },
        contentPane = { contentModifier ->
            LibraryDetailTrackPane(
                modifier = contentModifier,
                server = server,
                songs = songs,
                title = stringResource(R.string.library_artist_songs),
                subtitle = stringResource(R.string.library_artist_songs_subtitle, artist.name),
                loadingLabel = stringResource(R.string.library_loading_artist),
                isLoading = isLoading,
                error = error,
                cachedSongsBySongId = cachedSongsBySongId,
                streamCachedSongIds = streamCachedSongIds,
                downloadingSongIds = downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                currentPlaybackSongId = currentPlaybackSongId,
                isPlaying = isPlaying,
                accentColor = trackAccent,
                albumArtistLabel = artist.name,
                useSequentialNumbers = true,
                showArtwork = true,
                showAlbumLabel = true,
                bottomOverlayPadding = bottomOverlayPadding,
                onPlaySongs = onPlaySongs,
                onShowActions = onShowActions,
            )
        },
    )
}

@Composable
private fun ArtistDetailHeader(
    title: String,
    artwork: Any?,
    accentColor: Color,
    metaItems: List<String>,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    useContainer: Boolean = false,
    compact: Boolean = false,
) {
    if (useContainer) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            ArtistDetailHeaderContent(
                title = title,
                artwork = artwork,
                accentColor = accentColor,
                metaItems = metaItems,
                canPlay = canPlay,
                onPlay = onPlay,
                onBack = onBack,
                compact = compact,
                modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            )
        }
    } else {
        ArtistDetailHeaderContent(
            title = title,
            artwork = artwork,
            accentColor = accentColor,
            metaItems = metaItems,
            canPlay = canPlay,
            onPlay = onPlay,
            onBack = onBack,
            compact = false,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun ArtistDetailHeaderContent(
    title: String,
    artwork: Any?,
    accentColor: Color,
    metaItems: List<String>,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val onAccent = if (accentColor.contrastRatio(Color.White) >= accentColor.contrastRatio(Color.Black)) {
        Color.White
    } else {
        Color.Black
    }
    val metaLine = metaItems.joinToString(" • ")
    if (compact) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    ArtworkCard(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadiusDp = 24,
                        requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
                    )
                } else {
                    Text(
                        text = title.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = onAccent,
                    )
                }
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .minimumInteractiveComponentSize()
                        .size(40.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.32f),
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.library_back),
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = metaLine,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FilledIconButton(
                        onClick = onPlay,
                        enabled = canPlay,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.library_play),
                        )
                    }
                }
            }
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.library_back),
                modifier = Modifier.padding(9.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    ArtworkCard(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadiusDp = 24,
                        requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
                    )
                } else {
                    Text(
                        text = title.trim().firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = onAccent,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onPlay,
                    enabled = canPlay,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.library_play))
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    server: ServerConfig,
    album: Album,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp = 0.dp,
    isOfflineDegraded: Boolean = false,
    onOfflineSongUnavailable: () -> Unit = {},
    currentPlaybackSongId: String? = null,
    isPlaying: Boolean = false,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
    onBack: () -> Unit = {},
) {
    val songCount = album.songCount
    val artistYearSongCount = listOfNotNull(
        album.artistDisplayLabel(),
        album.year?.toString(),
        if (songCount != null) songCountText(songCount) else null,
    )
    val subtitle = artistYearSongCount.joinToString(" • ")
    val playAlbum: () -> Unit = {
        if (album.songs.isNotEmpty()) {
            if (isOfflineDegraded) {
                val startIndex = album.songs.firstOfflinePlayableIndexOrNull(
                    cachedSongsBySongId,
                    streamCachedSongIds,
                )
                if (startIndex != null) {
                    onPlaySongs(album.songs, startIndex)
                } else {
                    onOfflineSongUnavailable()
                }
            } else {
                onPlaySongs(album.songs, 0)
            }
        }
    }

    if (!SakiTheme.visuals.useExpressiveSurfaceContainers) {
        LibraryDetailScaffold(
            title = album.name,
            subtitle = subtitle,
            artwork = resolveArtworkModel(server, album.coverArtId, null),
            bottomOverlayPadding = bottomOverlayPadding,
        ) {
            when {
                isLoading && album.songs.isEmpty() -> item { LoadingStateCard(stringResource(R.string.library_loading_album)) }
                error != null && album.songs.isEmpty() -> item { ErrorStateCard(error) }
                else -> {
                    item {
                        SectionTitle(
                            title = stringResource(R.string.library_track_list),
                            subtitle = album.genre ?: stringResource(R.string.library_album_details),
                            actionLabel = stringResource(R.string.library_play_album),
                            onAction = playAlbum,
                        )
                    }
                    itemsIndexed(album.songs, key = { _, s -> s.id }) { index, song ->
                        val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
                        SongRow(
                            song = song,
                            server = server,
                            cachedSong = cachedSongsBySongId[song.id],
                            isStreamCached = song.id in streamCachedSongIds,
                            isDownloading = song.id in downloadingSongIds,
                            isOfflineDegraded = isOfflineDegraded,
                            isOfflinePlayable = isOfflinePlayable,
                            onClick = { onPlaySongs(album.songs, index) },
                            onMore = { onShowActions(song) },
                        )
                    }
                }
            }
        }
        return
    }

    val artworkModel = resolveArtworkModel(server, album.coverArtId, null)
    val albumAccent = rememberArtworkAccentColor(
        artworkModel,
        fallback = MaterialTheme.colorScheme.primary,
        harmonizeTarget = MaterialTheme.colorScheme.primary,
    )
    val trackAccent = albumAccent.ensureContrast(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary,
    )
    AdaptiveLibraryDetailLayout(
        modifier = Modifier.fillMaxSize(),
        singlePane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
                item {
                    AlbumDetailHeroCard(
                        title = album.name,
                        artwork = artworkModel,
                        accentColor = albumAccent,
                        metaItems = artistYearSongCount,
                        canPlay = album.songs.isNotEmpty(),
                        onPlay = playAlbum,
                        onBack = onBack,
                        playContentDescription = stringResource(R.string.library_play_album),
                    )
                }

                when {
                    isLoading && album.songs.isEmpty() -> item {
                        LoadingStateCard(stringResource(R.string.library_loading_album))
                    }
                    error != null && album.songs.isEmpty() -> item { ErrorStateCard(error) }
                    else -> item {
                        AlbumTrackListCard(
                            songs = album.songs,
                            cachedSongsBySongId = cachedSongsBySongId,
                            streamCachedSongIds = streamCachedSongIds,
                            downloadingSongIds = downloadingSongIds,
                            isOfflineDegraded = isOfflineDegraded,
                            currentPlaybackSongId = currentPlaybackSongId,
                            isPlaying = isPlaying,
                            accentColor = trackAccent,
                            albumArtistLabel = album.artistDisplayLabel(),
                            onPlaySongs = onPlaySongs,
                            onShowActions = onShowActions,
                        )
                    }
                }
            }
        },
        infoPane = { infoModifier ->
            LibraryDetailCenteredInfoPane(
                modifier = infoModifier,
                bottomOverlayPadding = bottomOverlayPadding,
            ) {
                AlbumDetailHeroCard(
                    title = album.name,
                    artwork = artworkModel,
                    accentColor = albumAccent,
                    metaItems = artistYearSongCount,
                    canPlay = album.songs.isNotEmpty(),
                    onPlay = playAlbum,
                    onBack = onBack,
                    playContentDescription = stringResource(R.string.library_play_album),
                )
            }
        },
        contentPane = { contentModifier ->
            LibraryDetailTrackPane(
                modifier = contentModifier,
                server = server,
                songs = album.songs,
                title = stringResource(R.string.library_track_list),
                subtitle = album.genre ?: stringResource(R.string.library_album_details),
                loadingLabel = stringResource(R.string.library_loading_album),
                isLoading = isLoading,
                error = error,
                cachedSongsBySongId = cachedSongsBySongId,
                streamCachedSongIds = streamCachedSongIds,
                downloadingSongIds = downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                currentPlaybackSongId = currentPlaybackSongId,
                isPlaying = isPlaying,
                accentColor = trackAccent,
                albumArtistLabel = album.artistDisplayLabel(),
                useSequentialNumbers = false,
                showArtwork = false,
                bottomOverlayPadding = bottomOverlayPadding,
                onPlaySongs = onPlaySongs,
                onShowActions = onShowActions,
            )
        },
    )
}

@Composable
private fun AlbumDetailHeroCard(
    title: String,
    artwork: Any?,
    metaItems: List<String>,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    accentColor: Color,
    playContentDescription: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useCompactLandscapeHero = maxWidth > maxHeight &&
            maxHeight < LibraryDetailWideHeroCompactMaxHeight
        val wideHeroArtworkWidth = if (useCompactLandscapeHero) {
            LibraryDetailWideHeroCompactArtworkWidth
        } else {
            LibraryDetailWideHeroArtworkWidth
        }

        if (maxWidth >= LibraryDetailWideHeroMinWidth) {
            WideAlbumDetailHeroCard(
                title = title,
                artwork = artwork,
                artworkWidth = wideHeroArtworkWidth,
                metaItems = metaItems,
                canPlay = canPlay,
                onPlay = onPlay,
                onBack = onBack,
                accentColor = accentColor,
            )
        } else {
            CompactAlbumDetailHeroCard(
                title = title,
                artwork = artwork,
                metaItems = metaItems,
                canPlay = canPlay,
                onPlay = onPlay,
                onBack = onBack,
                accentColor = accentColor,
                playContentDescription = playContentDescription,
            )
        }
    }
}

@Composable
private fun WideAlbumDetailHeroCard(
    title: String,
    artwork: Any?,
    artworkWidth: Dp,
    metaItems: List<String>,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    accentColor: Color,
) {
    val playContainer = accentColor.ensureContrast(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary,
    )
    val onPlayAccent = if (playContainer.contrastRatio(Color.White) >= playContainer.contrastRatio(Color.Black)) {
        Color.White
    } else {
        Color.Black
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = artworkWidth)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(artworkWidth),
            ) {
                AdaptiveBlurArtwork(
                    model = artwork,
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 28,
                )
                Surface(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .minimumInteractiveComponentSize()
                        .size(40.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.32f),
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.library_back),
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaLine = metaItems.joinToString(" • ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                FilledTonalButton(
                    onClick = onPlay,
                    enabled = canPlay,
                    modifier = Modifier.padding(top = 22.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = playContainer,
                        contentColor = onPlayAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.library_play))
                }
            }
        }
    }
}

@Composable
private fun CompactAlbumDetailHeroCard(
    title: String,
    artwork: Any?,
    metaItems: List<String>,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    accentColor: Color,
    playContentDescription: String,
) {
    val playContainer = accentColor.ensureContrast(Color.Black, MaterialTheme.colorScheme.primary)
    val onPlayAccent = if (playContainer.contrastRatio(Color.White) >= playContainer.contrastRatio(Color.Black)) {
        Color.White
    } else {
        Color.Black
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        AdaptiveBlurArtwork(
            model = artwork,
            contentDescription = title,
            modifier = Modifier.fillMaxWidth(),
            cornerRadiusDp = 28,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaLine = metaItems.joinToString(" • ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 20.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            FilledIconButton(
                onClick = onPlay,
                enabled = canPlay,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = playContainer,
                    contentColor = onPlayAccent,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = playContentDescription,
                )
            }
        }
        Surface(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .minimumInteractiveComponentSize()
                .size(40.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.32f),
            contentColor = Color.White,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.library_back),
                modifier = Modifier.padding(9.dp),
            )
        }
    }
}

@Composable
private fun LibraryDetailTrackPane(
    modifier: Modifier,
    server: ServerConfig,
    songs: List<Song>,
    title: String,
    subtitle: String,
    loadingLabel: String,
    isLoading: Boolean,
    error: String?,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    currentPlaybackSongId: String?,
    isPlaying: Boolean,
    accentColor: Color,
    albumArtistLabel: String?,
    useSequentialNumbers: Boolean,
    showArtwork: Boolean,
    showAlbumLabel: Boolean = false,
    allowDuplicateSongs: Boolean = false,
    bottomOverlayPadding: Dp,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
) {
    val useTrackNumbers = !useSequentialNumbers && songs.all { (it.track ?: 0) > 0 }
    Card(
        modifier = modifier.padding(top = 8.dp, bottom = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = 24.dp + bottomOverlayPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "detail-track-heading") {
                SectionTitle(title = title, subtitle = subtitle)
            }
            when {
                isLoading && songs.isEmpty() -> item(key = "detail-track-loading") {
                    LoadingStateCard(loadingLabel)
                }
                error != null && songs.isEmpty() -> item(key = "detail-track-error") {
                    ErrorStateCard(error)
                }
                songs.isEmpty() -> item(key = "detail-track-empty") {
                    EmptyStateCard(
                        title = stringResource(R.string.browse_no_songs),
                        body = stringResource(R.string.browse_no_songs_body),
                    )
                }
                else -> itemsIndexed(
                    items = songs,
                    key = { index, song ->
                        if (allowDuplicateSongs) "${song.id}_$index" else song.id
                    },
                ) { index, song ->
                    val isOfflinePlayable = song.isOfflinePlayable(
                        cachedSongsBySongId,
                        streamCachedSongIds,
                    )
                    AlbumTrackRow(
                        song = song,
                        index = index,
                        useTrackNumbers = useTrackNumbers,
                        albumArtistLabel = albumArtistLabel,
                        cachedSong = cachedSongsBySongId[song.id],
                        isStreamCached = song.id in streamCachedSongIds,
                        isDownloading = song.id in downloadingSongIds,
                        isOfflineDegraded = isOfflineDegraded,
                        isOfflinePlayable = isOfflinePlayable,
                        isCurrent = currentPlaybackSongId == song.id,
                        isPlaying = isPlaying,
                        accentColor = accentColor,
                        artworkModel = if (showArtwork) {
                            resolveArtworkModel(server, song.coverArtId, cachedSongsBySongId[song.id])
                        } else {
                            null
                        },
                        secondaryLabel = if (showAlbumLabel) song.album?.takeIf { it.isNotBlank() } else null,
                        onClick = { onPlaySongs(songs, index) },
                        onMore = { onShowActions(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackListCard(
    songs: List<Song>,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isOfflineDegraded: Boolean,
    currentPlaybackSongId: String?,
    isPlaying: Boolean,
    accentColor: Color,
    albumArtistLabel: String?,
    collapsedCount: Int? = null,
    useSequentialNumbers: Boolean = false,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val useTrackNumbers = !useSequentialNumbers && songs.all { (it.track ?: 0) > 0 }
            var expanded by rememberSaveable(songs.size) { mutableStateOf(false) }
            val canCollapse = collapsedCount != null && songs.size > collapsedCount
            val visibleSongs = if (canCollapse && !expanded) songs.take(collapsedCount!!) else songs
            visibleSongs.forEachIndexed { index, song ->
                val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
                AlbumTrackRow(
                    song = song,
                    index = index,
                    useTrackNumbers = useTrackNumbers,
                    albumArtistLabel = albumArtistLabel,
                    cachedSong = cachedSongsBySongId[song.id],
                    isStreamCached = song.id in streamCachedSongIds,
                    isDownloading = song.id in downloadingSongIds,
                    isOfflineDegraded = isOfflineDegraded,
                    isOfflinePlayable = isOfflinePlayable,
                    isCurrent = currentPlaybackSongId == song.id,
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    onClick = { onPlaySongs(songs, index) },
                    onMore = { onShowActions(song) },
                )
            }
            if (canCollapse) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.library_show_less)
                        } else {
                            stringResource(R.string.library_show_all_songs, songs.size)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AlbumTrackRow(
    song: Song,
    index: Int,
    useTrackNumbers: Boolean,
    albumArtistLabel: String?,
    cachedSong: CachedSong?,
    isStreamCached: Boolean,
    isDownloading: Boolean,
    isOfflineDegraded: Boolean = false,
    isOfflinePlayable: Boolean = true,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    artworkModel: Any? = null,
    secondaryLabel: String? = null,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val visuals = SakiTheme.visuals
    val isUnavailableOffline = isOfflineDegraded && !isOfflinePlayable
    val trackLabel = (if (useTrackNumbers) song.track else index + 1).toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .alpha(if (isUnavailableOffline) 0.5f else 1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artworkModel != null) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkCard(
                    model = artworkModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadiusDp = 10,
                    requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingIndicator(isPlaying = isPlaying, color = Color.White)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.width(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent) {
                    NowPlayingIndicator(isPlaying = isPlaying, color = accentColor)
                } else {
                    Text(
                        text = trackLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val supportingLine = secondaryLabel ?: song.artistLabel()?.takeIf { it != albumArtistLabel }
            if (supportingLine != null) {
                Text(
                    text = supportingLine,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            cachedSong != null || isStreamCached -> Icon(
                Icons.Rounded.DownloadDone,
                contentDescription = stringResource(R.string.library_available_offline),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            isDownloading -> CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(14.dp),
                strokeWidth = 2.dp,
            )

            isUnavailableOffline -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.library_unavailable_offline),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        song.durationSeconds?.let { durationSeconds ->
            Text(
                text = formatDurationSeconds(durationSeconds),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        IconButton(onClick = onMore) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.library_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = visuals.browseRowActionIconAlpha),
            )
        }
    }
}

private fun Color.contrastRatio(other: Color): Float {
    val lighter = maxOf(luminance(), other.luminance())
    val darker = minOf(luminance(), other.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.ensureContrast(against: Color, fallback: Color, minRatio: Float = 3f): Color =
    if (contrastRatio(against) >= minRatio) this else fallback

@Composable
private fun NowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "nowPlayingEq")
    val bar1 = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1",
    )
    val bar2 = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(440, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2",
    )
    val bar3 = transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3",
    )
    val heights = if (isPlaying) {
        listOf(bar1.value, bar2.value, bar3.value)
    } else {
        listOf(0.5f, 0.3f, 0.45f)
    }
    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    server: ServerConfig,
    playlist: Playlist,
    cachedSongsBySongId: Map<String, CachedSong>,
    streamCachedSongIds: Set<String>,
    downloadingSongIds: Set<String>,
    isLoading: Boolean,
    error: String?,
    bottomOverlayPadding: Dp = 0.dp,
    isOfflineDegraded: Boolean = false,
    onOfflineSongUnavailable: () -> Unit = {},
    currentPlaybackSongId: String? = null,
    isPlaying: Boolean = false,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShowActions: (Song) -> Unit,
    onBack: () -> Unit = {},
) {
    val songCount = playlist.songCount
    val playPlaylist: () -> Unit = {
        if (playlist.songs.isNotEmpty()) {
            if (isOfflineDegraded) {
                val startIndex = playlist.songs.firstOfflinePlayableIndexOrNull(
                    cachedSongsBySongId,
                    streamCachedSongIds,
                )
                if (startIndex != null) {
                    onPlaySongs(playlist.songs, startIndex)
                } else {
                    onOfflineSongUnavailable()
                }
            } else {
                onPlaySongs(playlist.songs, 0)
            }
        }
    }

    if (!SakiTheme.visuals.useExpressiveSurfaceContainers) {
        val subtitle = listOfNotNull(
            playlist.owner,
            if (songCount != null) songCountText(songCount) else null,
        ).joinToString(" • ")
        LibraryDetailScaffold(
            title = playlist.name,
            subtitle = subtitle,
            artwork = resolveArtworkModel(server, playlist.coverArtId, null),
            bottomOverlayPadding = bottomOverlayPadding,
        ) {
            when {
                isLoading && playlist.songs.isEmpty() -> item { LoadingStateCard(stringResource(R.string.library_loading_playlist)) }
                error != null && playlist.songs.isEmpty() -> item { ErrorStateCard(error) }
                else -> {
                    item {
                        SectionTitle(
                            title = stringResource(R.string.library_tracks),
                            subtitle = stringResource(R.string.library_playlist_sequence),
                            actionLabel = stringResource(R.string.library_play_playlist),
                            onAction = playPlaylist,
                        )
                    }
                    itemsIndexed(playlist.songs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                        val isOfflinePlayable = song.isOfflinePlayable(cachedSongsBySongId, streamCachedSongIds)
                        SongRow(
                            song = song,
                            server = server,
                            cachedSong = cachedSongsBySongId[song.id],
                            isStreamCached = song.id in streamCachedSongIds,
                            isDownloading = song.id in downloadingSongIds,
                            isOfflineDegraded = isOfflineDegraded,
                            isOfflinePlayable = isOfflinePlayable,
                            onClick = { onPlaySongs(playlist.songs, index) },
                            onMore = { onShowActions(song) },
                        )
                    }
                }
            }
        }
        return
    }

    val artworkModel = resolveArtworkModel(server, playlist.coverArtId, null)
    val accent = rememberArtworkAccentColor(
        artworkModel,
        fallback = MaterialTheme.colorScheme.primary,
        harmonizeTarget = MaterialTheme.colorScheme.primary,
    )
    val trackAccent = accent.ensureContrast(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.primary,
    )
    val playlistMetaItems = listOfNotNull(
        playlist.owner,
        songCount?.let { songCountText(it) },
    )
    AdaptiveLibraryDetailLayout(
        modifier = Modifier.fillMaxSize(),
        singlePane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = bottomContentPadding(bottomOverlayPadding),
            ) {
                item {
                    AlbumDetailHeroCard(
                        title = playlist.name,
                        artwork = artworkModel,
                        accentColor = accent,
                        metaItems = playlistMetaItems,
                        canPlay = playlist.songs.isNotEmpty(),
                        onPlay = playPlaylist,
                        onBack = onBack,
                        playContentDescription = stringResource(R.string.library_play_playlist),
                    )
                }
                when {
                    isLoading && playlist.songs.isEmpty() -> item {
                        LoadingStateCard(stringResource(R.string.library_loading_playlist))
                    }
                    error != null && playlist.songs.isEmpty() -> item { ErrorStateCard(error) }
                    else -> itemsIndexed(
                        playlist.songs,
                        key = { index, song -> "${song.id}_$index" },
                    ) { index, song ->
                        val isOfflinePlayable = song.isOfflinePlayable(
                            cachedSongsBySongId,
                            streamCachedSongIds,
                        )
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
                            accentColor = trackAccent,
                            artworkModel = resolveArtworkModel(
                                server,
                                song.coverArtId,
                                cachedSongsBySongId[song.id],
                            ),
                            onClick = { onPlaySongs(playlist.songs, index) },
                            onMore = { onShowActions(song) },
                        )
                    }
                }
            }
        },
        infoPane = { infoModifier ->
            LibraryDetailCenteredInfoPane(
                modifier = infoModifier,
                bottomOverlayPadding = bottomOverlayPadding,
            ) {
                AlbumDetailHeroCard(
                    title = playlist.name,
                    artwork = artworkModel,
                    accentColor = accent,
                    metaItems = playlistMetaItems,
                    canPlay = playlist.songs.isNotEmpty(),
                    onPlay = playPlaylist,
                    onBack = onBack,
                    playContentDescription = stringResource(R.string.library_play_playlist),
                )
            }
        },
        contentPane = { contentModifier ->
            LibraryDetailTrackPane(
                modifier = contentModifier,
                server = server,
                songs = playlist.songs,
                title = stringResource(R.string.library_tracks),
                subtitle = stringResource(R.string.library_playlist_sequence),
                loadingLabel = stringResource(R.string.library_loading_playlist),
                isLoading = isLoading,
                error = error,
                cachedSongsBySongId = cachedSongsBySongId,
                streamCachedSongIds = streamCachedSongIds,
                downloadingSongIds = downloadingSongIds,
                isOfflineDegraded = isOfflineDegraded,
                currentPlaybackSongId = currentPlaybackSongId,
                isPlaying = isPlaying,
                accentColor = trackAccent,
                albumArtistLabel = null,
                useSequentialNumbers = true,
                showArtwork = true,
                allowDuplicateSongs = true,
                bottomOverlayPadding = bottomOverlayPadding,
                onPlaySongs = onPlaySongs,
                onShowActions = onShowActions,
            )
        },
    )
}

@Composable
private fun LibraryDetailScaffold(
    title: String,
    subtitle: String?,
    artwork: Any?,
    bottomOverlayPadding: Dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = bottomContentPadding(bottomOverlayPadding),
    ) {
        item {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
                if (artwork != null) {
                    ArtworkCard(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        cornerRadiusDp = 34,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = if (artwork != null) 14.dp else 0.dp),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun ArtistRow(artist: ArtistSummary, onOpenArtist: (String) -> Unit) {
    val albumCount = artist.albumCount
    RowCard(
        title = artist.name,
        subtitle = if (albumCount != null) albumCountText(albumCount) else null,
        artwork = null,
        onClick = { onOpenArtist(artist.id) },
    )
}

@Composable
fun PlaylistCard(playlist: PlaylistSummary, server: ServerConfig, onOpenPlaylist: (String) -> Unit) {
    val songCount = playlist.songCount
    val subtitle = listOfNotNull(
        playlist.owner,
        if (songCount != null) songCountText(songCount) else null,
    ).joinToString(" • ")
    RowCard(
        title = playlist.name,
        subtitle = subtitle,
        artwork = resolveArtworkModel(server, playlist.coverArtId, null),
        artworkRequestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
        onClick = { onOpenPlaylist(playlist.id) },
    )
}

@Composable
fun AlbumRow(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    val songCount = album.songCount
    val subtitle = listOfNotNull(
        album.artistDisplayLabel(),
        album.year?.toString(),
        if (songCount != null) songCountText(songCount) else null,
    ).joinToString(" • ")
    RowCard(
        title = album.name,
        subtitle = subtitle,
        artwork = resolveArtworkModel(server, album.coverArtId, null),
        artworkRequestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
        onClick = { onOpenAlbum(album.id) },
    )
}

@Composable
fun ArtistShortcutCard(artist: ArtistSummary, onOpenArtist: (String) -> Unit) {
    val albumCount = artist.albumCount
    Card(
        modifier = Modifier
            .width(190.dp)
            .padding(end = 12.dp)
            .clickable { onOpenArtist(artist.id) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = sakiCardContainerColor()),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = artist.name, style = MaterialTheme.typography.titleLarge)
            if (albumCount != null) {
                Text(
                    text = releaseCountText(albumCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AlbumCard(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    Card(
        onClick = { onOpenAlbum(album.id) },
        modifier = Modifier
            .padding(6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = sakiCardContainerColor()),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ArtworkCard(
                model = resolveArtworkModel(server, album.coverArtId, null),
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                cornerRadiusDp = 24,
                requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
            )
            Text(text = album.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(album.artistDisplayLabel(), album.year?.toString()).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumMiniCard(album: AlbumSummary, server: ServerConfig, onOpenAlbum: (String) -> Unit) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .padding(end = 12.dp)
            .clickable { onOpenAlbum(album.id) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = sakiCardContainerColor()),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ArtworkCard(
                model = resolveArtworkModel(server, album.coverArtId, null),
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
                cornerRadiusDp = 18,
                requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
            )
            Text(text = album.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = album.year?.toString() ?: album.artistDisplayLabel().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    server: ServerConfig,
    cachedSong: CachedSong?,
    isStreamCached: Boolean,
    isDownloading: Boolean,
    isOfflineDegraded: Boolean = false,
    isOfflinePlayable: Boolean = true,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val visuals = SakiTheme.visuals
    val isUnavailableOffline = isOfflineDegraded && !isOfflinePlayable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .alpha(if (isUnavailableOffline) 0.5f else 1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkCard(
            model = resolveArtworkModel(server, song.coverArtId, cachedSong),
            contentDescription = song.title,
            modifier = Modifier.size(60.dp),
            cornerRadiusDp = 18,
            requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    cachedSong != null || isStreamCached -> Icon(
                        Icons.Rounded.DownloadDone,
                        contentDescription = stringResource(R.string.library_available_offline),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    isDownloading -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )

                    isUnavailableOffline -> Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = stringResource(R.string.library_unavailable_offline),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SongArtistAlbumLine(
                    song = song,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        IconButton(onClick = onMore) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.library_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = visuals.browseRowActionIconAlpha),
            )
        }
    }
}

@Composable
private fun SongArtistAlbumLine(
    song: Song,
    modifier: Modifier = Modifier,
) {
    val artistLinks = song.artistRefsForDisplay()
    if (artistLinks.isEmpty() && song.album.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.library_unknown_artist_album),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        artistLinks.forEachIndexed { index, artist ->
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (index != artistLinks.lastIndex) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (artistLinks.isNotEmpty() && !song.album.isNullOrBlank()) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        song.album?.takeIf(String::isNotBlank)?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SongActionsSheet(
    song: Song,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleDownload: () -> Unit,
    onDetails: () -> Unit,
    onQueueSong: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = song.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = song.artistAlbumLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SheetActionRow(
                icon = Icons.Rounded.SkipNext,
                title = stringResource(R.string.library_play_next),
                subtitle = stringResource(R.string.library_play_next_subtitle),
                onClick = onPlayNext,
            )
            SheetActionRow(
                icon = if (isDownloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.CloudDownload,
                title = when {
                    isDownloaded -> stringResource(R.string.settings_remove_download)
                    isDownloading -> stringResource(R.string.library_downloading)
                    else -> stringResource(R.string.library_download)
                },
                subtitle = if (isDownloaded) {
                    stringResource(R.string.library_delete_cached_copy)
                } else {
                    stringResource(R.string.library_save_offline)
                },
                enabled = !isDownloading,
                onClick = onToggleDownload,
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = stringResource(R.string.library_add_to_queue),
                subtitle = stringResource(R.string.library_add_to_queue_subtitle),
                onClick = onQueueSong,
            )
            SheetActionRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.library_details),
                subtitle = stringResource(R.string.library_show_metadata),
                onClick = onDetails,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.common_close))
            }
        },
        title = { Text(song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(stringResource(R.string.detail_artist), song.artistLabel())
                DetailLine(stringResource(R.string.detail_album), song.album)
                DetailLine(stringResource(R.string.detail_track), song.track?.toString())
                DetailLine(stringResource(R.string.detail_disc), song.discNumber?.toString())
                DetailLine(stringResource(R.string.detail_genre), song.genre)
                DetailLine(stringResource(R.string.detail_bitrate), song.bitRate?.let { "$it kbps" })
                DetailLine(stringResource(R.string.detail_duration), song.durationSeconds?.let(::formatDurationSeconds))
            }
        },
    )
}

@Composable
fun NoServerBrowseState(modifier: Modifier, onManageServers: () -> Unit, onImportBackup: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = sakiCardContainerColor()),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.browse_needs_server), style = MaterialTheme.typography.displaySmall)
                Text(
                    text = stringResource(R.string.browse_needs_server_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onManageServers, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.browse_add_server)) }
                    OutlinedButton(onClick = onImportBackup, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.browse_import_backup)) }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(actionLabel, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingStateCard(label: String) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = sakiCardContainerColor())) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (SakiTheme.visuals.useExpressiveLoadingIndicator) {
                LoadingIndicator(modifier = Modifier.size(28.dp))
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(text = label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorStateCard(message: String) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text = message,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun EmptyStateCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = sakiSubtleCardContainerColor())) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowCard(
    title: String,
    subtitle: String?,
    artwork: Any?,
    artworkRequestSizePx: Int? = null,
    onClick: () -> Unit,
) {
    val visuals = SakiTheme.visuals
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = sakiSubtleCardContainerColor()),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (artwork != null) {
                ArtworkCard(
                    model = artwork,
                    contentDescription = title,
                    modifier = Modifier.size(72.dp),
                    cornerRadiusDp = 22,
                    requestSizePx = artworkRequestSizePx,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (artwork != null) 12.dp else 0.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = visuals.browseRowNavigationIconAlpha),
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = sakiTonalContainerColor(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    val unknown = stringResource(R.string.detail_unknown)
    Text(
        text = stringResource(
            R.string.detail_line_format,
            label,
            value.orEmpty().ifBlank { unknown },
        ),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun albumCountText(count: Int): String =
    pluralStringResource(R.plurals.library_album_count, count, count)

@Composable
private fun songCountText(count: Int): String =
    pluralStringResource(R.plurals.library_song_count, count, count)

@Composable
private fun releaseCountText(count: Int): String =
    pluralStringResource(R.plurals.library_release_count, count, count)

private fun formatDurationSeconds(durationSeconds: Int): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun Song.artistRefsForDisplay(): List<ArtistRef> {
    if (artists.isNotEmpty()) return artists
    val fallbackName = artist?.takeIf(String::isNotBlank) ?: return emptyList()
    return listOf(ArtistRef(id = artistId.orEmpty(), name = fallbackName))
}

private fun Song.artistLabel(): String? {
    return artistRefsForDisplay().artistNamesLabel()
}

private fun Song.artistAlbumLabel(): String {
    return listOfNotNull(artistLabel(), album?.takeIf(String::isNotBlank))
        .joinToString(" • ")
}

private fun AlbumSummary.artistDisplayLabel(): String? {
    return artists.artistNamesLabel() ?: artist
}

private fun Album.artistDisplayLabel(): String? {
    return artists.artistNamesLabel() ?: artist
}

private fun List<ArtistRef>.artistNamesLabel(): String? {
    return joinToString(" / ") { it.name }
        .ifBlank { null }
}
