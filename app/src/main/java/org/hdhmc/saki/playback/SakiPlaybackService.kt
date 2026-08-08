package org.hdhmc.saki.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import org.hdhmc.saki.MainActivity
import org.hdhmc.saki.R
import org.hdhmc.saki.data.remote.HTTP_USER_AGENT
import org.hdhmc.saki.data.remote.NetworkType
import org.hdhmc.saki.domain.model.BufferStrategy
import org.hdhmc.saki.domain.model.LyricLine
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshot
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshotSource
import org.hdhmc.saki.domain.model.LocalPlayQueueSnapshotSourceType
import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.PlaybackFailureKind
import org.hdhmc.saki.domain.model.OriginalPlaybackFailureAction
import org.hdhmc.saki.domain.model.ServerEndpoint
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.SongLyrics
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.normalizeBluetoothLyricsOffsetMs
import org.hdhmc.saki.domain.repository.LocalPlayQueueRepository
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.InterruptedIOException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import org.hdhmc.saki.decoder.alac.BundledAlacAudioRenderer

private const val CUSTOM_PLAYER_MAX_BUFFER_MS = 5 * 60 * 1_000
private const val STREAM_PREFETCH_LOG_TAG = "SakiStreamPrefetch"
private const val PLAYBACK_RECOVERY_LOG_TAG = "SakiPlaybackRecovery"
private const val STREAM_PREFETCH_REEVALUATION_MS = 30_000L
private const val STREAM_PREFETCH_RETRY_DELAY_MS = 30_000L
private const val BLUETOOTH_LYRICS_MIN_POLL_DELAY_MS = 50L
private const val BLUETOOTH_LYRICS_MAX_POLL_DELAY_MS = 500L
private const val PLAYBACK_FAILURE_REPORT_DELAY_MS = 750L
private const val AUTO_TRANSCODE_FORMAT = "mp3"

private fun Long.coerceKnownDuration(): Long? {
    return takeIf { it != C.TIME_UNSET && it > 0L }
}

private fun Long.toWholeSecondOffsetMs(): Long =
    coerceAtLeast(0L).div(1_000L).times(1_000L)

private fun String.forStreamOffset(timeOffsetSeconds: Int?): String =
    timeOffsetSeconds?.let { offset -> "$this|seek=$offset" } ?: this

private fun Int.forStreamOffset(timeOffsetSeconds: Int?): Int =
    if (timeOffsetSeconds == null) this else this or DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN

@UnstableApi
internal class NavigationPreservingTimeline(
    contentTimeline: Timeline,
    private val navigationTimeline: Timeline,
) : ForwardingTimeline(contentTimeline) {
    init {
        require(contentTimeline.windowCount == navigationTimeline.windowCount) {
            "Content and navigation timelines must have the same window count"
        }
    }

    override fun getNextWindowIndex(
        windowIndex: Int,
        repeatMode: Int,
        shuffleModeEnabled: Boolean,
    ): Int = navigationTimeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getPreviousWindowIndex(
        windowIndex: Int,
        repeatMode: Int,
        shuffleModeEnabled: Boolean,
    ): Int = navigationTimeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
        navigationTimeline.getFirstWindowIndex(shuffleModeEnabled)

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int =
        navigationTimeline.getLastWindowIndex(shuffleModeEnabled)
}
private data class ActiveServerSeek(
    val mediaItemIndex: Int,
    val serverId: Long,
    val songId: String,
    val offsetMs: Long,
    val streamQuality: StreamQuality,
)

private data class ForcedTranscode(
    val serverId: Long,
    val songId: String,
    val quality: StreamQuality = StreamQuality.KBPS_320,
    val format: String = AUTO_TRANSCODE_FORMAT,
)

private data class OpenedStream(
    val quality: StreamQuality,
    val forcedTranscode: Boolean,
    val cacheKey: String,
)

private data class BluetoothLyricsDisplayConfig(
    val enabled: Boolean,
    val offsetMs: Int,
)

private data class BluetoothLyricsDisplayState(
    val lyrics: SongLyrics,
    val offsetMs: Int,
)

internal fun bluetoothLyricsLookupPositionMs(
    playbackPositionMs: Long,
    offsetMs: Int,
): Long {
    val safePositionMs = playbackPositionMs.coerceAtLeast(0L)
    val normalizedOffsetMs = normalizeBluetoothLyricsOffsetMs(offsetMs).toLong()
    return safePositionMs.coerceAtMost(Long.MAX_VALUE - normalizedOffsetMs) + normalizedOffsetMs
}

internal fun bluetoothLyricsPollDelayMs(
    lookupPositionMs: Long,
    nextLineStartMs: Long?,
): Long {
    if (nextLineStartMs == null) return BLUETOOTH_LYRICS_MAX_POLL_DELAY_MS
    return (nextLineStartMs - lookupPositionMs).coerceIn(
        BLUETOOTH_LYRICS_MIN_POLL_DELAY_MS,
        BLUETOOTH_LYRICS_MAX_POLL_DELAY_MS,
    )
}

internal fun shouldPauseOffsetStreamAtEnd(repeatMode: Int, mediaItemCount: Int): Boolean =
    repeatMode == Player.REPEAT_MODE_ONE ||
        (repeatMode == Player.REPEAT_MODE_ALL && mediaItemCount == 1)

internal fun isConfirmedTranscode(sourceBitRate: Int?, requestedBitRate: Int?): Boolean {
    val source = sourceBitRate?.takeIf { it > 0 } ?: return false
    val requested = requestedBitRate?.takeIf { it > 0 } ?: return false
    return source > requested
}

internal fun supportsTranscodedServerSeek(
    isCached: Boolean,
    sourceBitRate: Int?,
    openedStreamQuality: StreamQuality?,
    forcedTranscode: Boolean = false,
): Boolean =
    openedStreamQuality != null &&
        (!isCached || forcedTranscode) &&
        (forcedTranscode || isConfirmedTranscode(sourceBitRate, openedStreamQuality.maxBitRate))

internal fun isOriginalPlaybackFailure(kind: PlaybackFailureKind): Boolean =
    kind == PlaybackFailureKind.UNSUPPORTED_FORMAT ||
        kind == PlaybackFailureKind.DECODING_FAILED

internal fun shouldApplyOriginalPlaybackFailureAction(
    kind: PlaybackFailureKind,
    openedStreamQuality: StreamQuality?,
    requestedStreamQuality: StreamQuality?,
    sourceBitRate: Int?,
    sourceSuffix: String?,
    sourceContentType: String?,
    forcedTranscode: Boolean,
    localStreamQuality: StreamQuality? = null,
): Boolean {
    if (!isOriginalPlaybackFailure(kind) || forcedTranscode) return false
    if (localStreamQuality != null) {
        if (localStreamQuality == StreamQuality.ORIGINAL) return true
        return kind == PlaybackFailureKind.UNSUPPORTED_FORMAT &&
            isKnownUnsupportedDownloadedContainer(sourceSuffix, sourceContentType)
    }
    val streamQuality = openedStreamQuality ?: requestedStreamQuality ?: return false
    if (streamQuality == StreamQuality.ORIGINAL) return true
    val source = sourceBitRate?.takeIf { it > 0 }
    if (source == null) {
        return kind == PlaybackFailureKind.UNSUPPORTED_FORMAT &&
            isKnownUnsupportedOriginalContainer(sourceSuffix, sourceContentType)
    }
    val requestedLimit = streamQuality.maxBitRate?.takeIf { it > 0 } ?: return false
    return source <= requestedLimit
}

private fun isKnownUnsupportedDownloadedContainer(
    suffix: String?,
    contentType: String?,
): Boolean {
    val normalizedContentType = contentType?.trim()?.lowercase(Locale.ROOT)
    return when (normalizedContentType) {
        "audio/x-ms-wma",
        "audio/wma",
        "video/x-ms-asf",
        "audio/asf",
        "application/vnd.ms-asf",
        -> true
        null, "", "application/octet-stream" -> {
            val normalizedSuffix = suffix?.trim()?.lowercase(Locale.ROOT)
            normalizedSuffix == "wma" || normalizedSuffix == "asf"
        }
        else -> false
    }
}

internal fun canRetryOriginalWithForcedTranscode(
    usesLocalSource: Boolean,
    hasCompleteStreamCache: Boolean = false,
    hasCompleteForcedTranscodeCache: Boolean = false,
    isOfflineDegraded: Boolean,
): Boolean = hasCompleteForcedTranscodeCache ||
    !(usesLocalSource || hasCompleteStreamCache) ||
    !isOfflineDegraded

internal data class ForcedTranscodeResumePlan(
    val serverOffsetMs: Long?,
    val playerPositionMs: Long,
)

internal fun planForcedTranscodeResume(
    resumePositionMs: Long,
    hasCompleteForcedTranscodeCache: Boolean,
): ForcedTranscodeResumePlan {
    val positionMs = resumePositionMs.coerceAtLeast(0L)
    return if (hasCompleteForcedTranscodeCache) {
        ForcedTranscodeResumePlan(
            serverOffsetMs = null,
            playerPositionMs = positionMs,
        )
    } else {
        ForcedTranscodeResumePlan(
            serverOffsetMs = positionMs.takeIf { it > 0L },
            playerPositionMs = 0L,
        )
    }
}

internal fun selectEffectiveStreamQuality(
    prefs: PlaybackPreferences,
    networkType: NetworkType,
    fallbackMaxBitRate: Int?,
): StreamQuality {
    if (!prefs.adaptiveQualityEnabled) {
        return StreamQuality.entries.find { it.maxBitRate == fallbackMaxBitRate }
            ?: StreamQuality.ORIGINAL
    }
    return when (networkType) {
        NetworkType.WIFI -> prefs.wifiStreamQuality
        NetworkType.MOBILE -> prefs.mobileStreamQuality
    }
}

