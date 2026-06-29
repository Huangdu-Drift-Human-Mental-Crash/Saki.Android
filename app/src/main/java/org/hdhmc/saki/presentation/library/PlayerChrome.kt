package org.hdhmc.saki.presentation.library

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.lerp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import org.hdhmc.saki.R
import org.hdhmc.saki.presentation.EndpointProbeInfo
import org.hdhmc.saki.presentation.predictiveBackMotion
import org.hdhmc.saki.domain.model.ArtistRef
import org.hdhmc.saki.domain.model.PlaybackQueueItem
import org.hdhmc.saki.domain.model.PlaybackProgressState
import org.hdhmc.saki.domain.model.PlaybackRuntimeInfo
import org.hdhmc.saki.domain.model.PlaybackSessionState
import org.hdhmc.saki.domain.model.RepeatModeSetting
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.ui.theme.LocalSakiPaletteStyle
import org.hdhmc.saki.ui.theme.SakiTheme
import org.hdhmc.saki.ui.theme.rememberSakiExpressiveColorScheme
import java.io.File
import coil3.imageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingCapsule(
    track: PlaybackQueueItem?,
    isPlaying: Boolean,
    currentServer: ServerConfig?,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSkipToNext: () -> Unit,
    prewarmDynamicColors: Boolean = false,
) {
    val visuals = SakiTheme.visuals
    // Warm the current track's artwork color into the cache while the mini player is
    // shown, so opening Now Playing finds it ready instead of animating from the theme
    // fallback to the artwork accent on first view.
    if (prewarmDynamicColors) {
        val prewarmContext = LocalContext.current.applicationContext
        LaunchedEffect(track?.songId) {
            val model = track?.queueArtworkModel(currentServer) ?: return@LaunchedEffect
            prewarmArtworkPresentation(prewarmContext, model)
        }
    }
    val capsuleContainerColor = if (visuals.useExpressiveSurfaceContainers) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = visuals.nowPlayingCapsuleContainerAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = visuals.nowPlayingCapsuleContainerAlpha)
    }
    val elevation by animateDpAsState(
        targetValue = if (isPlaying) 12.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "capsuleElevation",
    )
    val onExpandState = rememberUpdatedState(onExpand)

    Card(
        onClick = onExpand,
        enabled = track != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp)
            .pointerInput(track != null) {
                if (track == null) return@pointerInput
                val distanceThresholdPx = 72.dp.toPx()
                val velocityThresholdDpPerSecond = 300f
                var upwardDistance = 0f
                var dragStartedAtNanos = 0L
                var didOpen = false

                fun openFromSwipe() {
                    if (!didOpen) {
                        didOpen = true
                        onExpandState.value()
                    }
                }

                detectVerticalDragGestures(
                    onDragStart = {
                        upwardDistance = 0f
                        dragStartedAtNanos = System.nanoTime()
                        didOpen = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < 0f) {
                            upwardDistance -= dragAmount
                            if (upwardDistance >= distanceThresholdPx) {
                                openFromSwipe()
                            }
                        } else {
                            upwardDistance = (upwardDistance - dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        val elapsedSeconds = (System.nanoTime() - dragStartedAtNanos) / 1_000_000_000f
                        val upwardVelocityDpPerSecond = if (elapsedSeconds > 0f) {
                            (upwardDistance / elapsedSeconds).toDp().value
                        } else {
                            0f
                        }
                        if (elapsedSeconds > 0f &&
                            upwardVelocityDpPerSecond >= velocityThresholdDpPerSecond
                        ) {
                            openFromSwipe()
                        }
                    },
                    onDragCancel = {
                        upwardDistance = 0f
                        didOpen = false
                    },
                )
            },
        shape = RoundedCornerShape(visuals.miniPlayerContainerCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = capsuleContainerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = track != null) {
                Box(
                    modifier = Modifier
                        .size(width = visuals.miniPlayerHandleWidth, height = 3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = visuals.miniPlayerHandleAlpha,
                            ),
                            shape = RoundedCornerShape(100),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = Pair(
                        track?.queueArtworkModel(currentServer),
                        track?.title,
                    ),
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "capsule-artwork",
                ) { (model, title) ->
                    ArtworkCard(
                        model = model,
                        contentDescription = title,
                        modifier = Modifier.size(46.dp),
                        cornerRadiusDp = visuals.miniPlayerArtworkCornerRadius.value.roundToInt(),
                        requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = track?.title ?: stringResource(R.string.player_nothing_playing),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.let { listOfNotNull(it.artist, it.album).joinToString(" • ") }
                            ?: stringResource(R.string.player_start_from_browse),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MiniPlayerIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous),
                    onClick = onSkipToPrevious,
                    enabled = track != null,
                )
                MiniPlayerIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) {
                        stringResource(R.string.player_pause)
                    } else {
                        stringResource(R.string.player_play)
                    },
                    onClick = onPlayPause,
                    enabled = track != null,
                    primary = true,
                )
                MiniPlayerIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    onClick = onSkipToNext,
                    enabled = track != null,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean = false,
) {
    val visuals = SakiTheme.visuals
    if (visuals.miniPlayerControlContainerAlpha <= 0f && (!primary || visuals.miniPlayerPrimaryControlContainerAlpha <= 0f)) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        val isPrimary = primary && visuals.miniPlayerPrimaryControlContainerAlpha > 0f
        PressScaleIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            compact = true,
            tint = if (isPrimary) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            containerColor = if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = visuals.miniPlayerPrimaryControlContainerAlpha,
                )
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                    alpha = visuals.miniPlayerControlContainerAlpha,
                )
            },
            cornerRadius = if (isPrimary) {
                visuals.miniPlayerPrimaryControlSize / 2f
            } else {
                visuals.miniPlayerControlCornerRadius
            },
            iconSize = if (isPrimary) {
                visuals.miniPlayerPrimaryControlIconSize
            } else {
                visuals.miniPlayerControlIconSize
            },
            buttonSize = if (isPrimary) {
                visuals.miniPlayerPrimaryControlSize
            } else {
                48.dp
            },
            containerSize = if (isPrimary) {
                visuals.miniPlayerPrimaryControlSize
            } else {
                visuals.miniPlayerControlVisibleSize
            },
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingOverlay(
    visible: Boolean,
    playbackState: PlaybackSessionState,
    playbackProgressFlow: StateFlow<PlaybackProgressState>,
    track: PlaybackQueueItem,
    onDismiss: () -> Unit,
    canOpenArtist: (String?) -> Boolean,
    onOpenArtist: (String?) -> Unit,
    onOpenAlbum: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    currentServer: ServerConfig?,
    servers: List<ServerConfig> = emptyList(),
    activeEndpointLabel: String? = null,
    activeEndpointId: Long? = null,
    isEndpointForced: Boolean = false,
    endpointProbeResults: List<EndpointProbeInfo> = emptyList(),
    isProbing: Boolean = false,
    onReprobeEndpoints: () -> Unit = {},
    onForceEndpoint: (Long) -> Unit = {},
    lyrics: SongLyrics? = null,
    useDynamicArtworkColors: Boolean = true,
    useGradientBackground: Boolean = true,
    useArtworkMotion: Boolean = true,
    useArtworkBackdrop: Boolean = false,
    artworkPrewarmRadius: Int = ARTWORK_PREWARM_RADIUS_PAGES,
) {
    val visuals = SakiTheme.visuals
    val serversById = remember(servers) { servers.associateBy { it.id } }
    var showDetails by remember(track.songId) { mutableStateOf(false) }
    var showMenu by remember(track.songId) { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showEndpointStatus by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val visualSnapshot = rememberNowPlayingVisualSnapshot(
        queue = playbackState.queue,
        currentIndex = playbackState.currentIndex,
        currentTrack = track,
    )
    val artworkMotionState = rememberNowPlayingArtworkMotionState(visualSnapshot.currentIndex, visible)
    var visualSkipRequest by remember { mutableStateOf<ArtworkPageRequest?>(null) }
    fun requestVisualSkip(delta: Int) {
        val queue = visualSnapshot.queue
        val basePage = visualSkipRequest
            ?.page
            ?.takeIf { it in queue.indices }
            ?: visualSnapshot.currentIndex
        val targetPage = basePage + delta
        if (targetPage in queue.indices) {
            visualSkipRequest = ArtworkPageRequest(
                page = targetPage,
                sequence = (visualSkipRequest?.sequence ?: 0) + 1,
            ).takeUnless { targetPage == visualSnapshot.currentIndex }
        }
    }
    LaunchedEffect(visualSkipRequest, visualSnapshot.currentIndex) {
        val request = visualSkipRequest ?: return@LaunchedEffect
        if (visualSnapshot.currentIndex == request.page) {
            visualSkipRequest = null
            return@LaunchedEffect
        }
        delay(ARTWORK_BUTTON_SKIP_CONFIRM_TIMEOUT_MS)
        if (visualSkipRequest == request && visualSnapshot.currentIndex != request.page) {
            visualSkipRequest = null
        }
    }
    val visualCurrentServer = visualSnapshot.currentTrack.serverId?.let { serversById[it] }
        ?: currentServer.takeIf { it?.id == visualSnapshot.currentTrack.serverId }

    // Preload adjacent artwork into Coil and palette caches.
    val context = LocalContext.current
    val queue = visualSnapshot.queue
    val currentIdx = visualSnapshot.currentIndex
    val prewarmRadius = artworkPrewarmRadius.coerceAtLeast(0)
    val prewarmArtworkKeys = remember(queue, currentIdx, prewarmRadius) {
        (currentIdx - prewarmRadius..currentIdx + prewarmRadius)
            .mapNotNull { index -> queue.getOrNull(index)?.artworkIdentityKey() }
    }
    LaunchedEffect(
        visible,
        visualSnapshot.currentTrack.artworkIdentityKey(),
        prewarmArtworkKeys,
        visualCurrentServer,
        useDynamicArtworkColors,
        prewarmRadius,
    ) {
        if (!visible) return@LaunchedEffect
        val adjacentIndices = (currentIdx - prewarmRadius..currentIdx + prewarmRadius)
            .filter { it in queue.indices && it != currentIdx }
        for (i in adjacentIndices) {
            val item = queue[i]
            val server = item.serverId?.let { serversById[it] }
            val model = item.queueArtworkModel(server) ?: continue
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(FULL_COVER_ART_SIZE_PX)
                .build()
            context.imageLoader.enqueue(request)
            if (useDynamicArtworkColors) {
                prewarmArtworkPresentation(context.applicationContext, model)
            }
        }
    }

    val latestOnDismiss by rememberUpdatedState(onDismiss)
    // Back is consumed by the topmost transient/anchored surface first; only run the
    // page-level predictive back (dismiss Now Playing) when none of them are showing.
    val backConsumedByOverlay =
        showQueueSheet || showDetails || showMenu || showLyrics || showEndpointStatus
    val predictiveBackModifier = Modifier.predictiveBackMotion(
        enabled = visible && !backConsumedByOverlay,
        onBack = { latestOnDismiss() },
    )
    // Internal-state back: collapse the lyrics panel before exiting the page.
    BackHandler(enabled = visible && showLyrics) { showLyrics = false }

    // Reset transient Now Playing overlays when the player is dismissed.
    LaunchedEffect(visible) {
        if (!visible) {
            showLyrics = false
            showQueueSheet = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessVeryLow,
            ),
            initialOffsetY = { fullHeight -> fullHeight / 4 },
        ),
        exit = fadeOut(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        ) + slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            targetOffsetY = { fullHeight -> fullHeight / 3 },
        ),
    ) {
        val colorScheme = MaterialTheme.colorScheme
        val isDark = colorScheme.background.luminance() < 0.5f
        val isExpressive = visuals.useExpressiveSurfaceContainers
        val paletteStyle = LocalSakiPaletteStyle.current
        // Build a scheme from the current track's seed using the same palette-style mapping
        // as the app theme, so the unplayed bar (and any future role use) tracks the selected
        // palette style automatically — new styles need no change here.
        val artworkScheme = if (isExpressive && useDynamicArtworkColors) {
            val model = remember(track.songId, serversById) {
                track.queueArtworkModel(track.serverId?.let { serversById[it] })
            }
            rememberSakiExpressiveColorScheme(
                seedColor = rememberArtworkSeed(model) ?: colorScheme.primary,
                isDark = isDark,
                paletteStyle = paletteStyle,
            )
        } else {
            null
        }
        val rawArtworkColors = if (useDynamicArtworkColors) {
            rememberMotionArtworkColors(
                queue = visualSnapshot.queue,
                serversById = serversById,
                position = artworkMotionState.position,
                currentIndex = visualSnapshot.currentIndex,
                freezePresentationUpdates = artworkMotionState.isScrollInProgress || visualSkipRequest != null,
                expressive = isExpressive,
                isDark = isDark,
                fallbackDominant = colorScheme.primary,
                fallbackAccent = colorScheme.tertiary,
                prewarmRadius = prewarmRadius,
            )
        } else {
            ArtworkColors(
                dominant = colorScheme.primary,
                accent = colorScheme.tertiary,
            )
        }
        val artworkColors = rememberDisplayedArtworkColors(
            targetColors = rawArtworkColors,
            followImmediately = artworkMotionState.isScrollInProgress || !visible || !useDynamicArtworkColors,
        )
        val dominant = artworkColors.dominant
        val accent = artworkColors.accent
        val background = remember(dominant, accent, colorScheme, visuals, useGradientBackground) {
            if (useGradientBackground) {
                Brush.verticalGradient(
                    listOf(
                        dominant.copy(alpha = visuals.nowPlayingBackgroundDominantOverlayAlpha)
                            .compositeOver(colorScheme.background),
                        accent.copy(alpha = visuals.nowPlayingBackgroundAccentOverlayAlpha)
                            .compositeOver(colorScheme.surface),
                        dominant.copy(alpha = visuals.nowPlayingBackgroundTailOverlayAlpha)
                            .compositeOver(colorScheme.background),
                    ),
                )
            } else {
                SolidColor(colorScheme.background)
            }
        }
        val playButtonColor = remember(dominant, isDark) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominant.toArgb(), hsv)
            hsv[1] = hsv[1].coerceAtMost(0.35f)
            hsv[2] = if (isDark) 0.30f else 0.88f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        val onPlayButtonColor = if (isDark) Color.White else Color.Black
        val onArtwork = onPlayButtonColor
        val sliderActiveColor = remember(dominant, accent, isDark, isExpressive) {
            if (isExpressive) {
                accent
            } else {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(dominant.toArgb(), hsv)
                hsv[1] = hsv[1].coerceIn(0.30f, 0.55f)
                hsv[2] = if (isDark) 0.70f else 0.45f
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
        val sliderInactiveColor = if (artworkScheme != null) {
            // Take hue/chroma from the scheme's secondary (varies per palette style) but pin
            // the tone to a visible band, so the unplayed track stays legible in dark mode
            // instead of collapsing to the near-black secondaryContainer tone.
            val sec = Hct.fromInt(artworkScheme.secondary.toArgb())
            Color(Hct.from(sec.hue, sec.chroma.coerceAtMost(28.0), if (isDark) 52.0 else 80.0).toInt())
        } else {
            sliderActiveColor.copy(alpha = 0.25f)
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(predictiveBackModifier)
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(Unit) { detectTapGestures { /* consume all taps */ } },
        ) {
            val playerSnackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var snackbarJob by remember { mutableStateOf<Job?>(null) }
            fun showPlayerSnackbar(message: String) {
                snackbarJob?.cancel()
                playerSnackbarHostState.currentSnackbarData?.dismiss()
                snackbarJob = scope.launch {
                    val showJob = launch {
                        playerSnackbarHostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
                    }
                    delay(1500)
                    showJob.cancel()
                    playerSnackbarHostState.currentSnackbarData?.dismiss()
                }
            }
            val combinedMetadata = listOfNotNull(track.artist, track.album).joinToString(" • ")
            val denseTitle = track.title.length >= 24
            val denseMetadata = combinedMetadata.length >= 42
            val compactControls = maxHeight < 640.dp
            val shortScreen = maxHeight < 700.dp
            val titleStyle = when {
                track.title.length >= 34 -> MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                )
                denseTitle || shortScreen -> MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                )
                else -> MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                )
            }
            val metadataStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (denseMetadata || shortScreen) 16.sp else 17.sp,
                lineHeight = 22.sp,
            )
            val horizontalPadding = if (shortScreen) 16.dp else 20.dp
            val verticalSpacing = if (shortScreen) 8.dp else 12.dp
            val showQueueAffordance = playbackState.queue.size > 1
            val latestOpenQueueSheet by rememberUpdatedState { showQueueSheet = true }
            val dismissSwipeModifier = Modifier.pointerInput(visible) {
                if (!visible) return@pointerInput
                val dismissThresholdPx = 72.dp.toPx()
                val velocityThresholdDpPerSecond = 300f
                var downwardDistance = 0f
                var dragStartedAtNanos = 0L
                var didDismiss = false

                fun dismissFromSwipe() {
                    if (visible && !didDismiss) {
                        didDismiss = true
                        latestOnDismiss()
                    }
                }

                detectVerticalDragGestures(
                    onDragStart = {
                        downwardDistance = 0f
                        dragStartedAtNanos = System.nanoTime()
                        didDismiss = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) {
                            downwardDistance += dragAmount
                            if (downwardDistance >= dismissThresholdPx) {
                                dismissFromSwipe()
                            }
                        } else {
                            downwardDistance = (downwardDistance + dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        val elapsedSeconds = (System.nanoTime() - dragStartedAtNanos) / 1_000_000_000f
                        if (elapsedSeconds > 0f) {
                            val downwardVelocityDpPerSecond = (downwardDistance / elapsedSeconds).toDp().value
                            if (downwardVelocityDpPerSecond >= velocityThresholdDpPerSecond) {
                                dismissFromSwipe()
                            }
                        }
                    },
                    onDragCancel = {
                        downwardDistance = 0f
                        didDismiss = false
                    },
                )
            }
            val queueSwipeModifier = Modifier.pointerInput(showQueueAffordance) {
                if (!showQueueAffordance) return@pointerInput
                val thresholdPx = 80.dp.toPx()
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < 0f) {
                            accumulated -= dragAmount
                            if (accumulated >= thresholdPx) {
                                accumulated = 0f
                                latestOpenQueueSheet()
                            }
                        } else {
                            accumulated = 0f
                        }
                    },
                )
            }

            CompositionLocalProvider(LocalContentColor provides onArtwork) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                    Spacer(Modifier.heightIn(min = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_now_playing),
                            style = MaterialTheme.typography.titleLarge,
                            color = onArtwork,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = (if (isDark) Color.Black else Color.White).copy(alpha = 0.28f),
                        ) {
                            Text(
                                text = when {
                                    track.isCached -> stringResource(R.string.player_offline)
                                    playbackState.isStreamCached -> stringResource(R.string.player_cached)
                                    else -> stringResource(R.string.player_streaming)
                                } + " • ${localizeQualityLabel(track.qualityLabel)}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = onArtwork,
                            )
                        }
                    }
                    // Cover art — fills remaining vertical space
                    val hasLyrics = lyrics != null && lyrics.lines.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NowPlayingArtworkPagerHost(
                            queue = visualSnapshot.queue,
                            currentIndex = visualSnapshot.currentIndex,
                            currentTrack = visualSnapshot.currentTrack,
                            serversById = serversById,
                            motionState = artworkMotionState,
                            visualSkipRequest = visualSkipRequest,
                            useProgrammaticMotion = useArtworkMotion,
                            useArtworkBackdrop = useArtworkBackdrop,
                            modifier = Modifier.fillMaxSize(),
                            onArtworkClick = {
                                if (showLyrics) showLyrics = false
                                else if (hasLyrics) showLyrics = true
                            },
                            onUserSelectQueueItem = onSkipToQueueItem,
                        )
                        // Lyrics overlay on artwork
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showLyrics && hasLyrics,
                            enter = fadeIn(tween(250)),
                            exit = fadeOut(tween(250)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(34.dp))
                                    .background(Color.Black.copy(alpha = visuals.nowPlayingLyricsOverlayAlpha)),
                            ) {
                                if (lyrics != null && lyrics.lines.isNotEmpty()) {
                                    SyncedLyricsView(
                                        lyrics = lyrics,
                                        playbackProgressFlow = playbackProgressFlow,
                                        isPlaying = playbackState.isPlaying,
                                        onSeekTo = onSeekTo,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                                        textColor = Color.White,
                                    )
                                }
                                IconButton(
                                    onClick = { showLyrics = false },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.player_close_lyrics),
                                        tint = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(dismissSwipeModifier),
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    ) {
                        // Repeat / Shuffle / More row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val repeatDescription = stringResource(R.string.player_repeat)
                            val repeatOneLabel = stringResource(R.string.player_repeat_one)
                            val repeatAllLabel = stringResource(R.string.player_repeat_all)
                            val repeatOffLabel = stringResource(R.string.player_repeat_off)
                            val shuffleDescription = stringResource(R.string.player_shuffle)
                            val shuffleOnLabel = stringResource(R.string.player_shuffle_on)
                            val shuffleOffLabel = stringResource(R.string.player_shuffle_off)
                            Row(
                                modifier = Modifier.offset(x = -visuals.nowPlayingTopControlEdgeOffset),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ToggleIconButton(
                                    icon = if (playbackState.repeatMode == RepeatModeSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    active = playbackState.repeatMode != RepeatModeSetting.OFF,
                                    contentDescription = repeatDescription,
                                    onClick = {
                                        onCycleRepeatMode()
                                        val label = when (playbackState.repeatMode) {
                                            RepeatModeSetting.OFF -> repeatAllLabel
                                            RepeatModeSetting.ALL -> repeatOneLabel
                                            RepeatModeSetting.ONE -> repeatOffLabel
                                        }
                                        showPlayerSnackbar(label)
                                    },
                                    compact = true,
                                )
                                ToggleIconButton(
                                    icon = Icons.Rounded.Shuffle,
                                    active = playbackState.shuffleEnabled,
                                    contentDescription = shuffleDescription,
                                    onClick = {
                                        onToggleShuffle()
                                        val label = if (!playbackState.shuffleEnabled) shuffleOnLabel else shuffleOffLabel
                                        showPlayerSnackbar(label)
                                    },
                                    compact = true,
                                )
                            }
                            Box(modifier = Modifier.offset(x = visuals.nowPlayingTopControlEdgeOffset)) {
                                PressScaleIconButton(
                                    icon = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.player_more),
                                    onClick = { showMenu = true },
                                    compact = true,
                                    tint = LocalContentColor.current,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = visuals.nowPlayingMoreControlContainerAlpha,
                                    ),
                                    cornerRadius = visuals.nowPlayingSecondaryControlCornerRadius,
                                    iconSize = visuals.nowPlayingSecondaryControlIconSize,
                                )
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.player_song_details)) },
                                        onClick = {
                                            showMenu = false
                                            showDetails = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                activeEndpointLabel
                                                    ?: if (isProbing) {
                                                        stringResource(R.string.player_probing)
                                                    } else {
                                                        stringResource(R.string.player_no_endpoint)
                                                    },
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showEndpointStatus = true
                                        },
                                    )
                                }
                            }
                        }
                        Text(
                            text = track.title,
                            style = titleStyle,
                            color = onArtwork,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MetadataLinkRow(
                            track = track,
                            textStyle = metadataStyle,
                            linkColor = sliderActiveColor,
                            canOpenArtist = canOpenArtist,
                            onOpenArtist = onOpenArtist,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                    val isCachedTrack = track.isCached || playbackState.isStreamCached
                    NowPlayingProgressSection(
                        playbackProgressFlow = playbackProgressFlow,
                        trackId = track.songId,
                        isCachedTrack = isCachedTrack,
                        sliderActiveColor = sliderActiveColor,
                        sliderInactiveColor = sliderInactiveColor,
                        onArtwork = onArtwork,
                        verticalSpacing = verticalSpacing,
                        onSeekTo = onSeekTo,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(queueSwipeModifier),
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerActionButton(
                                icon = Icons.Rounded.SkipPrevious,
                                label = stringResource(R.string.player_previous),
                                onClick = {
                                    requestVisualSkip(-1)
                                    onSkipToPrevious()
                                },
                                compact = compactControls,
                            )
                            Spacer(Modifier.width(14.dp))
                            Surface(
                                modifier = Modifier.size(
                                    width = if (compactControls) 132.dp else 148.dp,
                                    height = if (compactControls) 64.dp else 72.dp,
                                ),
                                shape = RoundedCornerShape(visuals.nowPlayingPrimaryControlCornerRadius),
                                color = playButtonColor,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(onClick = onPlayPause)
                                        .padding(horizontal = visuals.nowPlayingPrimaryControlHorizontalPadding),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = onPlayButtonColor,
                                        modifier = Modifier.size(visuals.nowPlayingPrimaryControlIconSize),
                                    )
                                    Text(
                                        text = if (playbackState.isPlaying) {
                                            stringResource(R.string.player_pause)
                                        } else {
                                            stringResource(R.string.player_play)
                                        },
                                        modifier = Modifier.padding(start = visuals.nowPlayingPrimaryControlLabelSpacing),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = onPlayButtonColor,
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            PlayerActionButton(
                                icon = Icons.Rounded.SkipNext,
                                label = stringResource(R.string.player_next),
                                onClick = {
                                    requestVisualSkip(1)
                                    onSkipToNext()
                                },
                                compact = compactControls,
                            )
                        }
                        // Tech info bar
                        val techTextCandidate = track
                            .compactTechnicalInfoParts(
                                playbackState.runtimeInfo?.takeIf { runtimeInfo -> runtimeInfo.hasCompactTechnicalInfo() },
                            )
                            .joinToString(" | ")
                        var visibleTechText by remember { mutableStateOf("") }
                        var visibleTechMediaId by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(track.mediaId, techTextCandidate) {
                            if (visibleTechMediaId != track.mediaId) {
                                visibleTechMediaId = track.mediaId
                                visibleTechText = ""
                            }
                            if (techTextCandidate.isBlank()) {
                                visibleTechText = ""
                            } else {
                                delay(COMPACT_TECH_INFO_SETTLE_MS)
                                visibleTechText = techTextCandidate
                            }
                        }
                        AnimatedContent(
                            targetState = visibleTechText,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                fadeIn(tween(COMPACT_TECH_INFO_FADE_MS)) togetherWith
                                    fadeOut(tween(COMPACT_TECH_INFO_FADE_MS))
                            },
                            label = "compact-tech-info",
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (text.isEmpty()) {
                                            Modifier.clearAndSetSemantics {}
                                        } else {
                                            Modifier
                                        },
                                    ),
                                textAlign = TextAlign.Center,
                                minLines = 1,
                                maxLines = 1,
                            )
                        }
                        // Queue toggle
                        if (showQueueAffordance) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.player_show_queue),
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { showQueueSheet = true },
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = playerSnackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.small,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }

    // Queue sheet renders at the overlay root (outside the status/nav-bar padding) so its scrim
    // covers the full window including the status bar. It owns its own predictive back.
    if (visible && showQueueSheet) {
        PlayerQueueSheet(
            queue = playbackState.queue,
            currentIndex = playbackState.currentIndex,
            serversById = serversById,
            onSkipToQueueItem = onSkipToQueueItem,
            onRemoveQueueItem = onRemoveQueueItem,
            onDismissed = { showQueueSheet = false },
        )
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.common_close))
                }
            },
            title = { Text(track.title) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { DetailLine(stringResource(R.string.detail_artist), track.artist) }
                    item { DetailLine(stringResource(R.string.detail_album), track.album) }
                    item { DetailLine(stringResource(R.string.detail_quality), localizeQualityLabel(track.qualityLabel)) }
                    item { DetailLine(stringResource(R.string.detail_server), currentServer?.name) }
                    item {
                        DetailLine(
                            stringResource(R.string.detail_mode),
                            if (track.isCached) {
                                stringResource(R.string.player_offline)
                            } else {
                                stringResource(R.string.player_streaming)
                            },
                        )
                    }
                    item { DetailLine(stringResource(R.string.detail_source_media), track.sourceMediaInfoLabel()) }
                    if (!track.isCached) {
                        item {
                            DetailLine(
                                stringResource(R.string.detail_requested_quality),
                                track.requestedQualityInfoLabel(localizeQualityLabel(track.qualityLabel)),
                            )
                        }
                    }
                    item { DetailLine(stringResource(R.string.detail_playing_media), track.playingMediaInfoLabel(playbackState.runtimeInfo)) }
                    item { DetailLine(stringResource(R.string.detail_mime_type), playbackState.runtimeInfo?.sampleMimeType ?: track.contentType) }
                    item { DetailLine(stringResource(R.string.detail_container), playbackState.runtimeInfo?.containerMimeType) }
                    item { DetailLine(stringResource(R.string.detail_codec), playbackState.runtimeInfo?.codecs ?: track.suffix?.uppercase(java.util.Locale.ROOT)) }
                    playbackState.runtimeInfo?.averageBitrate?.let { averageBitrate ->
                        item { DetailLine(stringResource(R.string.detail_average_bitrate), formatBitrate(averageBitrate)) }
                    }
                    item { DetailLine(stringResource(R.string.detail_peak_bitrate), playbackState.runtimeInfo?.peakBitrate?.let(::formatBitrate)) }
                    item { DetailLine(stringResource(R.string.detail_sample_rate), (playbackState.runtimeInfo?.sampleRate ?: track.sampleRate)?.let(::formatSampleRate)) }
                    item { DetailLine(stringResource(R.string.detail_channels), playbackState.runtimeInfo?.channelCount?.toString()) }
                    item { DetailLine(stringResource(R.string.detail_language), playbackState.runtimeInfo?.language) }
                    item { DetailLine(stringResource(R.string.detail_cover_art_id), track.coverArtId) }
                    item { DetailLine(stringResource(R.string.detail_local_file), track.localPath) }
                }
            },
        )
    }

    if (showEndpointStatus) {
        AlertDialog(
            onDismissRequest = { showEndpointStatus = false },
            confirmButton = {
                TextButton(onClick = { showEndpointStatus = false }, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.common_close))
                }
            },
            title = { Text(stringResource(R.string.player_endpoint_status)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (endpointProbeResults.isEmpty()) {
                        Text(stringResource(R.string.player_no_probe_results), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        endpointProbeResults.forEach { result ->
                            val isActive = result.id == activeEndpointId
                            Surface(
                                onClick = { onForceEndpoint(result.id) },
                                enabled = result.reachable || (isActive && isEndpointForced),
                                shape = MaterialTheme.shapes.medium,
                                color = when {
                                    isActive -> MaterialTheme.colorScheme.primaryContainer
                                    !result.reachable -> MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = visuals.nowPlayingDisabledEndpointContainerAlpha,
                                    )
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.label + when {
                                                isActive && isEndpointForced -> " 📌"
                                                isActive -> " ✓"
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (!result.reachable) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        Text(
                                            text = result.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (!result.reachable) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Text(
                                        text = if (result.reachable) {
                                            stringResource(R.string.server_config_latency_ms, result.latencyMs ?: 0)
                                        } else {
                                            stringResource(R.string.player_unreachable)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (result.reachable) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onReprobeEndpoints,
                        enabled = !isProbing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            if (isProbing) {
                                stringResource(R.string.player_probing)
                            } else {
                                stringResource(R.string.player_retest_endpoints)
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun NowPlayingProgressSection(
    playbackProgressFlow: StateFlow<PlaybackProgressState>,
    trackId: String,
    isCachedTrack: Boolean,
    sliderActiveColor: Color,
    sliderInactiveColor: Color,
    onArtwork: Color,
    verticalSpacing: Dp,
    onSeekTo: (Long) -> Unit,
) {
    val playbackProgress by playbackProgressFlow.collectAsStateWithLifecycle()
    val duration = playbackProgress.durationMs.coerceAtLeast(1L).toFloat()
    val bufferFraction = if (playbackProgress.durationMs > 0) {
        (playbackProgress.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    // Once the buffer overlay reaches the end, switch to the solid "buffered" style
    // even if the disk cache isn't yet marked fully cached: the streaming overlay
    // (player + cache buffered position) and isStreamCached are different signals.
    val showCachedStyle = isCachedTrack ||
        (playbackProgress.durationMs > 0 && bufferFraction >= 0.999f)
    val sliderColors = if (showCachedStyle) {
        SliderDefaults.colors(
            thumbColor = sliderActiveColor,
            activeTrackColor = sliderActiveColor,
            inactiveTrackColor = sliderInactiveColor,
        )
    } else {
        SliderDefaults.colors(
            thumbColor = sliderActiveColor,
            activeTrackColor = sliderActiveColor,
            inactiveTrackColor = Color.Transparent,
        )
    }
    val bufferColor = sliderActiveColor.copy(alpha = 0.3f)
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var sliderValue by remember(trackId) {
        mutableFloatStateOf(playbackProgress.positionMs.toFloat())
    }
    var isDragging by remember(trackId) { mutableStateOf(false) }

    LaunchedEffect(playbackProgress.positionMs, trackId) {
        if (!isDragging) {
            sliderValue = playbackProgress.positionMs.toFloat()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        Slider(
            value = sliderValue.coerceIn(0f, duration),
            onValueChange = {
                isDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeekTo(sliderValue.roundToLong())
            },
            valueRange = 0f..duration,
            colors = sliderColors,
            modifier = if (!showCachedStyle) {
                Modifier.drawBehind {
                    val trackHeight = 4.dp.toPx()
                    val y = size.height / 2
                    val padding = 6.dp.toPx()
                    val trackWidth = size.width - padding * 2
                    val start = if (isRtl) size.width - padding else padding
                    val end = if (isRtl) padding else size.width - padding
                    drawLine(
                        color = trackBgColor,
                        start = Offset(start, y),
                        end = Offset(end, y),
                        strokeWidth = trackHeight,
                        cap = StrokeCap.Round,
                    )
                    if (bufferFraction > 0f) {
                        val bufferEnd = if (isRtl) {
                            start - trackWidth * bufferFraction
                        } else {
                            start + trackWidth * bufferFraction
                        }
                        drawLine(
                            color = bufferColor,
                            start = Offset(start, y),
                            end = Offset(bufferEnd, y),
                            strokeWidth = trackHeight,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            } else {
                Modifier
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatDuration(sliderValue.roundToLong()), color = onArtwork)
            Text(text = formatDuration(playbackProgress.durationMs), color = onArtwork)
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
) {
    val visuals = SakiTheme.visuals
    if (visuals.nowPlayingSecondaryControlContainerAlpha <= 0f) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(if (compact) 48.dp else 56.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        PressScaleIconButton(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            compact = compact,
            tint = LocalContentColor.current,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = visuals.nowPlayingSecondaryControlContainerAlpha,
            ),
            cornerRadius = visuals.nowPlayingSecondaryControlCornerRadius,
            iconSize = visuals.nowPlayingSecondaryControlIconSize,
        )
    }
}

@Composable
private fun ToggleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val visuals = SakiTheme.visuals
    val hasSelectedContainer = visuals.nowPlayingToggleSelectedContainerAlpha > 0f
    val hasInactiveContainer = visuals.nowPlayingToggleContainerAlpha > 0f
    val onText = stringResource(R.string.common_on)
    val offText = stringResource(R.string.common_off)
    PressScaleIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        compact = compact,
        tint = if (active) {
            (if (hasSelectedContainer) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                LocalContentColor.current
            }).copy(alpha = visuals.nowPlayingToggleSelectedIconAlpha)
        } else {
            LocalContentColor.current.copy(alpha = visuals.nowPlayingToggleIconAlpha)
        },
        containerColor = when {
            active -> MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = visuals.nowPlayingToggleSelectedContainerAlpha,
            )
            hasInactiveContainer -> MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = visuals.nowPlayingToggleContainerAlpha,
            )
            else -> Color.Transparent
        },
        cornerRadius = visuals.nowPlayingSecondaryControlCornerRadius,
        iconSize = visuals.nowPlayingSecondaryControlIconSize,
        role = Role.Switch,
        semanticStateDescription = "$contentDescription: ${if (active) onText else offText}",
    )
}

@Composable
private fun PressScaleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    tint: Color? = null,
    containerColor: Color = Color.Transparent,
    cornerRadius: Dp = 28.dp,
    iconSize: Dp = 24.dp,
    buttonSize: Dp = if (compact) 48.dp else 56.dp,
    containerSize: Dp = buttonSize,
    role: Role = Role.Button,
    semanticStateDescription: String? = null,
    enabled: Boolean = true,
) {
    val baseTint = tint ?: MaterialTheme.colorScheme.onBackground
    val iconTint = if (enabled) {
        baseTint
    } else {
        baseTint.copy(alpha = baseTint.alpha * 0.38f)
    }
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "PressScaleIconButtonPressScale",
    )

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .then(
                if (semanticStateDescription != null) {
                    Modifier.semantics {
                        stateDescription = semanticStateDescription
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(containerSize)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}


@Composable
private fun MetadataLinkRow(
    track: PlaybackQueueItem,
    textStyle: TextStyle,
    linkColor: Color,
    canOpenArtist: (String?) -> Boolean,
    onOpenArtist: (String?) -> Unit,
    onOpenAlbum: () -> Unit,
) {
    val artistLinks = track.artists.ifEmpty {
        listOfNotNull(
            track.artist?.takeIf(String::isNotBlank)?.let { artist ->
                ArtistRef(
                    id = track.artistId.orEmpty(),
                    name = artist,
                )
            },
        )
    }
    val scrollState = rememberScrollState()
    val scrollKey = remember(track.mediaId, artistLinks, track.album, track.albumId) {
        buildString {
            append(track.mediaId)
            artistLinks.forEach { artist ->
                append('|')
                append(artist.id)
                append(':')
                append(artist.name)
            }
            append('|')
            append(track.album.orEmpty())
            append(':')
            append(track.albumId.orEmpty())
        }
    }
    val density = LocalDensity.current
    LaunchedEffect(scrollKey, scrollState.maxValue, density) {
        scrollState.scrollTo(0)
        if (scrollState.maxValue <= 0 || scrollState.maxValue == Int.MAX_VALUE) return@LaunchedEffect
        if (coroutineContext[MotionDurationScale.Key]?.scaleFactor == 0f) return@LaunchedEffect

        delay(METADATA_LINK_SCROLL_EDGE_PAUSE_MS)
        val speedPxPerMs = with(density) {
            METADATA_LINK_SCROLL_SPEED_DP_PER_SECOND.dp.toPx()
        } / 1000f
        while (scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE) {
            val forwardDistance = (scrollState.maxValue - scrollState.value).coerceAtLeast(0)
            if (forwardDistance > 0) {
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = tween(
                        durationMillis = metadataLinkScrollDurationMillis(forwardDistance, speedPxPerMs),
                        easing = LinearEasing,
                    ),
                )
            }
            delay(METADATA_LINK_SCROLL_EDGE_PAUSE_MS)

            val backwardDistance = scrollState.value.coerceAtLeast(0)
            if (backwardDistance > 0) {
                scrollState.animateScrollTo(
                    0,
                    animationSpec = tween(
                        durationMillis = metadataLinkScrollDurationMillis(backwardDistance, speedPxPerMs),
                        easing = LinearEasing,
                    ),
                )
            }
            delay(METADATA_LINK_SCROLL_EDGE_PAUSE_MS)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState, enabled = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artistLinks.forEachIndexed { index, artist ->
                val artistId = artist.id.takeIf(String::isNotBlank)
                val canOpen = canOpenArtist(artistId)
                MetadataLinkText(
                    text = artist.name,
                    style = textStyle,
                    color = if (canOpen) linkColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = canOpen,
                    onClick = { onOpenArtist(artistId) },
                )
                if (index != artistLinks.lastIndex) {
                    Text(
                        text = "/",
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }

            if ((artistLinks.isNotEmpty() || !track.artist.isNullOrBlank()) && !track.album.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(percent = 50),
                        ),
                )
            }
            track.album?.takeIf(String::isNotBlank)?.let { album ->
                MetadataLinkText(
                    text = album,
                    style = textStyle,
                    color = if (track.albumId != null) linkColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = track.albumId != null,
                    onClick = onOpenAlbum,
                )
            }
        }
    }
}

@Composable
private fun MetadataLinkText(
    text: String,
    style: TextStyle,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "MetadataLinkTextPressScale",
    )

    Text(
        text = text,
        style = style,
        color = color,
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

private fun metadataLinkScrollDurationMillis(distancePx: Int, speedPxPerMs: Float): Int {
    if (speedPxPerMs <= 0f) return METADATA_LINK_SCROLL_MIN_DURATION_MS
    return (distancePx / speedPxPerMs)
        .roundToInt()
        .coerceAtLeast(METADATA_LINK_SCROLL_MIN_DURATION_MS)
}

private enum class QueueSheetAnchor { Hidden, Partial, Expanded }

/**
 * Custom anchored queue sheet (issue #317 phase 2).
 *
 * It owns a single [offsetY] (top inset of the sheet, in px) as the one source of truth for the
 * gesture, the drag, the nested scroll and the animations. Because there is no hand-off between a
 * "preview" value and a separate "commit" animation, the predictive back finishes by simply
 * *continuing* from the current offset into the target anchor with a critically-damped
 * (non-bouncy) spring — which is what removes the unnatural rebound on the Expanded -> Partial
 * back commit.
 */
@Composable
private fun PlayerQueueSheet(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int,
    serversById: Map<Long, ServerConfig>,
    onSkipToQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onDismissed: () -> Unit,
) {
    // Critically damped: eases into the anchor, never overshoots -> never rebounds.
    val settleSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeightPx = constraints.maxHeight.toFloat()
        val verticalSpacing = if (maxHeight < 640.dp) 8.dp else 12.dp
        val expandedOffset = with(density) { 56.dp.toPx() }
        val partialOffset = fullHeightPx * 0.5f
        val hiddenOffset = fullHeightPx

        fun offsetFor(anchor: QueueSheetAnchor): Float = when (anchor) {
            QueueSheetAnchor.Expanded -> expandedOffset
            QueueSheetAnchor.Partial -> partialOffset
            QueueSheetAnchor.Hidden -> hiddenOffset
        }

        // Mirror M3 ModalBottomSheet settling: a flick past the velocity threshold moves to the
        // next anchor in the fling direction; otherwise snap to the nearest anchor (positional).
        // The velocity rule is what makes a short one-handed swipe actually expand the sheet.
        val velocityThresholdPx = with(density) { 125.dp.toPx() }
        val anchorOffsets = listOf(
            QueueSheetAnchor.Expanded to expandedOffset,
            QueueSheetAnchor.Partial to partialOffset,
            QueueSheetAnchor.Hidden to hiddenOffset,
        )
        fun settleTarget(value: Float, velocityY: Float): QueueSheetAnchor {
            val expandSide = anchorOffsets.last { it.second <= value }
            val collapseSide = anchorOffsets.first { it.second >= value }
            return when {
                velocityY <= -velocityThresholdPx -> expandSide.first
                velocityY >= velocityThresholdPx -> collapseSide.first
                value - expandSide.second <= collapseSide.second - value -> expandSide.first
                else -> collapseSide.first
            }
        }

        var offsetY by remember { mutableFloatStateOf(hiddenOffset) }
        var settledAnchor by remember { mutableStateOf(QueueSheetAnchor.Hidden) }
        val scope = rememberCoroutineScope()
        var motionJob by remember { mutableStateOf<Job?>(null) }

        fun settleTo(anchor: QueueSheetAnchor) {
            settledAnchor = anchor
            motionJob?.cancel()
            motionJob = scope.launch {
                animate(offsetY, offsetFor(anchor), animationSpec = settleSpec) { value, _ -> offsetY = value }
                if (anchor == QueueSheetAnchor.Hidden) onDismissed()
            }
        }

        // Animate in from the bottom to the half anchor on first show.
        LaunchedEffect(Unit) { settleTo(QueueSheetAnchor.Partial) }

        // Predictive back: Expanded -> Partial -> Hidden, with a live preview that follows the
        // gesture and a non-bouncy commit so the half-screen settle does not rebound.
        PredictiveBackHandler(enabled = settledAnchor != QueueSheetAnchor.Hidden) { events ->
            motionJob?.cancel()
            val start = settledAnchor
            val target = if (start == QueueSheetAnchor.Expanded) QueueSheetAnchor.Partial else QueueSheetAnchor.Hidden
            val from = offsetY
            val to = offsetFor(target)
            try {
                events.collect { event -> offsetY = lerp(from, to, event.progress.coerceIn(0f, 1f)) }
                // Committed: keep going from the current offset into the target anchor.
                settledAnchor = target
                animate(offsetY, to, animationSpec = settleSpec) { value, _ -> offsetY = value }
                if (target == QueueSheetAnchor.Hidden) onDismissed()
            } catch (_: CancellationException) {
                // Cancelled: ease back to where the gesture started.
                animate(offsetY, from, animationSpec = settleSpec) { value, _ -> offsetY = value }
            }
        }

        // Scrim — alpha read deferred into the graphics layer so dragging does not recompose.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = ((hiddenOffset - offsetY) / (hiddenOffset - partialOffset)).coerceIn(0f, 1f) * 0.45f
                }
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { settleTo(QueueSheetAnchor.Hidden) }
                },
        )

        val nestedScrollConnection = remember(expandedOffset, hiddenOffset) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Only user drags move the sheet; ignore momentum/fling so a leftover list
                    // fling can't re-expand or dismiss the sheet during/after a back commit.
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val delta = available.y
                    // Dragging up first expands the sheet until it reaches the top anchor.
                    if (delta < 0f && offsetY > expandedOffset) {
                        motionJob?.cancel()
                        val newOffset = (offsetY + delta).coerceAtLeast(expandedOffset)
                        val consumed = newOffset - offsetY
                        offsetY = newOffset
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val delta = available.y
                    // Dragging down once the list is at the top collapses the sheet.
                    if (delta > 0f) {
                        motionJob?.cancel()
                        val newOffset = (offsetY + delta).coerceAtMost(hiddenOffset)
                        val used = newOffset - offsetY
                        offsetY = newOffset
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offsetY > expandedOffset) {
                        settleTo(settleTarget(offsetY, available.y))
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer { translationY = offsetY }
                .nestedScroll(nestedScrollConnection),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The whole header (handle + title) is the drag grab area so the sheet can be
                // expanded/collapsed one-handed like the M3 ModalBottomSheet, not only via the pill.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = rememberDraggableState { delta ->
                                offsetY = (offsetY + delta).coerceIn(expandedOffset, hiddenOffset)
                            },
                            orientation = Orientation.Vertical,
                            onDragStarted = { motionJob?.cancel() },
                            onDragStopped = { velocity -> settleTo(settleTarget(offsetY, velocity)) },
                        ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .size(width = 32.dp, height = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                    Text(
                        text = stringResource(R.string.player_queue),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                val queueListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = queueListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                    itemsIndexed(queue) { index, item ->
                        QueueRow(
                            item = item,
                            isCurrent = index == currentIndex,
                            currentServer = item.serverId?.let { serversById[it] },
                            onClick = { onSkipToQueueItem(index) },
                            onRemove = { onRemoveQueueItem(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: PlaybackQueueItem,
    isCurrent: Boolean,
    currentServer: ServerConfig?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val visuals = SakiTheme.visuals
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = visuals.nowPlayingQueueSelectedContainerAlpha,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = visuals.nowPlayingQueueContainerAlpha)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkCard(
                model = item.queueArtworkModel(currentServer),
                contentDescription = item.title,
                modifier = Modifier.size(48.dp),
                cornerRadiusDp = 16,
                requestSizePx = THUMBNAIL_COVER_ART_SIZE_PX,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(item.artist, item.album).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isCurrent) {
                TextButton(onClick = onRemove, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.common_remove))
                }
            }
        }
    }
}

@Composable
private fun localizeQualityLabel(label: String): String {
    val resId = when (label) {
        "Original" -> R.string.stream_quality_original
        "320 kbps" -> R.string.stream_quality_320_kbps
        "256 kbps" -> R.string.stream_quality_256_kbps
        "192 kbps" -> R.string.stream_quality_192_kbps
        "160 kbps" -> R.string.stream_quality_160_kbps
        "128 kbps" -> R.string.stream_quality_128_kbps
        "96 kbps" -> R.string.stream_quality_96_kbps
        else -> return label
    }
    return stringResource(resId)
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

private fun PlaybackQueueItem.queueArtworkModel(server: ServerConfig?): Any? {
    return when {
        !coverArtPath.isNullOrBlank() -> File(coverArtPath)
        server != null -> resolveArtworkModel(server, coverArtId, null)
        !artworkUri.isNullOrBlank() -> artworkUri
        else -> null
    }
}

private fun PlaybackQueueItem.artworkIdentityKey(): String {
    return listOf(
        mediaId,
        serverId?.toString().orEmpty(),
        coverArtId.orEmpty(),
        coverArtPath.orEmpty(),
        artworkUri.orEmpty(),
    ).joinToString("|")
}

private data class NowPlayingVisualSnapshot(
    val queue: List<PlaybackQueueItem>,
    val currentIndex: Int,
    val currentTrack: PlaybackQueueItem,
)

@Composable
private fun rememberNowPlayingVisualSnapshot(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int,
    currentTrack: PlaybackQueueItem,
): NowPlayingVisualSnapshot {
    val candidate = remember(queue, currentIndex, currentTrack) {
        buildNowPlayingVisualSnapshot(queue, currentIndex, currentTrack)
    }
    var snapshot by remember {
        mutableStateOf(candidate ?: fallbackNowPlayingVisualSnapshot(queue, currentIndex, currentTrack))
    }

    LaunchedEffect(candidate) {
        if (candidate != null) {
            snapshot = candidate
        }
    }

    return candidate ?: snapshot
}

private fun buildNowPlayingVisualSnapshot(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int,
    currentTrack: PlaybackQueueItem,
): NowPlayingVisualSnapshot? {
    if (currentIndex !in queue.indices) return null
    if (queue[currentIndex].songId != currentTrack.songId) return null
    return NowPlayingVisualSnapshot(
        queue = queue.withVisualCurrentItem(currentIndex, currentTrack),
        currentIndex = currentIndex,
        currentTrack = currentTrack,
    )
}

private fun fallbackNowPlayingVisualSnapshot(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int,
    currentTrack: PlaybackQueueItem,
): NowPlayingVisualSnapshot {
    val safeIndex = currentIndex.takeIf { it in queue.indices }
    if (safeIndex != null && queue[safeIndex].songId == currentTrack.songId) {
        return NowPlayingVisualSnapshot(
            queue = queue.withVisualCurrentItem(safeIndex, currentTrack),
            currentIndex = safeIndex,
            currentTrack = currentTrack,
        )
    }
    return NowPlayingVisualSnapshot(
        queue = listOf(currentTrack),
        currentIndex = 0,
        currentTrack = currentTrack,
    )
}

private fun List<PlaybackQueueItem>.withVisualCurrentItem(
    currentIndex: Int,
    currentTrack: PlaybackQueueItem,
): List<PlaybackQueueItem> {
    val currentItem = getOrNull(currentIndex) ?: return this
    if (currentItem == currentTrack || currentItem.hasSameArtworkVisual(currentTrack)) return this
    return toMutableList().also { it[currentIndex] = currentTrack }
}

private fun PlaybackQueueItem.hasSameArtworkVisual(other: PlaybackQueueItem): Boolean {
    return title == other.title && artworkIdentityKey() == other.artworkIdentityKey()
}

private class NowPlayingArtworkMotionState(initialPosition: Float) {
    var position by mutableFloatStateOf(initialPosition)
    var velocity by mutableFloatStateOf(0f)
    var isScrollInProgress by mutableStateOf(false)
}

@Composable
private fun rememberNowPlayingArtworkMotionState(
    currentIndex: Int,
    visible: Boolean,
): NowPlayingArtworkMotionState {
    return remember(visible) { NowPlayingArtworkMotionState(currentIndex.coerceAtLeast(0).toFloat()) }
}

private data class ArtworkColors(
    val dominant: Color,
    val accent: Color,
)

private data class ArtworkPageRequest(
    val page: Int,
    val sequence: Int,
)

private data class ArtworkPresentationRequest(
    val key: String,
    val model: Any?,
)

@Composable
private fun rememberDisplayedArtworkColors(
    targetColors: ArtworkColors,
    followImmediately: Boolean,
): ArtworkColors {
    var displayedColors by remember { mutableStateOf(targetColors) }

    LaunchedEffect(targetColors, followImmediately) {
        if (followImmediately) {
            displayedColors = targetColors
            return@LaunchedEffect
        }
        if (displayedColors == targetColors) return@LaunchedEffect

        val startColors = displayedColors
        withFixedArtworkMotionDurationScale {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(ARTWORK_BACKGROUND_SETTLE_MS),
            ) { fraction, _ ->
                displayedColors = ArtworkColors(
                    dominant = lerp(startColors.dominant, targetColors.dominant, fraction),
                    accent = lerp(startColors.accent, targetColors.accent, fraction),
                )
            }
        }
    }

    return if (followImmediately) targetColors else displayedColors
}

@Composable
private fun rememberArtworkSeed(model: Any?): Color? {
    val context = LocalContext.current.applicationContext
    var seed by remember(model) { mutableStateOf(model?.cachedArtworkPresentation()?.seedColor) }
    LaunchedEffect(model) {
        val target = model ?: return@LaunchedEffect
        if (seed == null) seed = loadArtworkPresentation(context, target).seedColor
    }
    return seed
}

@Composable
private fun rememberMotionArtworkColors(
    queue: List<PlaybackQueueItem>,
    serversById: Map<Long, ServerConfig>,
    position: Float,
    currentIndex: Int,
    freezePresentationUpdates: Boolean,
    expressive: Boolean,
    isDark: Boolean,
    fallbackDominant: Color,
    fallbackAccent: Color,
    prewarmRadius: Int,
): ArtworkColors {
    if (queue.isEmpty()) {
        return ArtworkColors(fallbackDominant, fallbackAccent)
    }

    val context = LocalContext.current.applicationContext
    var presentations by remember { mutableStateOf<Map<String, ArtworkPresentation>>(emptyMap()) }
    var appliedPresentations by remember { mutableStateOf<Map<String, ArtworkPresentation>>(emptyMap()) }
    // While the queue is reordered under us (e.g. toggling shuffle) the pager
    // position briefly indexes a different song in the new order, which flashes
    // the background gradient. During artwork motion, keep the background fixed
    // too, so the full player does not recompose on every pager offset tick.
    val orderKeys = remember(queue) { queue.map { it.mediaId } }
    val anchor = remember { ReorderColorAnchor() }
    if (anchor.orderKeys != orderKeys) {
        anchor.active = anchor.initialized
        anchor.orderKeys = orderKeys
        anchor.initialized = true
    }
    if (position.roundToInt() == currentIndex) anchor.active = false
    val effectivePosition = if (freezePresentationUpdates || anchor.active) {
        currentIndex.toFloat()
    } else {
        position
    }
    val clampedPosition = effectivePosition.coerceIn(0f, queue.lastIndex.toFloat())
    val fromPage = floor(clampedPosition).toInt().coerceIn(0, queue.lastIndex)
    val toPage = ceil(clampedPosition).toInt().coerceIn(0, queue.lastIndex)
    val fraction = (clampedPosition - fromPage).coerceIn(0f, 1f)
    val centerPage = clampedPosition.roundToInt().coerceIn(0, queue.lastIndex)
    val pageRequests = remember(queue, serversById, centerPage, prewarmRadius) {
        (centerPage - prewarmRadius..centerPage + prewarmRadius)
            .filter { it in queue.indices }
            .associateWith { page ->
                val item = queue[page]
                ArtworkPresentationRequest(
                    key = item.artworkIdentityKey(),
                    model = item.queueArtworkModel(item.serverId?.let { serversById[it] }),
                )
            }
    }

    LaunchedEffect(pageRequests) {
        var loadedPresentations = presentations
        for (request in pageRequests.values) {
            val model = request.model ?: continue
            if (loadedPresentations[request.key]?.hasColors != true) {
                val presentation = loadArtworkPresentation(context, model)
                if (!presentation.hasColors) continue
                loadedPresentations = loadedPresentations + (request.key to presentation)
                presentations = loadedPresentations
            }
        }
    }

    LaunchedEffect(freezePresentationUpdates, presentations) {
        if (!freezePresentationUpdates) {
            appliedPresentations = presentations
        }
    }

    fun colorsFor(page: Int): ArtworkColors {
        val item = queue.getOrNull(page)
        val key = item?.artworkIdentityKey()
        val cachedPresentation = item
            ?.queueArtworkModel(item.serverId?.let { serversById[it] })
            ?.cachedArtworkPresentation()
        val presentation = key
            ?.let { appliedPresentations[it]?.takeIf { presentation -> presentation.hasColors } }
            ?: cachedPresentation?.takeIf { presentation -> presentation.hasColors }
            ?: ArtworkPresentation()
        if (expressive) {
            presentation.seedColor?.let { return expressiveArtworkColors(it, isDark) }
        }
        return ArtworkColors(
            dominant = presentation.dominantColor ?: fallbackDominant,
            accent = presentation.accentColor ?: fallbackAccent,
        )
    }

    val fromColors = colorsFor(fromPage)
    if (fromPage == toPage) return fromColors
    val toColors = colorsFor(toPage)
    return ArtworkColors(
        dominant = lerp(fromColors.dominant, toColors.dominant, fraction),
        accent = lerp(fromColors.accent, toColors.accent, fraction),
    )
}

private data class ArtworkPresentation(
    val dominantColor: Color? = null,
    val accentColor: Color? = null,
    val seedColor: Color? = null,
) {
    val hasColors: Boolean
        get() = dominantColor != null || accentColor != null || seedColor != null
}

// Material Expressive: map the artwork seed to tonal roles via HCT. Chroma is clamped
// (calmer than the raw swatch, vividness retained) and tones are fixed so contrast
// between the accent, the background tint and on-surface text holds for any hue.
private fun expressiveArtworkColors(seed: Color, isDark: Boolean): ArtworkColors {
    val hct = Hct.fromInt(seed.toArgb())
    val accent = Hct.from(hct.hue, hct.chroma.coerceIn(32.0, 64.0), if (isDark) 80.0 else 44.0)
    val base = Hct.from(hct.hue, hct.chroma.coerceIn(8.0, 20.0), if (isDark) 26.0 else 92.0)
    return ArtworkColors(dominant = Color(base.toInt()), accent = Color(accent.toInt()))
}

private class ReorderColorAnchor {
    var orderKeys: List<String> = emptyList()
    var active = false
    var initialized = false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingArtworkPagerHost(
    queue: List<PlaybackQueueItem>,
    currentIndex: Int,
    currentTrack: PlaybackQueueItem,
    serversById: Map<Long, ServerConfig>,
    motionState: NowPlayingArtworkMotionState,
    visualSkipRequest: ArtworkPageRequest?,
    useProgrammaticMotion: Boolean,
    useArtworkBackdrop: Boolean,
    modifier: Modifier = Modifier,
    onArtworkClick: () -> Unit,
    onUserSelectQueueItem: (Int) -> Unit,
) {
    val targetPage = currentIndex.coerceAtLeast(0)
    val requestedVisualPage = visualSkipRequest?.page?.takeIf { it in queue.indices }
    val visualTargetPage = requestedVisualPage ?: targetPage
    val visualSkipSequence = visualSkipRequest?.sequence
    val queueIdentity = remember(queue) { queue.map { it.artworkIdentityKey() } }
    val latestOnArtworkClick by rememberUpdatedState(onArtworkClick)
    val latestOnUserSelectQueueItem by rememberUpdatedState(onUserSelectQueueItem)
    var stableQueue by remember { mutableStateOf(queue) }
    val artworkPagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { stableQueue.size.coerceAtLeast(1) },
    )

    var lastTrackId by remember { mutableStateOf(currentTrack.songId) }
    // Programmatic sync keeps pager state aligned but never drives playback.
    // Any other settled page change comes from the user's pager gesture.
    var programmaticPagerSync by remember { mutableStateOf(false) }
    var lastProgrammaticSettledPage by remember { mutableStateOf<Int?>(null) }
    var lastPlaybackTargetPage by remember { mutableStateOf(targetPage) }

    // Stabilize artwork during deferred queue expansion:
    // update the page count first, then move after any insertion before the
    // current item. Track changes use the pager's own animation so there is
    // only one artwork render path and no overlay handoff frame.
    LaunchedEffect(
        visualTargetPage,
        visualSkipSequence,
        currentTrack.songId,
        queueIdentity,
        useProgrammaticMotion,
    ) {
        val expectedPage = lastPlaybackTargetPage
        val isLocalVisualSkip = requestedVisualPage != null && requestedVisualPage != targetPage
        if (isLocalVisualSkip && artworkPagerState.currentPage != visualTargetPage) {
            stableQueue = queue
            programmaticPagerSync = true
            try {
                val distancePages = abs(
                    visualTargetPage - (artworkPagerState.currentPage + artworkPagerState.currentPageOffsetFraction),
                )
                artworkPagerState.moveArtworkMotionToPage(
                    page = visualTargetPage,
                    motionState = motionState,
                    distancePages = distancePages,
                    useProgrammaticMotion = useProgrammaticMotion,
                    velocityBoostPagesPerSecond = if (visualTargetPage > artworkPagerState.currentArtworkPosition()) {
                        ARTWORK_BUTTON_SKIP_INITIAL_VELOCITY_PAGES
                    } else {
                        -ARTWORK_BUTTON_SKIP_INITIAL_VELOCITY_PAGES
                    },
                )
            } finally {
                lastProgrammaticSettledPage = artworkPagerState.settledPage
                programmaticPagerSync = false
            }
        }
        if (!isLocalVisualSkip && currentTrack.songId == lastTrackId && artworkPagerState.currentPage != targetPage) {
            val userMovedBeforeStabilize =
                artworkPagerState.currentPage != expectedPage ||
                    artworkPagerState.settledPage != expectedPage
            stableQueue = queue
            withFrameNanos { }
            while (artworkPagerState.isScrollInProgress) {
                withFrameNanos { }
            }
            val userMovedPager =
                userMovedBeforeStabilize ||
                    artworkPagerState.currentPage != expectedPage ||
                    artworkPagerState.settledPage != expectedPage
            if (!userMovedPager && artworkPagerState.currentPage != targetPage) {
                programmaticPagerSync = true
                try {
                    artworkPagerState.scrollToPage(targetPage)
                    motionState.position = targetPage.toFloat()
                    withFrameNanos { }
                } finally {
                    lastProgrammaticSettledPage = artworkPagerState.settledPage
                    programmaticPagerSync = false
                }
            } else {
                stableQueue = queue
                val selectedPage = artworkPagerState.settledPage
                if (selectedPage != targetPage && selectedPage in queue.indices) {
                    lastProgrammaticSettledPage = null
                    latestOnUserSelectQueueItem(selectedPage)
                }
            }
        } else {
            stableQueue = queue
        }
        if (!isLocalVisualSkip && currentTrack.songId != lastTrackId && artworkPagerState.currentPage != targetPage) {
            stableQueue = queue
            withFrameNanos { }
            programmaticPagerSync = true
            try {
                val distancePages = abs(
                    targetPage - (artworkPagerState.currentPage + artworkPagerState.currentPageOffsetFraction),
                )
                artworkPagerState.moveArtworkMotionToPage(
                    page = targetPage,
                    motionState = motionState,
                    distancePages = distancePages,
                    useProgrammaticMotion = useProgrammaticMotion,
                )
            } finally {
                lastProgrammaticSettledPage = artworkPagerState.settledPage
                programmaticPagerSync = false
            }
        }
        lastTrackId = currentTrack.songId
        lastPlaybackTargetPage = targetPage
    }

    LaunchedEffect(artworkPagerState, motionState) {
        snapshotFlow {
            val maxPage = (artworkPagerState.pageCount - 1).coerceAtLeast(0)
            val position = (
                artworkPagerState.currentPage +
                    artworkPagerState.currentPageOffsetFraction
                ).coerceIn(0f, maxPage.toFloat())
            position to artworkPagerState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { (position, isScrollInProgress) ->
                motionState.position = position
                motionState.isScrollInProgress = isScrollInProgress
            }
    }

    val currentPlaybackIndex by rememberUpdatedState(currentIndex)
    val currentQueueSize by rememberUpdatedState(queue.size)
    LaunchedEffect(artworkPagerState) {
        snapshotFlow { artworkPagerState.settledPage to programmaticPagerSync }
            .distinctUntilChanged()
            .collect { (page, isProgrammatic) ->
                if (isProgrammatic) {
                    lastProgrammaticSettledPage = page
                    return@collect
                }
                if (lastProgrammaticSettledPage == page) {
                    lastProgrammaticSettledPage = null
                    return@collect
                }
                if (page != currentPlaybackIndex && page in 0 until currentQueueSize) {
                    latestOnUserSelectQueueItem(page)
                }
            }
    }

    // Content-stable, unique key per slot: keep the page index OUT of the key so
    // reordering (e.g. shuffle) moves the existing artwork composable instead of
    // recreating it (which would reload the image and flash). The occurrence count
    // disambiguates duplicate mediaIds.
    val queueKeyOccurrence = remember(stableQueue) {
        val seen = HashMap<String, Int>()
        IntArray(stableQueue.size) { i ->
            val id = stableQueue[i].mediaId
            (seen[id] ?: 0).also { seen[id] = it + 1 }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = artworkPagerState,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(34.dp)),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            key = { page ->
                stableQueue.getOrNull(page)?.let { "queue-${it.mediaId}-${queueKeyOccurrence.getOrElse(page) { 0 }}" } ?: "empty-$page"
            },
        ) { page ->
            NowPlayingArtworkFrame(
                item = stableQueue.getOrNull(page),
                serversById = serversById,
                showBackdrop = useArtworkBackdrop && !motionState.isScrollInProgress,
                modifier = Modifier.fillMaxSize(),
                onClick = { latestOnArtworkClick() },
            )
        }
    }
}

@Composable
private fun NowPlayingArtworkFrame(
    item: PlaybackQueueItem?,
    serversById: Map<Long, ServerConfig>,
    showBackdrop: Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val clickInteractionSource = remember { MutableInteractionSource() }
        val clickModifier = if (onClick != null) {
            Modifier.clickable(
                interactionSource = clickInteractionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        }
        val artworkModel = item?.queueArtworkModel(item.serverId?.let { serversById[it] })
        val frameModifier = contentModifier
            .aspectRatio(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(34.dp))
            .then(clickModifier)
        if (artworkModel != null) {
            NowPlayingLayeredArtwork(
                model = artworkModel,
                contentDescription = item?.title,
                showBackdrop = showBackdrop,
                modifier = frameModifier,
            )
        } else {
            ArtworkCard(
                model = null,
                contentDescription = item?.title,
                modifier = frameModifier,
                cornerRadiusDp = 34,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun NowPlayingLayeredArtwork(
    model: Any,
    contentDescription: String?,
    showBackdrop: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val visuals = SakiTheme.visuals
    val imageRequest = remember(model, context) {
        ImageRequest.Builder(context)
            .data(model)
            .size(FULL_COVER_ART_SIZE_PX)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = imageRequest)
    val backdropRenderEffect = remember(showBackdrop) {
        if (showBackdrop && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect.createBlurEffect(
                NOW_PLAYING_ARTWORK_BACKDROP_BLUR_RADIUS_PX,
                NOW_PLAYING_ARTWORK_BACKDROP_BLUR_RADIUS_PX,
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        } else {
            null
        }
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = visuals.nowPlayingArtworkBackdropContainerAlpha,
            ),
        ),
    ) {
        if (showBackdrop && backdropRenderEffect != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = NOW_PLAYING_ARTWORK_BACKDROP_SCALE
                        scaleY = NOW_PLAYING_ARTWORK_BACKDROP_SCALE
                        renderEffect = backdropRenderEffect
                    },
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = visuals.nowPlayingArtworkBackdropOverlayAlpha)),
            )
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

private const val ARTWORK_PRESENTATION_CACHE_ENTRIES = 64
private const val ARTWORK_PREWARM_RADIUS_PAGES = 3
private const val ARTWORK_SCORE_SAMPLE_COUNT = 16384
private const val ARTWORK_BACKGROUND_SETTLE_MS = 180
private const val ARTWORK_BUTTON_SKIP_CONFIRM_TIMEOUT_MS = 900L
private const val ARTWORK_BUTTON_SKIP_INITIAL_VELOCITY_PAGES = 3.5f
private const val COMPACT_TECH_INFO_SETTLE_MS = 700L
private const val COMPACT_TECH_INFO_FADE_MS = 700
private const val KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS = 1_200L
private const val METADATA_LINK_SCROLL_EDGE_PAUSE_MS = 1_200L
private const val METADATA_LINK_SCROLL_MIN_DURATION_MS = 350
private const val METADATA_LINK_SCROLL_SPEED_DP_PER_SECOND = 32f
private const val NOW_PLAYING_ARTWORK_BACKDROP_SCALE = 1.1f
private const val NOW_PLAYING_ARTWORK_BACKDROP_BLUR_RADIUS_PX = 60f
private const val PROGRAMMATIC_ARTWORK_SPRING_BASE_STIFFNESS = 140f
private const val PROGRAMMATIC_ARTWORK_SPRING_DISTANCE_STIFFNESS = 60f
private const val PROGRAMMATIC_ARTWORK_MAX_INITIAL_VELOCITY_PAGES = 8f
private val artworkPresentationCache = LruCache<String, ArtworkPresentation>(ARTWORK_PRESENTATION_CACHE_ENTRIES)

private object FixedArtworkMotionDurationScale : MotionDurationScale {
    override val key: CoroutineContext.Key<*> = MotionDurationScale.Key
    override val scaleFactor: Float = 1f
}

private suspend fun withFixedArtworkMotionDurationScale(block: suspend () -> Unit) {
    if (coroutineContext[MotionDurationScale.Key]?.scaleFactor == 0f) {
        block()
    } else {
        withContext(FixedArtworkMotionDurationScale) {
            block()
        }
    }
}

private suspend fun PagerState.moveArtworkMotionToPage(
    page: Int,
    motionState: NowPlayingArtworkMotionState,
    distancePages: Float,
    useProgrammaticMotion: Boolean,
    velocityBoostPagesPerSecond: Float = 0f,
) {
    if (useProgrammaticMotion) {
        withFixedArtworkMotionDurationScale {
            animateArtworkMotionToPage(
                page = page,
                motionState = motionState,
                distancePages = distancePages,
                velocityBoostPagesPerSecond = velocityBoostPagesPerSecond,
            )
        }
    } else {
        val safePageCount = pageCount.coerceAtLeast(1)
        val targetPage = page.coerceIn(0, safePageCount - 1)
        scrollToPage(targetPage)
        motionState.position = targetPage.toFloat()
        motionState.velocity = 0f
    }
}

private suspend fun PagerState.animateArtworkMotionToPage(
    page: Int,
    motionState: NowPlayingArtworkMotionState,
    distancePages: Float,
    velocityBoostPagesPerSecond: Float = 0f,
) {
    val safePageCount = pageCount.coerceAtLeast(1)
    val targetPage = page.coerceIn(0, safePageCount - 1)
    val targetPosition = targetPage.toFloat()
    val startPosition = currentArtworkPosition(safePageCount)
    val inheritedVelocity = motionState.velocity
        .takeUnless { it.isNaN() || it.isInfinite() }
        ?: 0f
    val boostedVelocity = when {
        velocityBoostPagesPerSecond == 0f -> inheritedVelocity
        inheritedVelocity * velocityBoostPagesPerSecond > 0f -> inheritedVelocity + velocityBoostPagesPerSecond
        else -> velocityBoostPagesPerSecond
    }
    val startVelocity = boostedVelocity
        .coerceIn(
            -PROGRAMMATIC_ARTWORK_MAX_INITIAL_VELOCITY_PAGES,
            PROGRAMMATIC_ARTWORK_MAX_INITIAL_VELOCITY_PAGES,
        )
    val pagerState = this
    var previousPosition = startPosition

    scroll {
        updateTargetPage(targetPage)
        animate(
            initialValue = startPosition,
            targetValue = targetPosition,
            initialVelocity = startVelocity,
            animationSpec = programmaticArtworkScrollSpec(distancePages),
        ) { value, velocity ->
            val position = value.coerceIn(0f, (safePageCount - 1).toFloat())
            val deltaPx = (position - previousPosition) * pagerState.artworkPageDistancePx()
            if (deltaPx != 0f) {
                scrollBy(deltaPx)
            }
            previousPosition = position
            motionState.position = pagerState.currentArtworkPosition(safePageCount)
            motionState.velocity = velocity
        }
        val remainingPx = (targetPosition - pagerState.currentArtworkPosition(safePageCount)) *
            pagerState.artworkPageDistancePx()
        if (abs(remainingPx) > 0.5f) {
            scrollBy(remainingPx)
        }
        motionState.position = targetPosition
        motionState.velocity = 0f
    }
}

private fun PagerState.currentArtworkPosition(pageCount: Int = this.pageCount.coerceAtLeast(1)): Float {
    return (currentPage + currentPageOffsetFraction)
        .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
}

private fun PagerState.artworkPageDistancePx(): Float {
    val visiblePages = layoutInfo.visiblePagesInfo.sortedBy { page -> page.index }
    val adjacentPages = visiblePages
        .zipWithNext()
        .firstOrNull { (first, second) -> second.index == first.index + 1 }
    val measuredDistance = adjacentPages?.let { (first, second) ->
        abs(second.offset - first.offset).toFloat()
    }
    return (measuredDistance ?: layoutInfo.pageSize.toFloat()).coerceAtLeast(1f)
}

private fun programmaticArtworkScrollSpec(distancePages: Float) = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = (
        PROGRAMMATIC_ARTWORK_SPRING_BASE_STIFFNESS +
            distancePages.coerceIn(0f, 3f) * PROGRAMMATIC_ARTWORK_SPRING_DISTANCE_STIFFNESS
        ),
)

private suspend fun loadArtworkPresentation(
    context: android.content.Context,
    model: Any?,
): ArtworkPresentation {
    val key = model?.artworkPresentationCacheKey() ?: return ArtworkPresentation()
    artworkPresentationCache.get(key)?.let { return it }
    val presentation = decodeArtworkPresentation(context, model)
    if (presentation.hasColors) {
        artworkPresentationCache.put(key, presentation)
    }
    return presentation
}

private suspend fun prewarmArtworkPresentation(
    context: android.content.Context,
    model: Any?,
) {
    loadArtworkPresentation(context, model)
}

private fun Any.artworkPresentationCacheKey(): String {
    return when (this) {
        is File -> "file:$absolutePath"
        else -> "model:${this}"
    }
}

private fun Any.cachedArtworkPresentation(): ArtworkPresentation? {
    return artworkPresentationCache.get(artworkPresentationCacheKey())
}

private suspend fun decodeArtworkPresentation(
    context: android.content.Context,
    model: Any,
): ArtworkPresentation = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(PALETTE_COVER_ART_SIZE_PX)
            .allowHardware(false)
            .build()
        val image = context.imageLoader.execute(request).image
            ?: return@withContext ArtworkPresentation()
        val bitmap = image.toBitmap()

        // Non-Expressive themes use the original Palette swatches (dominant + vibrant) at the
        // original decode size, so their colors stay unchanged. Expressive additionally derives
        // a seed via HCT quantize + Score (the Android 12 wallpaper-color algorithm): balances
        // pixel population with chroma and filters near-grey / disliked hues. Score runs on a
        // subsample (rather than a smaller decode) to keep quantize cost low; the fallback color
        // is disabled so a fully-filtered (neutral) cover yields no seed and falls back to theme.
        val palette = Palette.from(bitmap).clearFilters().generate()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val step = (pixels.size / ARTWORK_SCORE_SAMPLE_COUNT).coerceAtLeast(1)
        val sampled = if (step <= 1) pixels else IntArray(pixels.size / step) { pixels[it * step] }
        val seed = Score.score(QuantizerCelebi.quantize(sampled, 64), 4, null, true)
            .firstOrNull()?.let(::Color)
        ArtworkPresentation(
            dominantColor = palette.getDominantColor(0).takeIf { it != 0 }?.let(::Color),
            accentColor = palette.getVibrantColor(0).takeIf { it != 0 }?.let(::Color),
            seedColor = seed,
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        ArtworkPresentation()
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hours = minutes / 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes % 60L, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatBitrate(bitrate: Int): String {
    return if (bitrate >= 1_000_000) {
        "%.2f Mbps".format(bitrate / 1_000_000f)
    } else {
        "%.0f kbps".format(bitrate / 1_000f)
    }
}

private fun PlaybackQueueItem.compactTechnicalInfoParts(runtimeInfo: PlaybackRuntimeInfo?): List<String> {
    val showSourceTechnicalInfo = shouldShowSourceTechnicalInfo()
    val stableFormat = stableFormatLabel().takeIf { showSourceTechnicalInfo }
    val stableSampleRate = sampleRate?.let(::formatSampleRateShort).takeIf { showSourceTechnicalInfo }
    return listOfNotNull(
        runtimeInfo?.formatLabel() ?: stableFormat,
        runtimeInfo?.sampleRate?.let(::formatSampleRateShort) ?: stableSampleRate,
        runtimeInfo?.averageBitrate?.let(::formatBitrate) ?: stableBitrateDisplay()?.label(),
    )
}

private fun PlaybackQueueItem.sourceMediaInfoLabel(): String? {
    return listOfNotNull(
        stableFormatLabel(),
        sampleRate?.let(::formatSampleRateShort),
        sourceBitRateKbps?.takeIf { bitrate -> bitrate > 0 }?.let(::formatKbps),
    ).joinNonEmpty()
}

private fun PlaybackQueueItem.requestedQualityInfoLabel(localizedQualityLabel: String): String? {
    val requestedMaxBitRate = requestedMaxBitRateKbps?.takeIf { bitrate -> bitrate > 0 }
    return requestedMaxBitRate?.let { bitrate -> "<=${formatKbps(bitrate)}" } ?: localizedQualityLabel
}

private fun PlaybackQueueItem.playingMediaInfoLabel(runtimeInfo: PlaybackRuntimeInfo?): String? {
    val showSourceTechnicalInfo = shouldShowSourceTechnicalInfo()
    return listOfNotNull(
        runtimeInfo?.formatLabel() ?: stableFormatLabel().takeIf { showSourceTechnicalInfo },
        runtimeInfo?.sampleRate?.let(::formatSampleRateShort)
            ?: sampleRate?.let(::formatSampleRateShort).takeIf { showSourceTechnicalInfo },
        runtimeInfo?.averageBitrate?.let(::formatBitrate) ?: stableBitrateDisplay()?.label(),
    ).joinNonEmpty()
}

private fun PlaybackQueueItem.shouldShowSourceTechnicalInfo(): Boolean {
    if (isCached) return true
    val requestedMaxBitRate = requestedMaxBitRateKbps?.takeIf { bitrate -> bitrate > 0 }
        ?: return true
    val sourceBitRate = sourceBitRateKbps?.takeIf { bitrate -> bitrate > 0 } ?: return false
    return sourceBitRate <= requestedMaxBitRate
}

private fun PlaybackQueueItem.stableBitrateDisplay(): BitrateDisplay? {
    val requestedMaxBitRate = requestedMaxBitRateKbps?.takeIf { bitrate -> bitrate > 0 }
    val sourceBitRate = sourceBitRateKbps?.takeIf { bitrate -> bitrate > 0 }
    val displayBitRate = bitRateKbps?.takeIf { bitrate -> bitrate > 0 }
    if (isCached || requestedMaxBitRate == null) {
        return (displayBitRate ?: sourceBitRate)?.let { bitrate -> BitrateDisplay(bitrate) }
    }
    if (sourceBitRate != null && sourceBitRate <= requestedMaxBitRate) {
        return BitrateDisplay(sourceBitRate)
    }
    return BitrateDisplay(requestedMaxBitRate, isUpperBound = true)
}

private data class BitrateDisplay(
    val kbps: Int,
    val isUpperBound: Boolean = false,
) {
    fun label(): String = if (isUpperBound) "<=${formatKbps(kbps)}" else formatKbps(kbps)
}

private fun formatKbps(kbps: Int): String = "$kbps kbps"

private fun PlaybackRuntimeInfo.formatLabel(): String? {
    return sampleMimeType?.mediaFormatLabel()
        ?: containerMimeType?.mediaFormatLabel()
        ?: codecs?.takeIf(String::isNotBlank)
}

private fun PlaybackRuntimeInfo.hasCompactTechnicalInfo(): Boolean {
    return formatLabel() != null || sampleRate != null || averageBitrate != null
}

private fun List<String>.joinNonEmpty(): String? {
    return takeIf { parts -> parts.isNotEmpty() }?.joinToString(" | ")
}

private fun PlaybackQueueItem.stableFormatLabel(): String? {
    suffix?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { return it.uppercase(java.util.Locale.ROOT) }

    return contentType?.mediaFormatLabel()
}

private fun String.mediaFormatLabel(): String? {
    return substringAfter("/")
        .substringBefore(";")
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { subtype ->
            when (subtype.lowercase(java.util.Locale.ROOT)) {
                "mpeg", "mp3" -> "MP3"
                "mp4", "x-m4a", "m4a" -> "M4A"
                "mp4a-latm", "aac" -> "AAC"
                "flac" -> "FLAC"
                "ogg", "oga" -> "OGG"
                "opus" -> "Opus"
                "vorbis" -> "Vorbis"
                "wav", "x-wav" -> "WAV"
                else -> subtype.uppercase(java.util.Locale.ROOT)
            }
        }
}

private fun formatSampleRateShort(sampleRate: Int): String {
    if (sampleRate < 1_000) return "$sampleRate Hz"
    val khz = sampleRate / 1_000.0
    return if (sampleRate % 1_000 == 0) {
        "${sampleRate / 1_000} kHz"
    } else {
        "%.1f kHz".format(khz)
    }
}

private fun formatSampleRate(sampleRate: Int): String = "$sampleRate Hz"

@Composable
private fun SyncedLyricsView(
    lyrics: SongLyrics,
    playbackProgressFlow: StateFlow<PlaybackProgressState>,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val playbackProgress by playbackProgressFlow.collectAsStateWithLifecycle()
    val positionMs = playbackProgress.positionMs
    val lines = lyrics.lines
    val hasWordLevelTiming = lyrics.synced && lines.any { it.words != null }
    var lyricPositionMs by remember(lyrics) { mutableLongStateOf(positionMs) }

    LaunchedEffect(positionMs, isPlaying, lyrics) {
        val deltaFromLyricPosition = positionMs - lyricPositionMs
        lyricPositionMs = when {
            !isPlaying -> positionMs
            deltaFromLyricPosition > KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS -> positionMs
            deltaFromLyricPosition < -KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS -> positionMs
            else -> maxOf(lyricPositionMs, positionMs)
        }
    }

    val activeIndex = if (lyrics.synced) {
        lines.activeLyricLineIndex(lyricPositionMs)
    } else {
        -1
    }

    val initialActiveIndex = remember(lyrics) {
        if (lyrics.synced) {
            lines.activeLyricLineIndex(lyricPositionMs).coerceAtLeast(0)
        } else {
            0
        }
    }
    val lyricsListState = rememberLazyListState(initialFirstVisibleItemIndex = initialActiveIndex)
    var hasPositionedInitialLine by remember(lyrics) { mutableStateOf(false) }
    val density = LocalDensity.current

    if (lyrics.synced && activeIndex >= 0) {
        LaunchedEffect(activeIndex, isPlaying) {
            val offsetPx = with(density) { 80.dp.roundToPx() }
            when {
                !hasPositionedInitialLine -> {
                    hasPositionedInitialLine = true
                    lyricsListState.scrollToItem(activeIndex, -offsetPx)
                }
                isPlaying -> {
                    lyricsListState.animateScrollToItem(
                        index = activeIndex,
                        scrollOffset = -offsetPx,
                    )
                }
            }
        }
    }

    LazyColumn(
        state = lyricsListState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            val style = if (isActive) {
                MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
            } else {
                MaterialTheme.typography.bodyMedium
            }
            val activeColor = if (isActive) textColor else textColor.copy(alpha = 0.45f)
            val dimColor = textColor.copy(alpha = 0.45f)

            if (isActive && line.words != null) {
                val lineEndMs = lines.getOrNull(index + 1)?.startMs ?: (line.words.last().startMs + 1000)
                val lineDurationMs = lineEndMs - line.startMs
                if (lineDurationMs > 0) {
                    KaraokeLyricLine(
                        text = line.text,
                        lineStartMs = line.startMs,
                        lineDurationMs = lineDurationMs,
                        positionMs = positionMs,
                        isPlaying = isPlaying && hasWordLevelTiming,
                        style = style,
                        textColor = textColor,
                        dimColor = dimColor,
                        onClick = if (lyrics.synced && line.startMs >= 0) {
                            {
                                onSeekTo(line.startMs)
                            }
                        } else {
                            null
                        },
                    )
                } else {
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = style,
                        color = activeColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (lyrics.synced && line.startMs >= 0) {
                                    Modifier.clickable {
                                        onSeekTo(line.startMs)
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 4.dp),
                    )
                }
            } else {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = style,
                    color = activeColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (lyrics.synced && line.startMs >= 0) {
                                Modifier.clickable {
                                    onSeekTo(line.startMs)
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun KaraokeLyricLine(
    text: String,
    lineStartMs: Long,
    lineDurationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    style: TextStyle,
    textColor: Color,
    dimColor: Color,
    onClick: (() -> Unit)?,
) {
    // Keep per-frame karaoke interpolation scoped to the active row. Do not key the
    // interpolator by every player progress tick: those ticks are only sampled every
    // ~500ms and can arrive behind the locally interpolated value, which makes the
    // highlight visibly jump backward. Normal playback stays monotonic; large
    // discontinuities still snap to the player position for seek/track changes.
    var smoothPositionMs by remember(lineStartMs) { mutableLongStateOf(positionMs) }
    val latestPositionMs = rememberUpdatedState(positionMs)

    LaunchedEffect(positionMs, isPlaying, lineStartMs) {
        val deltaFromSmooth = positionMs - smoothPositionMs
        when {
            !isPlaying -> smoothPositionMs = positionMs
            deltaFromSmooth > KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS -> smoothPositionMs = positionMs
            deltaFromSmooth < -KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS -> smoothPositionMs = positionMs
            positionMs > smoothPositionMs -> smoothPositionMs = positionMs
        }
    }

    LaunchedEffect(isPlaying, lineStartMs) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val delta = (now - lastFrame) / 1_000_000
            lastFrame = now
            val playerPositionMs = latestPositionMs.value
            val predictedPositionMs = smoothPositionMs + delta
            smoothPositionMs = when {
                playerPositionMs > predictedPositionMs + KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS -> playerPositionMs
                playerPositionMs < smoothPositionMs - KARAOKE_PROGRESS_CORRECTION_THRESHOLD_MS -> playerPositionMs
                else -> maxOf(predictedPositionMs, playerPositionMs)
            }
        }
    }

    val progress = ((smoothPositionMs - lineStartMs).toFloat() / lineDurationMs.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(text = text, style = style, color = dimColor)
        Text(
            text = text,
            style = style,
            color = textColor,
            modifier = Modifier.drawWithContent {
                clipRect(right = size.width * progress) { this@drawWithContent.drawContent() }
            },
        )
    }
}

private fun List<org.hdhmc.saki.domain.model.LyricLine>.activeLyricLineIndex(positionMs: Long): Int {
    if (isEmpty()) return -1
    var low = 0
    var high = lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].startMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result.coerceAtLeast(0)
}