@AndroidEntryPoint
@UnstableApi
class SakiPlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_SET_SHUFFLE_ORDER = "saki.action.SET_SHUFFLE_ORDER"
        const val ACTION_TOGGLE_REPEAT = "saki.action.TOGGLE_REPEAT"
        const val ACTION_TOGGLE_SHUFFLE = "saki.action.TOGGLE_SHUFFLE"
        const val EXTRA_SHUFFLE_SEED = "saki.extra.SHUFFLE_SEED"
        const val EXTRA_SHUFFLE_ANCHOR = "saki.extra.SHUFFLE_ANCHOR"
        const val EXTRA_SHUFFLE_COUNT = "saki.extra.SHUFFLE_COUNT"
        const val EXTRA_AUTO_TRANSCODE_MEDIA_ID = "saki.extra.AUTO_TRANSCODE_MEDIA_ID"
        const val EXTRA_AUTO_TRANSCODE_FORMAT = "saki.extra.AUTO_TRANSCODE_FORMAT"
        const val EXTRA_AUTO_TRANSCODE_MAX_BIT_RATE = "saki.extra.AUTO_TRANSCODE_MAX_BIT_RATE"
    }

    private var pendingShuffleOrder: Triple<Long, Int, Int>? = null // seed, anchor, count
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var subsonicRepository: SubsonicRepository

    @Inject
    lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    @Inject
    lateinit var localPlayQueueRepository: LocalPlayQueueRepository

    @Inject
    lateinit var streamCache: SimpleCache

    @Inject
    lateinit var streamCacheWriteCoordinator: StreamCacheWriteCoordinator

    @Inject
    lateinit var lyricsHolder: LyricsHolder

    @Inject
    lateinit var networkTypeProvider: org.hdhmc.saki.data.remote.NetworkTypeProvider

    @Inject
    lateinit var streamCacheRepository: org.hdhmc.saki.domain.repository.StreamCacheRepository

    @Inject
    lateinit var endpointSelector: org.hdhmc.saki.data.remote.EndpointSelector

    @Inject
    lateinit var playbackFailureReporter: PlaybackFailureReporter

    private val serviceScope = CoroutineScope(SupervisorJob())
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private lateinit var alacDecoderPolicy: AlacDecoderPolicy
    private lateinit var renderersFactory: SakiRenderersFactory
    private var mediaSession: MediaSession? = null
    private var originalMediaTitle: CharSequence? = null
    private var originalMediaId: String? = null
    private var soundBalancingMode = SoundBalancingMode.OFF
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private lateinit var streamCacheDataSourceFactory: CacheDataSource.Factory
    private var streamPrefetchJob: Job? = null
    private var streamPrefetchReevaluationJob: Job? = null
    private var playbackFailureReportJob: Job? = null
    private var pendingPlaybackFailureMediaId: String? = null
    private var pendingPlaybackFailureErrorCode: Int? = null
    private var activeStreamPrefetchKey: String? = null
    private val completedStreamPrefetchKeysByTarget = ConcurrentHashMap<StreamPrefetchTargetKey, String>()
    private val deferredStreamPrefetchTargets = ConcurrentHashMap<StreamPrefetchTargetKey, Long>()
    @Volatile
    private var activeStreamCacheWriter: CacheWriter? = null
    @Volatile
    private var activeServerSeek: ActiveServerSeek? = null
    private val openedStreams = ConcurrentHashMap<String, OpenedStream>()
    private val forcedTranscodes = ConcurrentHashMap<String, ForcedTranscode>()
    private var pauseAtEndBeforeServerSeek: Boolean? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(HTTP_USER_AGENT)
            .setTransferListener(object : TransferListener {
                override fun onTransferInitializing(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                ) = Unit

                override fun onTransferStart(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                ) {
                    if (!isNetwork) return
                    // Media3 invokes this only after the upstream data source opens successfully.
                    val selection = dataSpec.customData as? StreamEndpointSelection ?: return
                    endpointSelector.recordSuccess(selection.serverId, selection.endpoint)
                    openedStreams[selection.placeholderUri] = OpenedStream(
                        quality = selection.streamQuality,
                        forcedTranscode = selection.forcedTranscode,
                        cacheKey = selection.cacheKey,
                    )
                }

                override fun onBytesTransferred(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                    bytesTransferred: Int,
                ) = Unit

                override fun onTransferEnd(
                    source: DataSource,
                    dataSpec: DataSpec,
                    isNetwork: Boolean,
                ) = Unit
            })
        val upstreamDataSourceFactory = DefaultDataSource.Factory(
            this,
            DataSource.Factory {
                StreamCacheEofTrackingDataSource(
                    httpDataSourceFactory.createDataSource(),
                    ::recordStreamCacheEof,
                )
            },
        )
        val cacheFactory = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        streamCacheDataSourceFactory = cacheFactory
        val dataSourceFactory = ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            resolveStreamDataSpec(dataSpec)
        }

        val initialPrefs = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            playbackPreferencesRepository.getPreferences()
        }
        cachedPlaybackPrefs = initialPrefs
        alacDecoderPolicy = AlacDecoderPolicy(initialPrefs.alacDecoderMode)
        renderersFactory = SakiRenderersFactory(this, alacDecoderPolicy)

        val maxBufferMs = when (initialPrefs.bufferStrategy) {
            BufferStrategy.CUSTOM ->
                minOf(initialPrefs.customBufferSeconds * 1_000, CUSTOM_PLAYER_MAX_BUFFER_MS)
            else -> DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
        }
        val usesCustomBuffer = initialPrefs.bufferStrategy == BufferStrategy.CUSTOM
        val loadControl = if (usesCustomBuffer) {
            SakiLoadControl(
                minBufferMs = minOf(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, maxBufferMs),
                maxBufferMs = maxBufferMs,
                bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                bufferForPlaybackAfterRebufferMs =
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                targetBufferBytes = SakiLoadControl.customTargetBufferBytes(),
                prioritizeTimeOverSizeThresholds = false,
            )
        } else {
            SakiLoadControl(
                minBufferMs = minOf(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, maxBufferMs),
                maxBufferMs = maxBufferMs,
                bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                bufferForPlaybackAfterRebufferMs =
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
        }

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                preloadConfiguration = preloadConfigurationFor(initialPrefs)
                addListener(PlaybackRecoveryListener())
                addListener(PlayQueueSaveListener())
                addListener(NotificationMediaButtonListener())
                addListener(StreamPrefetchListener())
                addListener(ServerSeekStateListener())
                addListener(object : Player.Listener {
                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        val pending = pendingShuffleOrder ?: return
                        val (seed, anchor, count) = pending
                        if (mediaItemCount == count) {
                            setShuffleOrder(SakiShuffleOrder(count, seed, anchor))
                            pendingShuffleOrder = null
                        }
                    }
                })
            }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, SakiSessionPlayer(exoPlayer))
            .setSessionActivity(sessionActivity)
            .setCallback(SakiMediaSessionCallback())
            .setBitmapLoader(CoilBitmapLoader(this))
            .build()
        syncAutomaticTranscodeSessionExtras()
        syncMediaButtonPreferences()

        playerScope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.soundBalancingMode }
                .distinctUntilChanged()
                .collect { mode ->
                    soundBalancingMode = mode
                    val audioSessionId = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
                    syncSoundBalancingEffect(audioSessionId)
                }
        }

        playerScope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.alacDecoderMode }
                .distinctUntilChanged()
                .collect { mode ->
                    if (alacDecoderPolicy.updateMode(mode)) {
                        player?.let(renderersFactory::refreshAlacDecoderPolicy)
                    }
                }
        }

        // Keep playback prefs in memory for the non-suspend ResolvingDataSource resolver
        playerScope.launch {
            playbackPreferencesRepository.observePreferences().collect { prefs ->
                cachedPlaybackPrefs = prefs
                // Keep preload in sync with the same live state the prefetch planner uses, so a
                // mid-session switch into CUSTOM-long mode can't run prefetch while preload is
                // still on (which would reintroduce the cache-key contention).
                player?.preloadConfiguration = if (activeServerSeek == null) {
                    preloadConfigurationFor(prefs)
                } else {
                    ExoPlayer.PreloadConfiguration.DEFAULT
                }
                syncCurrentStreamPrefetch()
            }
        }

        playerScope.launch {
            networkTypeProvider.networkType
                .collect {
                    syncCurrentStreamPrefetch()
                }
        }

        playerScope.launch {
            combine(
                playbackPreferencesRepository.observePreferences()
                    .map { prefs ->
                        BluetoothLyricsDisplayConfig(
                            enabled = prefs.bluetoothLyricsEnabled,
                            offsetMs = normalizeBluetoothLyricsOffsetMs(prefs.bluetoothLyricsOffsetMs),
                        )
                    }
                    .distinctUntilChanged(),
                lyricsHolder.lyrics,
            ) { config, lyrics ->
                if (config.enabled && lyrics != null) {
                    BluetoothLyricsDisplayState(lyrics = lyrics, offsetMs = config.offsetMs)
                } else {
                    null
                }
            }.collectLatest { displayState ->
                if (displayState == null || !displayState.lyrics.synced) {
                    restoreOriginalTitle()
                    return@collectLatest
                }
                val lyrics = displayState.lyrics
                var lastLyricText: String? = null
                try {
                    while (true) {
                        val activePlayer = player ?: break
                        if (!activePlayer.isPlaying) {
                            delay(500)
                            continue
                        }
                        mediaSession ?: break
                        // This lead time is intentionally isolated to media-metadata lyrics.
                        // The in-app scrolling/karaoke lyrics continue to use the unmodified
                        // playback timeline exposed through LyricsHolder and the player state.
                        val positionMs = bluetoothLyricsLookupPositionMs(
                            playbackPositionMs = activePlayer.logicalCurrentPositionMs(),
                            offsetMs = displayState.offsetMs,
                        )
                        val lines = lyrics.lines
                        val index = lines.binarySearchLastBefore(positionMs)
                        val text = if (index >= 0) lines[index].text.takeIf { it.isNotBlank() } else null
                        if (text != null && text != lastLyricText) {
                            lastLyricText = text
                            val item = activePlayer.currentMediaItem ?: break
                            if (originalMediaTitle == null) {
                                originalMediaTitle = item.mediaMetadata.title
                                originalMediaId = item.mediaId
                            }
                            val updated = item.buildUpon()
                                .setMediaMetadata(
                                    item.mediaMetadata.buildUpon()
                                        .setTitle(text)
                                        .build(),
                                )
                                .build()
                            activePlayer.replaceMediaItem(activePlayer.currentMediaItemIndex, updated)
                        }
                        delay(
                            bluetoothLyricsPollDelayMs(
                                lookupPositionMs = positionMs,
                                nextLineStartMs = lines.getOrNull(index + 1)?.startMs,
                            ),
                        )
                    }
                } finally {
                    restoreOriginalTitle()
                }
            }
        }
    }

    private fun syncMediaButtonPreferences() {
        val activePlayer = player ?: return
        mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences(activePlayer))
    }

    private fun buildMediaButtonPreferences(activePlayer: Player): List<CommandButton> {
        val hasQueue = activePlayer.mediaItemCount > 0
        val repeatName = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> getString(R.string.player_repeat_one)
            Player.REPEAT_MODE_ALL -> getString(R.string.player_repeat_all)
            else -> getString(R.string.player_repeat_off)
        }
        val repeatIconRes = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_notification_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_notification_repeat_on
            else -> R.drawable.ic_notification_repeat
        }
        val shuffleName = getString(
            if (activePlayer.shuffleModeEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off,
        )
        val shuffleIconRes = if (activePlayer.shuffleModeEnabled) {
            R.drawable.ic_notification_shuffle_on
        } else {
            R.drawable.ic_notification_shuffle
        }

        return listOf(
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(repeatName)
                .setCustomIconResId(repeatIconRes)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(hasQueue)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(shuffleName)
                .setCustomIconResId(shuffleIconRes)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .setEnabled(activePlayer.mediaItemCount > 1)
                .build(),
        )
    }

    private fun cycleNotificationRepeatMode() {
        val activePlayer = player ?: return
        activePlayer.repeatMode = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncMediaButtonPreferences()
    }

    private fun toggleNotificationShuffle(): ListenableFuture<SessionResult> {
        val future = SettableFuture.create<SessionResult>()
        val activePlayer = player as? ExoPlayer
        if (activePlayer == null) {
            future.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            return future
        }
        playerScope.launch {
            runCatching {
                val count = activePlayer.mediaItemCount
                if (count <= 1) {
                    playbackPreferencesRepository.clearShuffleState()
                    activePlayer.shuffleModeEnabled = false
                    pendingShuffleOrder = null
                    syncMediaButtonPreferences()
                    return@runCatching
                }

                if (activePlayer.shuffleModeEnabled) {
                    playbackPreferencesRepository.clearShuffleState()
                    activePlayer.shuffleModeEnabled = false
                    pendingShuffleOrder = null
                } else {
                    val seed = System.nanoTime()
                    val anchor = activePlayer.currentMediaItemIndex.coerceIn(0, count - 1)
                    playbackPreferencesRepository.updateShuffleState(seed, anchor)
                    activePlayer.setShuffleOrder(SakiShuffleOrder(count, seed, anchor))
                    activePlayer.shuffleModeEnabled = true
                    pendingShuffleOrder = null
                }
                syncMediaButtonPreferences()
            }.onSuccess {
                future.set(SessionResult(SessionResult.RESULT_SUCCESS))
            }.onFailure { throwable ->
                future.setException(throwable)
            }
        }
        return future
    }

    /** Latest playback preferences, kept in memory to avoid blocking reads in the resolver. */
    @Volatile
    private var cachedPlaybackPrefs: PlaybackPreferences? = null

    private fun syncCurrentStreamPrefetch() {
        val activePlayer = player ?: run {
            cancelStreamPrefetch()
            return
        }
        val prefs = cachedPlaybackPrefs ?: return
        val plan = activePlayer.buildStreamPrefetchPlan(prefs)
        if (plan == null) {
            cancelStreamPrefetch()
            return
        }
        scheduleStreamPrefetchReevaluation()

        if (activeStreamPrefetchKey == plan.key && streamPrefetchJob?.isActive == true) {
            return
        }

        if (plan.targets.isEmpty()) {
            cancelActiveStreamPrefetch()
            return
        }

        cancelActiveStreamPrefetch()
        activeStreamPrefetchKey = plan.key
        streamPrefetchJob = serviceScope.launch(Dispatchers.IO) {
            val result = runCatching {
                prefetchTimelineToDisk(plan)
            }
            result.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (throwable is InterruptedIOException) {
                    return@onFailure
                }
                Log.w(STREAM_PREFETCH_LOG_TAG, "Failed to prefetch stream cache", throwable)
            }
            playerScope.launch {
                if (activeStreamPrefetchKey == plan.key) {
                    activeStreamPrefetchKey = null
                    streamPrefetchJob = null
                    if (result.isSuccess) {
                        syncCurrentStreamPrefetch()
                    }
                }
            }
        }
    }

    private fun cancelStreamPrefetch() {
        cancelActiveStreamPrefetch()
        streamPrefetchReevaluationJob?.cancel()
        streamPrefetchReevaluationJob = null
    }

    private fun cancelActiveStreamPrefetch() {
        activeStreamPrefetchKey = null
        activeStreamCacheWriter?.cancel()
        activeStreamCacheWriter = null
        streamPrefetchJob?.cancel()
        streamPrefetchJob = null
    }

    private fun scheduleStreamPrefetchReevaluation() {
        if (streamPrefetchReevaluationJob?.isActive == true) return
        streamPrefetchReevaluationJob = playerScope.launch {
            while (true) {
                delay(STREAM_PREFETCH_REEVALUATION_MS)
                if (streamPrefetchJob?.isActive == true) continue
                syncCurrentStreamPrefetch()
            }
        }
    }

    private fun requestedStreamQuality(
        prefs: PlaybackPreferences,
        fallbackMaxBitRate: Int?,
    ): StreamQuality = selectEffectiveStreamQuality(
        prefs = prefs,
        networkType = networkTypeProvider.networkType.value,
        fallbackMaxBitRate = fallbackMaxBitRate,
    )

    private fun PlaybackRequest.requestedStreamQuality(prefs: PlaybackPreferences): StreamQuality =
        requestedStreamQuality(prefs, maxBitRate)

    private fun PlaybackRequest.withStreamQuality(quality: StreamQuality): PlaybackRequest {
        return copy(
            qualityLabel = quality.label,
            streamCacheKey = streamCacheRepository.buildCacheKey(serverId, songId, quality),
            maxBitRate = quality.maxBitRate,
            format = quality.format,
            bitRate = estimatedPlaybackBitRateKbps(sourceBitRate, quality.maxBitRate),
        )
    }

    private fun PlaybackRequest.toStreamPlaceholderUri(streamInstanceId: String? = null): Uri {
        return Uri.Builder()
            .scheme("saki")
            .authority("stream")
            .appendQueryParameter("serverId", serverId.toString())
            .appendQueryParameter("songId", songId)
            .apply {
                maxBitRate?.let { appendQueryParameter("maxBitRate", it.toString()) }
                format?.let { appendQueryParameter("format", it) }
                streamInstanceId?.let { appendQueryParameter("instanceId", it) }
            }
            .build()
    }

    private fun PlaybackRequest.toStreamPlaceholderDataSpec(): DataSpec {
        return DataSpec.Builder()
            .setUri(toStreamPlaceholderUri())
            .build()
    }

    private fun usesDiskPrefetch(prefs: PlaybackPreferences): Boolean =
        prefs.bufferStrategy == BufferStrategy.CUSTOM &&
            prefs.customBufferSeconds * 1_000L > CUSTOM_PLAYER_MAX_BUFFER_MS

    // Disk prefetch owns look-ahead in CUSTOM-long mode. Next-item preload would hold the same
    // stream cache key and starve the prefetch, so the next track never fully caches ahead (#292).
    // Disable preload there; the fully-prefetched next track gives instant transitions from disk.
    // NORMAL / short-CUSTOM keep the 10s preload (no disk prefetch).
    private fun preloadConfigurationFor(prefs: PlaybackPreferences): ExoPlayer.PreloadConfiguration =
        if (usesDiskPrefetch(prefs)) {
            ExoPlayer.PreloadConfiguration.DEFAULT
        } else {
            ExoPlayer.PreloadConfiguration(10 * C.MICROS_PER_SECOND)
        }

    private fun ExoPlayer.buildStreamPrefetchPlan(prefs: PlaybackPreferences): StreamPrefetchPlan? {
        val customBufferMs = prefs.customBufferSeconds * 1_000L
        val currentIndex = currentMediaItemIndex
        if (
            !usesDiskPrefetch(prefs) ||
            playbackState == Player.STATE_IDLE ||
            playbackState == Player.STATE_ENDED ||
            currentIndex == C.INDEX_UNSET ||
            mediaItemCount <= 0 ||
            currentTimeline.windowCount == 0
        ) {
            return null
        }

        var remainingBudgetMs = customBufferMs
        var index = currentIndex
        var visited = 0
        val targets = mutableListOf<StreamPrefetchTarget>()
        val keyParts = mutableListOf<String>()
        val window = Timeline.Window()
        val nowMs = SystemClock.elapsedRealtime()
        var distanceFromCurrentMs = 0L
        while (remainingBudgetMs > 0L && index != C.INDEX_UNSET && visited < mediaItemCount) {
            val mediaItem = getMediaItemAt(index)
            val request = mediaItem.toPlaybackRequestOrNull() ?: break
            val durationMs = durationForPrefetchMs(
                index = index,
                mediaItem = mediaItem,
                request = request,
                window = window,
            )
            val positionMs = if (index == currentIndex) logicalCurrentPositionMs() else 0L
            val remainingTrackMs = durationMs
                ?.minus(positionMs)
                ?.coerceAtLeast(0L)
            if (!request.isCached && request.localPath == null && !endpointSelector.isOfflineDegraded(request.serverId)) {
                val quality = request.requestedStreamQuality(prefs)
                val prefetchRequest = request.withStreamQuality(quality)
                val targetKey = StreamPrefetchTargetKey(
                    serverId = request.serverId,
                    songId = request.songId,
                    qualityKey = quality.storageKey,
                )
                val isSatisfied = isStreamPrefetchSatisfied(request.serverId, request.songId, quality)
                val isDeferred = isStreamPrefetchDeferred(targetKey, nowMs)
                val trackEndFromCurrentMs = remainingTrackMs?.let { distanceFromCurrentMs + it }
                // Only the current item is covered by the player's in-memory buffer. This planner
                // runs only in the mode where next-item preload is disabled (#292), so upcoming
                // tracks are NOT player-buffered and must be disk-prefetched even when their end
                // falls within CUSTOM_PLAYER_MAX_BUFFER_MS.
                val isHandledByPlayerBuffer =
                    index == currentIndex &&
                        trackEndFromCurrentMs?.let { it <= CUSTOM_PLAYER_MAX_BUFFER_MS } == true
                val targetState = when {
                    isSatisfied -> "done"
                    isDeferred -> "deferred"
                    isHandledByPlayerBuffer -> "player"
                    else -> "pending"
                }
                keyParts +=
                    "$index:${targetKey.planKey()}:$targetState"
                if (targetState == "pending") {
                    targets += StreamPrefetchTarget(
                        request = prefetchRequest,
                        quality = quality,
                        queueIndex = index,
                        targetKey = targetKey,
                    )
                }
            }

            remainingTrackMs?.let {
                remainingBudgetMs -= it
                distanceFromCurrentMs += it
            } ?: break
            if (repeatMode == Player.REPEAT_MODE_ONE) break
            val nextIndex = currentTimeline.getNextWindowIndex(index, repeatMode, shuffleModeEnabled)
            if (nextIndex == C.INDEX_UNSET || nextIndex == index) break
            index = nextIndex
            visited++
        }

        if (keyParts.isEmpty()) return null
        return StreamPrefetchPlan(
            key = keyParts.joinToString(separator = "|", prefix = "$customBufferMs:$repeatMode:$shuffleModeEnabled:"),
            targets = targets,
        )
    }

    private fun ExoPlayer.currentStreamOffsetMs(): Long {
        val activeSeek = activeServerSeek ?: return 0L
        if (currentMediaItemIndex != activeSeek.mediaItemIndex) return 0L
        val request = currentMediaItem?.toPlaybackRequestOrNull() ?: return 0L
        return if (request.serverId == activeSeek.serverId && request.songId == activeSeek.songId) {
            activeSeek.offsetMs
        } else {
            0L
        }
    }

    private fun ExoPlayer.logicalCurrentPositionMs(): Long =
        currentPosition.coerceAtLeast(0L) + currentStreamOffsetMs()

    private fun MediaItem.openedStream(): OpenedStream? =
        localConfiguration?.uri?.toString()?.let(openedStreams::get)

    private fun ExoPlayer.durationForPrefetchMs(
        index: Int,
        mediaItem: MediaItem,
        request: PlaybackRequest,
        window: Timeline.Window,
    ): Long? {
        if (index == currentMediaItemIndex && currentStreamOffsetMs() > 0L) {
            return request.durationMs?.coerceKnownDuration()
                ?: mediaItem.metadataDurationMs()
        }
        val playerDuration = if (index == currentMediaItemIndex) {
            duration.coerceKnownDuration()
        } else {
            null
        }
        val timelineDuration = currentTimeline.getWindow(index, window).durationMs.coerceKnownDuration()
        return playerDuration
            ?: timelineDuration
            ?: request.durationMs?.coerceKnownDuration()
            ?: mediaItem.metadataDurationMs()
    }

    private fun isStreamPrefetchSatisfied(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): Boolean {
        if (streamCacheRepository.findCachedQualityKey(serverId, songId, preferredQuality) != null) {
            return true
        }
        return findCompletedStreamPrefetchCacheKey(serverId, songId, preferredQuality) != null
    }

    private fun findCompletedStreamPrefetchCacheKey(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): String? {
        val exactTargetKey = StreamPrefetchTargetKey(serverId, songId, preferredQuality.storageKey)
        isCompletedStreamPrefetchCacheKey(exactTargetKey)?.let { return it }

        val preferredIndex = StreamQuality.entries.indexOf(preferredQuality)
        if (preferredIndex < 0) return null
        for (index in 0..preferredIndex) {
            val quality = StreamQuality.entries[index]
            if (quality == preferredQuality) continue
            val targetKey = StreamPrefetchTargetKey(serverId, songId, quality.storageKey)
            isCompletedStreamPrefetchCacheKey(targetKey)?.let { return it }
        }
        return null
    }

    private fun isStreamPrefetchDeferred(
        targetKey: StreamPrefetchTargetKey,
        nowMs: Long,
    ): Boolean {
        val retryAtMs = deferredStreamPrefetchTargets[targetKey] ?: return false
        if (retryAtMs > nowMs) return true
        deferredStreamPrefetchTargets.remove(targetKey, retryAtMs)
        return false
    }

    private fun isCompletedStreamPrefetchCacheKey(targetKey: StreamPrefetchTargetKey): String? {
        val cacheKey = completedStreamPrefetchKeysByTarget[targetKey] ?: return null
        if (streamCache.getCachedSpans(cacheKey).any { span -> span.length > 0L }) return cacheKey
        completedStreamPrefetchKeysByTarget.remove(targetKey, cacheKey)
        return null
    }

    private suspend fun prefetchTimelineToDisk(plan: StreamPrefetchPlan) {
        plan.targets.forEach { target ->
            currentCoroutineContext().ensureActive()
            if (isStreamPrefetchSatisfied(target.request.serverId, target.request.songId, target.quality)) {
                return@forEach
            }
            val result = prefetchStreamToDisk(target.request) ?: return@forEach
            if (result.cachedBytes > 0L) {
                completedStreamPrefetchKeysByTarget[target.targetKey] = result.cacheKey
                deferredStreamPrefetchTargets.remove(target.targetKey)
                streamCacheRepository.requestSnapshotRefresh()
            } else {
                deferredStreamPrefetchTargets[target.targetKey] =
                    SystemClock.elapsedRealtime() + STREAM_PREFETCH_RETRY_DELAY_MS
            }
        }
    }

    private suspend fun prefetchStreamToDisk(request: PlaybackRequest): StreamPrefetchResult? {
        currentCoroutineContext().ensureActive()
        val resolvedSpec = resolveStreamDataSpec(
            dataSpec = request.toStreamPlaceholderDataSpec(),
            allowCachedResource = false,
        )
        currentCoroutineContext().ensureActive()
        val cacheKey = resolvedSpec.key?.takeIf { key -> key.isNotBlank() } ?: return null
        if (resolvedSpec.uri.scheme == "saki-cache") {
            return StreamPrefetchResult(cacheKey = cacheKey, cachedBytes = cachedBytes(cacheKey))
        }
        val prefetchSpec = resolvedSpec.buildUpon()
            .setFlags(resolvedSpec.flags or DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
            .build()

        val writer = CacheWriter(
            streamCacheDataSourceFactory.createDataSource(),
            prefetchSpec,
            null,
            null,
        )
        activeStreamCacheWriter = writer
        try {
            streamCacheWriteCoordinator.withWriter {
                runInterruptible(Dispatchers.IO) {
                    writer.cache()
                }
            }
        } finally {
            if (activeStreamCacheWriter === writer) {
                activeStreamCacheWriter = null
            }
        }
        return StreamPrefetchResult(cacheKey = cacheKey, cachedBytes = cachedBytes(cacheKey))
    }

    private fun cachedBytes(cacheKey: String): Long {
        return streamCache.getCachedSpans(cacheKey).sumOf { span -> span.length }
    }

    private data class StreamEndpointSelection(
        val serverId: Long,
        val endpoint: ServerEndpoint,
        val placeholderUri: String,
        val streamQuality: StreamQuality,
        val cacheKey: String,
        val forcedTranscode: Boolean = false,
    )

    private data class StreamPrefetchPlan(
        val key: String,
        val targets: List<StreamPrefetchTarget>,
    )

    private data class StreamPrefetchResult(
        val cacheKey: String,
        val cachedBytes: Long,
    )

    private data class StreamPrefetchTarget(
        val request: PlaybackRequest,
        val quality: StreamQuality,
        val queueIndex: Int,
        val targetKey: StreamPrefetchTargetKey,
    )

    private data class StreamPrefetchTargetKey(
        val serverId: Long,
        val songId: String,
        val qualityKey: String,
    ) {
        fun planKey(): String = "$serverId:$songId:$qualityKey"
    }

    private fun recordStreamCacheEof(dataSpec: DataSpec, eofPosition: Long) {
        val cacheKey = dataSpec.key ?: return
        if (eofPosition <= 0L || parseStreamCacheKey(cacheKey) == null) return
        runCatching {
            streamCache.applyContentMetadataMutations(
                cacheKey,
                ContentMetadataMutations().set(STREAM_CACHE_EOF_LENGTH_METADATA_KEY, eofPosition),
            )
        }.onSuccess {
            streamCacheRepository.requestSnapshotRefresh()
        }.onFailure { throwable ->
            Log.w(STREAM_PREFETCH_LOG_TAG, "Failed to record stream cache EOF for $cacheKey", throwable)
        }
    }

    /**
     * Resolves placeholder `saki://stream` URIs to real Subsonic stream URLs at the moment
     * ExoPlayer actually opens the data source. This ensures the quality and endpoint are
     * determined by the current network state, not the queue build time.
     */
    private fun resolveStreamDataSpec(
        dataSpec: DataSpec,
        allowCachedResource: Boolean = true,
    ): DataSpec {
        val uri = dataSpec.uri
        val sourceKey = uri.toString()
        val forcedTranscode = forcedTranscodes[sourceKey]
        val isStreamPlaceholder = uri.scheme == "saki" && uri.host == "stream"
        if (!isStreamPlaceholder && forcedTranscode == null) return dataSpec
        openedStreams.remove(sourceKey)

        val serverId = forcedTranscode?.serverId
            ?: uri.getQueryParameter("serverId")?.toLongOrNull()
            ?: throw IOException("Missing serverId in stream placeholder URI")
        val songId = forcedTranscode?.songId
            ?: uri.getQueryParameter("songId")
            ?: throw IOException("Missing songId in stream placeholder URI")
        val activeSeek = activeServerSeek
            ?.takeIf { seek -> seek.serverId == serverId && seek.songId == songId }
        val timeOffsetSeconds = activeSeek
            ?.offsetMs
            ?.div(1_000L)
            ?.toInt()
            ?.takeIf { it > 0 }

        // Use cached prefs to avoid blocking; fall back to blocking read if not yet available
        val prefs = cachedPlaybackPrefs ?: runBlocking { playbackPreferencesRepository.getPreferences() }
        val requestedQuality = activeSeek?.streamQuality
            ?: forcedTranscode?.quality
            ?: requestedStreamQuality(
                prefs = prefs,
                fallbackMaxBitRate = uri.getQueryParameter("maxBitRate")?.toIntOrNull(),
            )
        if (forcedTranscode != null) {
            val streamRequest = runBlocking {
                subsonicRepository.buildStreamRequest(
                    serverId = serverId,
                    songId = songId,
                    maxBitRate = forcedTranscode.quality.maxBitRate,
                    format = forcedTranscode.format,
                    timeOffsetSeconds = timeOffsetSeconds,
                )
            }
            if (streamRequest.candidates.isEmpty()) {
                throw IOException("No stream candidates for song $songId on server $serverId")
            }
            val candidate = streamRequest.candidates.first()
            val cacheKey = buildForcedTranscodeStreamCacheKey(
                serverId = serverId,
                songId = songId,
                quality = forcedTranscode.quality,
                format = forcedTranscode.format,
            )
            return dataSpec.buildUpon()
                .setUri(candidate.url)
                .setKey(cacheKey.forStreamOffset(timeOffsetSeconds))
                .setFlags(dataSpec.flags.forStreamOffset(timeOffsetSeconds))
                .setCustomData(
                    StreamEndpointSelection(
                        serverId = serverId,
                        endpoint = candidate.endpoint,
                        placeholderUri = sourceKey,
                        streamQuality = forcedTranscode.quality,
                        forcedTranscode = true,
                        cacheKey = cacheKey,
                    ),
                )
                .build()
        }
        val preferLocalCache = shouldPreferLocalStreamCache(serverId)
        val cacheLookupQuality = if (preferLocalCache) StreamQuality.entries.last() else requestedQuality
        val cachedQualityKey = if (timeOffsetSeconds == null) {
            streamCacheRepository.findCachedQualityKey(serverId, songId, cacheLookupQuality)
        } else {
            null
        }
        val cachedQuality = cachedQualityKey?.let { key -> StreamQuality.fromStorageKey(key) }
        val cachedResourceKey = cachedQuality?.let { quality ->
            streamCacheRepository.buildCacheKey(serverId, songId, quality)
        }
        if (cachedQuality != null && cachedResourceKey != null) {
            openedStreams[sourceKey] = OpenedStream(
                quality = cachedQuality,
                forcedTranscode = false,
                cacheKey = cachedResourceKey,
            )
        }
        if (allowCachedResource && preferLocalCache && cachedResourceKey != null) {
            return dataSpec.buildUpon()
                .setUri(cachedStreamUri(cachedResourceKey))
                .setKey(cachedResourceKey)
                .build()
        }

        val quality = cachedQuality ?: requestedQuality
        val format = uri.getQueryParameter("format")
        if (!prefs.adaptiveQualityEnabled && cachedResourceKey == null && format != null && format != requestedQuality.format) {
            val streamRequest = runBlocking {
                subsonicRepository.buildStreamRequest(
                    serverId = serverId,
                    songId = songId,
                    maxBitRate = requestedQuality.maxBitRate,
                    format = format,
                    timeOffsetSeconds = timeOffsetSeconds,
                )
            }
            if (streamRequest.candidates.isEmpty()) {
                throw IOException("No stream candidates for song $songId on server $serverId")
            }
            val candidate = streamRequest.candidates.first()
            val cacheKey = streamCacheRepository.buildCacheKey(serverId, songId, requestedQuality)
            return dataSpec.buildUpon()
                .setUri(candidate.url)
                .setKey(cacheKey.forStreamOffset(timeOffsetSeconds))
                .setFlags(dataSpec.flags.forStreamOffset(timeOffsetSeconds))
                .setCustomData(
                    StreamEndpointSelection(
                        serverId = serverId,
                        endpoint = candidate.endpoint,
                        placeholderUri = uri.toString(),
                        streamQuality = requestedQuality,
                        cacheKey = cacheKey,
                    ),
                )
                .build()
        }

        val streamRequest = runBlocking {
            subsonicRepository.buildStreamRequest(
                serverId = serverId,
                songId = songId,
                maxBitRate = quality.maxBitRate,
                format = quality.format,
                timeOffsetSeconds = timeOffsetSeconds,
            )
        }
        if (streamRequest.candidates.isEmpty()) {
            throw IOException("No stream candidates for song $songId on server $serverId")
        }

        val candidate = streamRequest.candidates.first()
        val realUrl = candidate.url
        val cacheKey = streamCacheRepository.buildCacheKey(serverId, songId, quality)

        return dataSpec.buildUpon()
            .setUri(realUrl)
            .setKey((cachedResourceKey ?: cacheKey).forStreamOffset(timeOffsetSeconds))
            .setFlags(dataSpec.flags.forStreamOffset(timeOffsetSeconds))
            .setCustomData(
                StreamEndpointSelection(
                    serverId = serverId,
                    endpoint = candidate.endpoint,
                    placeholderUri = uri.toString(),
                    streamQuality = quality,
                    cacheKey = cacheKey,
                ),
            )
            .build()
    }

    private inner class SakiSessionPlayer(
        private val exoPlayer: ExoPlayer,
    ) : ForwardingSimpleBasePlayer(exoPlayer) {
        override fun getState(): SimpleBasePlayer.State {
            val state = super.getState()
            val currentItem = exoPlayer.currentMediaItem
            val currentRequest = currentItem?.toPlaybackRequestOrNull()
            val openedStream = currentItem?.openedStream()
            val exposesServerSideSeek = currentRequest?.supportsServerSideSeek(openedStream) == true &&
                currentItem.metadataDurationMs() != null
            if (!exposesServerSideSeek) {
                return state
            }

            val logicalState = state.buildUpon()
                .setPlaylist(
                    state.playlist.mapIndexed { index, itemData ->
                        val item = itemData.mediaItem
                        val request = item.toPlaybackRequestOrNull()
                        val durationMs = item.metadataDurationMs()
                        if (
                            index == exoPlayer.currentMediaItemIndex &&
                            request?.supportsServerSideSeek(openedStream) == true &&
                            durationMs != null
                        ) {
                            itemData.buildUpon()
                                .setLiveConfiguration(null)
                                .setPresentationStartTimeMs(C.TIME_UNSET)
                                .setWindowStartTimeMs(C.TIME_UNSET)
                                .setElapsedRealtimeEpochOffsetMs(C.TIME_UNSET)
                                .setIsDynamic(false)
                                .setIsSeekable(true)
                                .setDurationUs(durationMs * 1_000L)
                                .setPeriods(emptyList())
                                .build()
                        } else {
                            itemData
                        }
                    },
                )
                .build()
            val stateBuilder = logicalState.buildUpon()
                .setPlaylist(
                    NavigationPreservingTimeline(
                        contentTimeline = logicalState.timeline,
                        navigationTimeline = state.timeline,
                    ),
                    logicalState.currentTracks,
                    logicalState.currentMetadata,
                )

            val logicalDurationMs = currentItem.metadataDurationMs()!!
            val logicalPosition = SimpleBasePlayer.PositionSupplier {
                exoPlayer.logicalCurrentPositionMs().coerceAtMost(logicalDurationMs)
            }
            val logicalBufferedPosition = SimpleBasePlayer.PositionSupplier {
                (exoPlayer.contentBufferedPosition.coerceAtLeast(0L) + exoPlayer.currentStreamOffsetMs())
                    .coerceAtMost(logicalDurationMs)
            }
            stateBuilder
                .setAvailableCommands(
                    state.availableCommands.buildUpon()
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_TIMELINE)
                        .add(Player.COMMAND_GET_METADATA)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_BACK)
                        .add(Player.COMMAND_SEEK_FORWARD)
                        .build(),
                )
                .setContentPositionMs(logicalPosition)
                .setContentBufferedPositionMs(logicalBufferedPosition)
            if (state.hasPositionDiscontinuity) {
                stateBuilder.setPositionDiscontinuity(
                    state.positionDiscontinuityReason,
                    (state.discontinuityPositionMs + exoPlayer.currentStreamOffsetMs())
                        .coerceAtMost(logicalDurationMs),
                )
            }
            return stateBuilder.build()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int,
        ): ListenableFuture<*> {
            val targetsCurrentItem = mediaItemIndex == C.INDEX_UNSET ||
                mediaItemIndex == exoPlayer.currentMediaItemIndex
            val canUseServerOffset = targetsCurrentItem && when (seekCommand) {
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                -> true
                else -> false
            }
            if (canUseServerOffset && seekTranscodedStream(positionMs)) {
                return Futures.immediateVoidFuture()
            }
            return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }

        private fun seekTranscodedStream(requestedPositionMs: Long): Boolean {
            val mediaItemIndex = exoPlayer.currentMediaItemIndex
            if (mediaItemIndex == C.INDEX_UNSET) return false
            val item = exoPlayer.currentMediaItem ?: return false
            val request = item.toPlaybackRequestOrNull() ?: return false
            val openedStream = item.openedStream() ?: return false
            if (!request.supportsServerSideSeek(openedStream)) return false
            val openedQuality = openedStream.quality

            val durationMs = item.metadataDurationMs() ?: return false
            val targetPositionMs = requestedPositionMs
                .coerceIn(0L, (durationMs - 1_000L).coerceAtLeast(0L))
                .toWholeSecondOffsetMs()
            val hasCompleteBaseStream = streamCacheRepository.isCacheKeyFullyCached(openedStream.cacheKey)
            val currentOffsetMs = exoPlayer.currentStreamOffsetMs()

            if (hasCompleteBaseStream && currentOffsetMs == 0L) {
                return false
            }

            val serverOffsetMs = if (hasCompleteBaseStream) 0L else targetPositionMs
            val rawSeekPositionMs = if (hasCompleteBaseStream) targetPositionMs else 0L
            val wasPlaying = exoPlayer.playWhenReady
            exoPlayer.stop()
            updateActiveServerSeek(
                if (serverOffsetMs > 0L) {
                    ActiveServerSeek(
                        mediaItemIndex = mediaItemIndex,
                        serverId = request.serverId,
                        songId = request.songId,
                        offsetMs = serverOffsetMs,
                        streamQuality = openedQuality,
                    )
                } else {
                    null
                },
            )
            exoPlayer.seekTo(mediaItemIndex, rawSeekPositionMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = wasPlaying
            return true
        }
    }

    private fun updateActiveServerSeek(activeSeek: ActiveServerSeek?) {
        val previousSeek = activeServerSeek
        val activePlayer = player
        if (previousSeek == null && activeSeek != null && activePlayer != null) {
            pauseAtEndBeforeServerSeek = activePlayer.pauseAtEndOfMediaItems
        }
        activeServerSeek = activeSeek
        activePlayer?.let {
            if (activeSeek == null) {
                pauseAtEndBeforeServerSeek?.let(it::setPauseAtEndOfMediaItems)
                pauseAtEndBeforeServerSeek = null
            } else {
                syncOffsetStreamPauseAtEnd(it)
            }
            it.preloadConfiguration = if (activeSeek == null) {
                cachedPlaybackPrefs?.let(::preloadConfigurationFor)
                    ?: ExoPlayer.PreloadConfiguration.DEFAULT
            } else {
                ExoPlayer.PreloadConfiguration.DEFAULT
            }
        }
    }

    private fun syncOffsetStreamPauseAtEnd(activePlayer: ExoPlayer) {
        val shouldPauseAtEnd = activeServerSeek != null &&
            shouldPauseOffsetStreamAtEnd(activePlayer.repeatMode, activePlayer.mediaItemCount)
        val desiredValue = shouldPauseAtEnd || pauseAtEndBeforeServerSeek == true
        if (activePlayer.pauseAtEndOfMediaItems != desiredValue) {
            activePlayer.setPauseAtEndOfMediaItems(desiredValue)
        }
    }

    private inner class ServerSeekStateListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val activeSeek = activeServerSeek ?: return
            val activePlayer = player ?: return
            val request = mediaItem?.toPlaybackRequestOrNull()
            val remainsOnOffsetStream =
                activePlayer.currentMediaItemIndex == activeSeek.mediaItemIndex &&
                    request?.serverId == activeSeek.serverId &&
                    request.songId == activeSeek.songId
            if (!remainsOnOffsetStream) {
                updateActiveServerSeek(null)
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val activePlayer = player ?: return
            if (activeServerSeek != null) {
                syncOffsetStreamPauseAtEnd(activePlayer)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return
            val activeSeek = activeServerSeek ?: return
            val activePlayer = player ?: return
            if (!shouldPauseOffsetStreamAtEnd(activePlayer.repeatMode, activePlayer.mediaItemCount)) return
            restartOffsetStreamRepeatFromBeginning(activePlayer, activeSeek.mediaItemIndex, shouldResume = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val activeSeek = activeServerSeek ?: return
            val activePlayer = player ?: return
            if (!shouldPauseOffsetStreamAtEnd(activePlayer.repeatMode, activePlayer.mediaItemCount)) return
            restartOffsetStreamRepeatFromBeginning(activePlayer, activeSeek.mediaItemIndex, shouldResume = true)
        }
    }

    private fun restartOffsetStreamRepeatFromBeginning(
        activePlayer: ExoPlayer,
        mediaItemIndex: Int,
        shouldResume: Boolean,
    ) {
        activePlayer.stop()
        updateActiveServerSeek(null)
        activePlayer.seekTo(mediaItemIndex, 0L)
        activePlayer.prepare()
        activePlayer.playWhenReady = shouldResume
    }

    private fun PlaybackRequest.supportsServerSideSeek(openedStream: OpenedStream?): Boolean =
        supportsTranscodedServerSeek(
            isCached = isCached,
            sourceBitRate = sourceBitRate,
            openedStreamQuality = openedStream?.quality,
            forcedTranscode = openedStream?.forcedTranscode == true,
        )

    private fun shouldPreferLocalStreamCache(serverId: Long): Boolean {
        return endpointSelector.isOfflineDegraded(serverId)
    }

    private fun cachedStreamUri(cacheKey: String): Uri {
        return Uri.Builder()
            .scheme("saki-cache")
            .authority("stream")
            .appendQueryParameter("key", cacheKey)
            .build()
    }

    override fun onGetSession(
        controllerInfo: ControllerInfo,
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlayQueue(immediate = true)
        val activePlayer = player ?: return super.onTaskRemoved(rootIntent)
        if (!activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        savePlayQueue(immediate = true)
        cancelStreamPrefetch()
        clearPlaybackFailureReport()
        playbackFailureReporter.resetOriginalPlaybackSkipRecovery()
        openedStreams.clear()
        forcedTranscodes.clear()
        releaseSoundBalancingEffect()

        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        playerScope.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private var savePlayQueueJob: kotlinx.coroutines.Job? = null

    private fun savePlayQueue(immediate: Boolean = false) {
        val activePlayer = player ?: return
        val itemCount = activePlayer.mediaItemCount
        if (itemCount == 0) return
        val request = activePlayer.currentMediaItem?.toPlaybackRequestOrNull() ?: return
        val queueRequests = (0 until itemCount)
            .mapNotNull { i -> activePlayer.getMediaItemAt(i).toPlaybackRequestOrNull() }
            .filter { itemRequest -> itemRequest.serverId == request.serverId }
        val songIds = queueRequests.map(PlaybackRequest::songId)
        if (songIds.isEmpty()) return
        val positionMs = activePlayer.logicalCurrentPositionMs()
        val serverId = request.serverId
        val currentSongId = request.songId
        val snapshot = LocalPlayQueueSnapshot(
            serverId = serverId,
            songs = queueRequests.map(PlaybackRequest::toSong),
            currentSongId = currentSongId,
            positionMs = positionMs,
            updatedAt = System.currentTimeMillis(),
            source = queueRequests.toSnapshotSource(request),
        )
        savePlayQueueJob?.cancel()
        if (immediate) {
            runBlocking {
                saveLocalPlayQueueSnapshot(snapshot)
            }
            savePlayQueueJob = serviceScope.launch {
                saveRemotePlayQueue(
                    serverId = serverId,
                    songIds = songIds,
                    currentSongId = currentSongId,
                    positionMs = positionMs,
                )
            }
            return
        }

        savePlayQueueJob = serviceScope.launch {
            delay(500)
            saveLocalPlayQueueSnapshot(snapshot)
            saveRemotePlayQueue(
                serverId = serverId,
                songIds = songIds,
                currentSongId = currentSongId,
                positionMs = positionMs,
            )
        }
    }

    private suspend fun saveLocalPlayQueueSnapshot(snapshot: LocalPlayQueueSnapshot) {
        try {
            localPlayQueueRepository.save(snapshot)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        }
    }

    private suspend fun saveRemotePlayQueue(
        serverId: Long,
        songIds: List<String>,
        currentSongId: String,
        positionMs: Long,
    ) {
        try {
            subsonicRepository.savePlayQueue(
                serverId = serverId,
                songIds = songIds,
                currentSongId = currentSongId,
                positionMs = positionMs,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
        }
    }

    private fun List<PlaybackRequest>.toSnapshotSource(
        currentRequest: PlaybackRequest,
    ): LocalPlayQueueSnapshotSource? {
        if (currentRequest.queueSource != PLAYBACK_QUEUE_SOURCE_LIBRARY_SONGS) return null
        val currentIndex = currentRequest.libraryIndex ?: return null
        val libraryIndexes = mapNotNull { request ->
            request.libraryIndex.takeIf { request.queueSource == PLAYBACK_QUEUE_SOURCE_LIBRARY_SONGS }
        }
        if (libraryIndexes.size != size) return null
        return LocalPlayQueueSnapshotSource(
            type = LocalPlayQueueSnapshotSourceType.LIBRARY_SONGS,
            currentIndex = currentIndex,
            windowOffset = libraryIndexes.minOrNull() ?: currentIndex,
        )
    }

    private inner class SakiMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: ControllerInfo,
        ): ConnectionResult {
            val baseResult = ConnectionResult.AcceptedResultBuilder(session)
            val sessionCommandsBuilder = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
            if (controller.packageName == packageName || controller.isTrusted) {
                val sessionCommands = sessionCommandsBuilder
                    .add(SessionCommand(ACTION_SET_SHUFFLE_ORDER, Bundle.EMPTY))
                    .build()
                return baseResult
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            val filteredPlayerCommands = Player.Commands.Builder()
                .addAllCommands()
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build()

            return baseResult
                .setAvailableSessionCommands(sessionCommandsBuilder.build())
                .setAvailablePlayerCommands(filteredPlayerCommands)
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()

            serviceScope.launch {
                try {
                    future.set(
                        mediaItems.map { mediaItem ->
                            resolvePlayableItem(mediaItem)
                        },
                    )
                } catch (throwable: Throwable) {
                    future.setException(throwable)
                }
            }

            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SET_SHUFFLE_ORDER -> {
                    val seed = args.getLong(EXTRA_SHUFFLE_SEED)
                    val anchor = args.getInt(EXTRA_SHUFFLE_ANCHOR)
                    val count = args.getInt(EXTRA_SHUFFLE_COUNT)
                    val exoPlayer = player as? ExoPlayer
                    if (exoPlayer != null && count > 0 && count == exoPlayer.mediaItemCount) {
                        val order = SakiShuffleOrder(count, seed, anchor)
                        exoPlayer.setShuffleOrder(order)
                        pendingShuffleOrder = null
                    } else {
                        pendingShuffleOrder = Triple(seed, anchor, count)
                    }
                    return successSessionResult()
                }
                ACTION_TOGGLE_REPEAT -> {
                    cycleNotificationRepeatMode()
                    return successSessionResult()
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    return toggleNotificationShuffle()
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        private suspend fun resolvePlayableItem(
            mediaItem: MediaItem,
        ): MediaItem {
            if (mediaItem.localConfiguration != null) {
                return mediaItem
            }

            val request = requireNotNull(mediaItem.toPlaybackRequestOrNull()) {
                "Missing Subsonic playback request metadata for ${mediaItem.mediaId}"
            }

            // Build placeholder URI — real stream URL resolved at play time by ResolvingDataSource
            val placeholderUri = request.toStreamPlaceholderUri(
                streamInstanceId = UUID.randomUUID().toString(),
            )

            // Resolve artwork URL using canonical endpoint (first by order) so
            // CoverArtEndpointInterceptor can rewrite it to the current best endpoint at load time
            val resolvedArtworkUri = request.artworkUri ?: request.coverArtId?.let { coverArtId ->
                runCatching {
                    val canonical = endpointSelector.getCanonicalEndpoint(request.serverId)
                    val candidates = subsonicRepository.buildCoverArtRequest(request.serverId, coverArtId, 720).candidates
                    val canonicalUrl = canonical?.let { c ->
                        val host = c.baseUrl.trimEnd('/').substringAfter("://")
                        candidates.find { it.url.contains(host) }?.url
                    }
                    canonicalUrl ?: candidates.firstOrNull()?.url
                }.getOrNull()
            }
            val finalRequest = if (resolvedArtworkUri != null && request.artworkUri == null) {
                request.copy(artworkUri = resolvedArtworkUri)
            } else {
                request
            }

            return MediaItem.Builder()
                .setMediaId(finalRequest.songId)
                .setUri(placeholderUri)
                .setMimeType(finalRequest.mimeType)
                .setMediaMetadata(finalRequest.toMediaMetadata())
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(placeholderUri)
                        .setExtras(finalRequest.toBundle())
                        .build(),
                )
                .build()
        }
    }

    private inner class StreamPrefetchListener : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            syncCurrentStreamPrefetch()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrentStreamPrefetch()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncCurrentStreamPrefetch()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            syncCurrentStreamPrefetch()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncCurrentStreamPrefetch()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncCurrentStreamPrefetch()
        }
    }

    private inner class NotificationMediaButtonListener : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncMediaButtonPreferences()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncMediaButtonPreferences()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncMediaButtonPreferences()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncMediaButtonPreferences()
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            syncMediaButtonPreferences()
        }
    }

    private inner class PlaybackRecoveryListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            syncSoundBalancingEffect(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            playbackFailureReporter.onMediaItemTransition(
                mediaItem?.toPlaybackRequestOrNull()?.let { request ->
                    PlaybackRecoveryItemKey(request.serverId, request.songId)
                },
            )
            syncAutomaticTranscodeSessionExtras()
            clearPlaybackFailureReport()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val activePlaceholderUris = buildSet {
                val activePlayer = player ?: return@buildSet
                repeat(activePlayer.mediaItemCount) { index ->
                    activePlayer.getMediaItemAt(index).localConfiguration?.uri?.toString()?.let(::add)
                }
            }
            openedStreams.keys.removeIf { it !in activePlaceholderUris }
            forcedTranscodes.keys.removeIf { it !in activePlaceholderUris }
            syncAutomaticTranscodeSessionExtras()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                clearPlaybackFailureReport()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val activePlayer = player ?: return
            if (
                error.isSystemAlacDecoderFailure() &&
                alacDecoderPolicy.markAutoSystemDecoderFailed()
            ) {
                val currentIndex = activePlayer.currentMediaItemIndex
                val resumePositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
                val wasPlaying = activePlayer.playWhenReady
                renderersFactory.refreshAlacDecoderPolicy(activePlayer)
                activePlayer.prepare()
                if (currentIndex != C.INDEX_UNSET) {
                    activePlayer.seekTo(currentIndex, resumePositionMs)
                }
                activePlayer.playWhenReady = wasPlaying
                return
            }

            val failedItem = activePlayer.currentMediaItem?.toQueueItemOrNull()
            val failureKind = classifyPlaybackFailure(
                errorCode = error.errorCode,
                suffix = failedItem?.suffix,
                contentType = failedItem?.contentType,
            )
            val currentItem = activePlayer.currentMediaItem
            val currentRequest = currentItem?.toPlaybackRequestOrNull()
            val placeholderUri = currentItem?.localConfiguration?.uri?.toString()
            val openedStream = currentItem?.openedStream()
            val requestedQuality = currentRequest?.let { request ->
                if (request.isCached || request.localPath != null) {
                    StreamQuality.entries.find { quality -> quality.maxBitRate == request.maxBitRate }
                        ?: StreamQuality.ORIGINAL
                } else {
                    cachedPlaybackPrefs?.let { prefs -> request.requestedStreamQuality(prefs) }
                        ?: StreamQuality.entries.find { quality -> quality.maxBitRate == request.maxBitRate }
                        ?: StreamQuality.ORIGINAL
                }
            }
            if (
                shouldApplyOriginalPlaybackFailureAction(
                    kind = failureKind,
                    openedStreamQuality = openedStream?.quality,
                    requestedStreamQuality = requestedQuality,
                    forcedTranscode = openedStream?.forcedTranscode == true ||
                        (placeholderUri != null && forcedTranscodes.containsKey(placeholderUri)),
                    sourceBitRate = currentRequest?.sourceBitRate,
                    sourceSuffix = currentRequest?.suffix,
                    sourceContentType = currentRequest?.mimeType,
                    localStreamQuality = currentRequest?.localStreamQuality,
                )
            ) {
                when (
                    cachedPlaybackPrefs?.originalPlaybackFailureAction
                        ?: OriginalPlaybackFailureAction.STOP
                ) {
                    OriginalPlaybackFailureAction.STOP -> {
                        activePlayer.playWhenReady = false
                        schedulePlaybackFailureReport(activePlayer, error)
                        return
                    }
                    OriginalPlaybackFailureAction.SKIP -> {
                        val failedRequest = currentRequest
                        if (failedRequest == null) {
                            activePlayer.playWhenReady = false
                            schedulePlaybackFailureReport(activePlayer, error)
                            return
                        }
                        val failureCount = playbackFailureReporter.recordOriginalPlaybackSkipFailure()
                        val failedKey = PlaybackRecoveryItemKey(
                            serverId = failedRequest.serverId,
                            songId = failedRequest.songId,
                        )
                        playerScope.launch {
                            val skipped = try {
                                playbackFailureReporter.requestOriginalPlaybackSkip(
                                    failedItem = failedKey,
                                    failureCount = failureCount,
                                )
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                Log.w(
                                    PLAYBACK_RECOVERY_LOG_TAG,
                                    "Failed to navigate playback recovery queue",
                                    exception,
                                )
                                false
                            }
                            val activeRequest = activePlayer.currentMediaItem?.toPlaybackRequestOrNull()
                            val remainsOnFailedItem = activeRequest?.serverId == failedKey.serverId &&
                                activeRequest.songId == failedKey.songId
                            if (skipped || !remainsOnFailedItem) {
                                clearPlaybackFailureReport()
                                return@launch
                            }
                            activePlayer.playWhenReady = false
                            schedulePlaybackFailureReport(activePlayer, error)
                        }
                        return
                    }
                    OriginalPlaybackFailureAction.AUTO_TRANSCODE -> {
                        if (retryCurrentItemWithForcedTranscode(activePlayer)) {
                            clearPlaybackFailureReport()
                            return
                        }
                        activePlayer.playWhenReady = false
                        schedulePlaybackFailureReport(activePlayer, error)
                        return
                    }
                }
            }
            schedulePlaybackFailureReport(activePlayer, error)
            if (!error.shouldRetryNextEndpoint()) {
                return
            }

            val currentIndex = activePlayer.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET) {
                return
            }

            // Invalidate the failed endpoint so the resolver picks a different one on retry
            val request = activePlayer.currentMediaItem?.toPlaybackRequestOrNull()
            if (request != null) {
                val activeEndpointId = endpointSelector.getActiveEndpointId(request.serverId)
                if (activeEndpointId != null) {
                    endpointSelector.invalidate(request.serverId, activeEndpointId)
                }
            }

            val resumePositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = activePlayer.playWhenReady
            activePlayer.prepare()
            activePlayer.seekTo(currentIndex, resumePositionMs)
            activePlayer.playWhenReady = wasPlaying
        }
    }

    private fun retryCurrentItemWithForcedTranscode(activePlayer: ExoPlayer): Boolean {
        val currentIndex = activePlayer.currentMediaItemIndex
        val mediaItem = activePlayer.currentMediaItem ?: return false
        val request = mediaItem.toPlaybackRequestOrNull() ?: return false
        val sourceUri = mediaItem.localConfiguration?.uri?.toString() ?: return false
        val usesLocalSource = request.isCached || request.localPath != null
        val hasCompleteStreamCache = mediaItem.openedStream()?.let { openedStream ->
            streamCacheRepository.isCacheKeyFullyCached(openedStream.cacheKey)
        } == true
        val forcedTranscode = ForcedTranscode(
            serverId = request.serverId,
            songId = request.songId,
        )
        val forcedTranscodeCacheKey = buildForcedTranscodeStreamCacheKey(
            serverId = forcedTranscode.serverId,
            songId = forcedTranscode.songId,
            quality = forcedTranscode.quality,
            format = forcedTranscode.format,
        )
        val hasCompleteForcedTranscodeCache = streamCacheRepository.isCacheKeyFullyCached(
            forcedTranscodeCacheKey,
        )
        if (
            !canRetryOriginalWithForcedTranscode(
                usesLocalSource = usesLocalSource,
                hasCompleteStreamCache = hasCompleteStreamCache,
                hasCompleteForcedTranscodeCache = hasCompleteForcedTranscodeCache,
                isOfflineDegraded = endpointSelector.isOfflineDegraded(request.serverId),
            )
        ) {
            return false
        }
        if (forcedTranscodes.putIfAbsent(sourceUri, forcedTranscode) != null) return false
        syncAutomaticTranscodeSessionExtras()

        val resumePositionMs = activePlayer.logicalCurrentPositionMs().toWholeSecondOffsetMs()
        val resumePlan = planForcedTranscodeResume(
            resumePositionMs = resumePositionMs,
            hasCompleteForcedTranscodeCache = hasCompleteForcedTranscodeCache,
        )
        val wasPlaying = activePlayer.playWhenReady
        openedStreams.remove(sourceUri)
        activePlayer.stop()
        updateActiveServerSeek(
            resumePlan.serverOffsetMs?.let { serverOffsetMs ->
                ActiveServerSeek(
                    mediaItemIndex = currentIndex,
                    serverId = request.serverId,
                    songId = request.songId,
                    offsetMs = serverOffsetMs,
                    streamQuality = StreamQuality.KBPS_320,
                )
            },
        )
        activePlayer.seekTo(currentIndex, resumePlan.playerPositionMs)
        activePlayer.prepare()
        activePlayer.playWhenReady = wasPlaying
        return true
    }

    private fun syncAutomaticTranscodeSessionExtras() {
        val activeItem = player?.currentMediaItem
        val placeholderUri = activeItem?.localConfiguration?.uri?.toString()
        val forcedTranscode = placeholderUri?.let(forcedTranscodes::get)
        val extras = if (activeItem != null && forcedTranscode != null) {
            Bundle().apply {
                putString(EXTRA_AUTO_TRANSCODE_MEDIA_ID, activeItem.mediaId)
                putString(EXTRA_AUTO_TRANSCODE_FORMAT, forcedTranscode.format.uppercase(java.util.Locale.ROOT))
                forcedTranscode.quality.maxBitRate?.let { maxBitRate ->
                    putInt(EXTRA_AUTO_TRANSCODE_MAX_BIT_RATE, maxBitRate)
                }
            }
        } else {
            Bundle.EMPTY
        }
        mediaSession?.setSessionExtras(extras)
    }

    private fun schedulePlaybackFailureReport(
        activePlayer: ExoPlayer,
        error: PlaybackException,
    ) {
        val failedMediaId = activePlayer.currentMediaItem?.mediaId
        if (
            playbackFailureReportJob?.isActive == true &&
            pendingPlaybackFailureMediaId == failedMediaId &&
            pendingPlaybackFailureErrorCode == error.errorCode
        ) {
            return
        }
        playbackFailureReportJob?.cancel()
        pendingPlaybackFailureMediaId = failedMediaId
        pendingPlaybackFailureErrorCode = error.errorCode
        val failedItem = activePlayer.currentMediaItem?.toQueueItemOrNull()
        playbackFailureReportJob = playerScope.launch {
            delay(PLAYBACK_FAILURE_REPORT_DELAY_MS)
            if (player !== activePlayer) return@launch
            if (activePlayer.currentMediaItem?.mediaId != failedMediaId) return@launch
            if (activePlayer.playerError?.errorCode != error.errorCode) return@launch
            playbackFailureReporter.report(
                kind = classifyPlaybackFailure(
                    errorCode = error.errorCode,
                    suffix = failedItem?.suffix,
                    contentType = failedItem?.contentType,
                ),
                trackTitle = failedItem?.title,
                formatLabel = failedItem?.playbackFormatLabel(),
            )
        }
    }

    private fun clearPlaybackFailureReport() {
        playbackFailureReportJob?.cancel()
        playbackFailureReportJob = null
        pendingPlaybackFailureMediaId = null
        pendingPlaybackFailureErrorCode = null
        playbackFailureReporter.clear()
    }

    private inner class PlayQueueSaveListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            savePlayQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) savePlayQueue()
        }
    }

    private fun restoreOriginalTitle() {
        val activePlayer = player ?: return
        val title = originalMediaTitle ?: return
        val savedId = originalMediaId
        originalMediaTitle = null
        originalMediaId = null
        val item = activePlayer.currentMediaItem ?: return
        if (item.mediaId != savedId || item.mediaMetadata.title == title) return
        val restored = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon().setTitle(title).build(),
            )
            .build()
        activePlayer.replaceMediaItem(activePlayer.currentMediaItemIndex, restored)
    }

    private fun List<LyricLine>.binarySearchLastBefore(positionMs: Long): Int {
        var low = 0
        var high = size - 1
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
        return result
    }

    private fun syncSoundBalancingEffect(audioSessionId: Int) {
        val targetSessionId = audioSessionId.takeIf { it != C.AUDIO_SESSION_ID_UNSET && it != 0 }
        val targetGainMb = soundBalancingMode.targetGainMb
        if (targetGainMb == null || targetSessionId == null) {
            releaseSoundBalancingEffect()
            return
        }

        if (loudnessEnhancer == null || loudnessEnhancerSessionId != targetSessionId) {
            releaseSoundBalancingEffect()
            loudnessEnhancer = createLoudnessEnhancer(targetSessionId) ?: return
            loudnessEnhancerSessionId = targetSessionId
        }

        runCatching {
            loudnessEnhancer?.setTargetGain(targetGainMb)
            loudnessEnhancer?.enabled = true
        }.onFailure {
            releaseSoundBalancingEffect()
        }
    }

    @Synchronized
    private fun releaseSoundBalancingEffect() {
        runCatching {
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
        loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun createLoudnessEnhancer(audioSessionId: Int): LoudnessEnhancer? {
        return runCatching {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(soundBalancingMode.targetGainMb ?: 0)
                enabled = true
            }
        }.getOrNull()
    }
}

@UnstableApi
private fun PlaybackException.isSystemAlacDecoderFailure(): Boolean {
    val exoError = this as? ExoPlaybackException ?: return false
    if (exoError.type != ExoPlaybackException.TYPE_RENDERER) return false
    if (exoError.rendererName == BundledAlacAudioRenderer.RENDERER_NAME) return false
    if (!MimeTypes.AUDIO_ALAC.equals(exoError.rendererFormat?.sampleMimeType, ignoreCase = true)) {
        return false
    }
    return errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
}

private fun PlaybackRequest.toSong(): Song {
    return Song(
        id = songId,
        parentId = null,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = coverArtId,
        durationSeconds = durationMs?.div(1_000L)?.toInt(),
        track = track,
        discNumber = discNumber,
        year = null,
        genre = null,
        bitRate = sourceBitRate ?: bitRate,
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = mimeType,
        sizeBytes = null,
        path = null,
        created = null,
    )
}

private fun PlaybackException.shouldRetryNextEndpoint(): Boolean {
    return causeSequence()
        .filterIsInstance<IOException>()
        .any { exception ->
            exception is UnknownHostException ||
                exception is ConnectException ||
                exception is SocketTimeoutException ||
                exception is NoRouteToHostException
        }
}

private fun Throwable.causeSequence(): Sequence<Throwable> = sequence {
    var current: Throwable? = this@causeSequence
    while (current != null) {
        yield(current)
        current = current.cause
    }
}

private fun successSessionResult(): ListenableFuture<SessionResult> =
    SettableFuture.create<SessionResult>().apply {
        set(SessionResult(SessionResult.RESULT_SUCCESS))
    }
