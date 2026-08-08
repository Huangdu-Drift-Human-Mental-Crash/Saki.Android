package org.hdhmc.saki.data.repository

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import org.hdhmc.saki.data.remote.EndpointSelector
import org.hdhmc.saki.data.remote.HTTP_USER_AGENT
import org.hdhmc.saki.data.remote.NetworkType
import org.hdhmc.saki.data.remote.NetworkTypeProvider
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.CollectionStreamCacheEstimate
import org.hdhmc.saki.domain.model.CollectionStreamCacheTask
import org.hdhmc.saki.domain.model.CollectionStreamCacheTaskStatus
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamCacheProgress
import org.hdhmc.saki.domain.model.StreamCacheSummary
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.StreamCacheRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import org.hdhmc.saki.playback.ConfigurableLeastRecentlyUsedCacheEvictor
import org.hdhmc.saki.playback.STREAM_CACHE_EOF_LENGTH_METADATA_KEY
import org.hdhmc.saki.playback.StreamCacheEofTrackingDataSource
import org.hdhmc.saki.playback.StreamCacheWriteCoordinator
import org.hdhmc.saki.playback.buildStreamCacheKey
import org.hdhmc.saki.playback.estimateSongStreamBytes
import org.hdhmc.saki.playback.parseStreamCacheKey
import dagger.Lazy
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Singleton
@UnstableApi
class DefaultStreamCacheRepository @Inject constructor(
    private val streamCacheProvider: Lazy<SimpleCache>,
    private val cacheEvictor: ConfigurableLeastRecentlyUsedCacheEvictor,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subsonicRepository: SubsonicRepository,
    private val networkTypeProvider: NetworkTypeProvider,
    private val endpointSelector: EndpointSelector,
    private val okHttpClient: OkHttpClient,
    private val streamCacheWriteCoordinator: StreamCacheWriteCoordinator,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StreamCacheRepository {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val cacheVersion = MutableStateFlow(0L)
    private val collectionCacheTask = MutableStateFlow<CollectionStreamCacheTask?>(null)
    private val snapshotRefreshMutex = Mutex()
    private val requestedSnapshotRefreshLock = Any()
    private val collectionTaskLock = Any()
    private var lastSnapshot = StreamCacheSnapshot()
    private var hasAppliedInitialCacheSize = false
    private var requestedSnapshotRefreshJob: Job? = null
    private var collectionCacheJob: Job? = null
    private var activeCollectionCacheWriter: CacheWriter? = null
    private var collectionTaskGeneration = 0L
    private val streamCache: SimpleCache
        get() = streamCacheProvider.get()
    private val collectionCacheDataSourceFactory: CacheDataSource.Factory by lazy {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(HTTP_USER_AGENT)
        val eofTrackingFactory = DataSource.Factory {
            StreamCacheEofTrackingDataSource(
                upstream = httpFactory.createDataSource(),
                onEof = ::recordStreamCacheEof,
            )
        }
        CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(eofTrackingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    init {
        scope.launch {
            playbackPreferencesRepository.observePreferences()
                .map { preferences -> preferences.streamCacheSizeBytes }
                .distinctUntilChanged()
                .collectLatest { maxBytes ->
                    cacheEvictor.updateMaxBytes(maxBytes)
                    if (hasAppliedInitialCacheSize) {
                        refreshSnapshot(forceEmit = true)
                    } else {
                        hasAppliedInitialCacheSize = true
                    }
                }
        }
        scope.launch {
            delay(STREAM_CACHE_SNAPSHOT_INITIAL_DELAY_MS)
            refreshSnapshot(forceEmit = true)
            while (isActive) {
                delay(STREAM_CACHE_SNAPSHOT_REFRESH_MS)
                refreshSnapshot()
            }
        }
    }

    override fun observeCacheVersion(): Flow<Long> = cacheVersion.asStateFlow()

    override fun observeCollectionCacheTask(): StateFlow<CollectionStreamCacheTask?> =
        collectionCacheTask.asStateFlow()

    override fun requestSnapshotRefresh() {
        val refreshJob = scope.launch {
            delay(STREAM_CACHE_SNAPSHOT_REQUEST_DEBOUNCE_MS)
            try {
                refreshSnapshot()
            } finally {
                synchronized(requestedSnapshotRefreshLock) {
                    if (requestedSnapshotRefreshJob === coroutineContext[Job]) {
                        requestedSnapshotRefreshJob = null
                    }
                }
            }
        }
        synchronized(requestedSnapshotRefreshLock) {
            requestedSnapshotRefreshJob?.cancel()
            requestedSnapshotRefreshJob = refreshJob
        }
    }

    override fun buildCacheKey(
        serverId: Long,
        songId: String,
        quality: StreamQuality,
    ): String = buildStreamCacheKey(serverId, songId, quality)

    override fun findCachedQualityKey(serverId: Long, songId: String, preferredQuality: StreamQuality): String? {
        val byQuality = snapshotForQueries().cachedSongIdsByServerAndQuality[serverId] ?: return null
        // Exact match first
        if (byQuality[preferredQuality.storageKey]?.contains(songId) == true) {
            return preferredQuality.storageKey
        }
        // Only consider equal or higher qualities (ORIGINAL is highest, then by maxBitRate desc)
        val preferredIndex = StreamQuality.entries.indexOf(preferredQuality)
        for (i in 0..preferredIndex) {
            val key = StreamQuality.entries[i].storageKey
            if (byQuality[key]?.contains(songId) == true) return key
        }
        return null
    }

    override fun getStreamCacheProgress(
        serverId: Long,
        songId: String,
        quality: StreamQuality,
    ): StreamCacheProgress? {
        val cacheKey = buildStreamCacheKey(serverId, songId, quality)
        val metadata = streamCache.getContentMetadata(cacheKey)
        val contentLength = completeStreamLength(metadata) ?: return null
        val cachedPrefixBytes = streamCache.getCachedLength(cacheKey, 0L, contentLength)
            .takeIf { length -> length > 0L }
            ?.coerceAtMost(contentLength)
            ?: return null
        return StreamCacheProgress(
            cachedPrefixBytes = cachedPrefixBytes,
            contentLengthBytes = contentLength,
        )
    }

    override suspend fun estimateCollectionCache(
        serverId: Long,
        songs: List<Song>,
    ): CollectionStreamCacheEstimate = withContext(ioDispatcher) {
        val uniqueSongs = songs.distinctBy(Song::id)
        val preferences = playbackPreferencesRepository.getPreferences()
        val quality = when {
            !preferences.adaptiveQualityEnabled -> preferences.streamQuality
            networkTypeProvider.networkType.value == NetworkType.WIFI -> preferences.wifiStreamQuality
            else -> preferences.mobileStreamQuality
        }
        var alreadyCachedSongCount = 0
        var unknownSizeSongCount = 0
        var estimatedCollectionBytes = 0L
        var estimatedAdditionalBytes = 0L

        uniqueSongs.forEach { song ->
            val retainedCacheKey = fullyCachedKeyAtPreferredQuality(serverId, song.id, quality)
            if (retainedCacheKey != null) {
                alreadyCachedSongCount += 1
                estimatedCollectionBytes += cachedBytes(retainedCacheKey)
                return@forEach
            }

            val estimatedBytes = estimateSongStreamBytes(song, quality)
            if (estimatedBytes == null) {
                unknownSizeSongCount += 1
            } else {
                estimatedCollectionBytes += estimatedBytes
            }

            if (estimatedBytes != null) {
                val existingBytes = cachedBytes(buildStreamCacheKey(serverId, song.id, quality))
                estimatedAdditionalBytes += (estimatedBytes - existingBytes).coerceAtLeast(0L)
            }
        }

        CollectionStreamCacheEstimate(
            quality = quality,
            songCount = uniqueSongs.size,
            alreadyCachedSongCount = alreadyCachedSongCount,
            unknownSizeSongCount = unknownSizeSongCount,
            estimatedCollectionBytes = estimatedCollectionBytes,
            estimatedAdditionalBytes = estimatedAdditionalBytes,
            currentCacheBytes = streamCache.cacheSpace,
            cacheLimitBytes = preferences.streamCacheSizeBytes,
        )
    }

    override fun startCollectionCache(
        sourceKey: String,
        title: String,
        serverId: Long,
        songs: List<Song>,
        estimate: CollectionStreamCacheEstimate,
    ) {
        if (endpointSelector.isOfflineDegraded(serverId)) return

        val uniqueSongs = songs.distinctBy(Song::id)
        val generation: Long
        synchronized(collectionTaskLock) {
            collectionTaskGeneration += 1
            generation = collectionTaskGeneration
            activeCollectionCacheWriter?.cancel()
            activeCollectionCacheWriter = null
            collectionCacheJob?.cancel()
            collectionCacheTask.value = CollectionStreamCacheTask(
                sourceKey = sourceKey,
                title = title,
                quality = estimate.quality,
                totalSongCount = uniqueSongs.size,
                estimatedAdditionalBytes = estimate.estimatedAdditionalBytes,
            )
            collectionCacheJob = scope.launch {
                cacheCollection(
                    generation = generation,
                    serverId = serverId,
                    songs = uniqueSongs,
                    estimate = estimate,
                )
            }
        }
    }

    override fun cancelCollectionCache() {
        requestCollectionCacheCancellation()
    }

    private fun requestCollectionCacheCancellation(): Job? {
        synchronized(collectionTaskLock) {
            collectionTaskGeneration += 1
            activeCollectionCacheWriter?.cancel()
            activeCollectionCacheWriter = null
            val cancelledJob = collectionCacheJob
            cancelledJob?.cancel()
            collectionCacheJob = null
            collectionCacheTask.update { task ->
                task?.takeIf { it.status == CollectionStreamCacheTaskStatus.RUNNING }
                    ?.copy(
                        status = CollectionStreamCacheTaskStatus.CANCELLED,
                        currentSongTitle = null,
                    )
                    ?: task
            }
            return cancelledJob
        }
    }

    override suspend fun getStreamCacheSummary(
        serverId: Long?,
        quality: StreamQuality?,
    ): StreamCacheSummary = withContext(ioDispatcher) {
        val snapshot = snapshotForQueries()
        if (serverId == null) {
            StreamCacheSummary(
                cachedSongIds = snapshot.cachedSongIdsByServerAndQuality.values
                    .flatMap { byQuality -> byQuality.values }
                    .flattenToSet(),
                bytes = snapshot.bytesByServer.values.sum(),
            )
        } else {
            StreamCacheSummary(
                cachedSongIds = snapshot.cachedSongIdsByServerAndQuality[serverId]
                    ?.let { byQuality ->
                        quality?.let { byQuality[it.storageKey].orEmpty() }
                            ?: byQuality.values.flattenToSet()
                    }
                    .orEmpty(),
                bytes = snapshot.bytesByServer[serverId] ?: 0L,
            )
        }
    }

    override suspend fun clearStreamCache(serverId: Long?): Int = withContext(ioDispatcher) {
        requestCollectionCacheCancellation()?.join()
        streamCacheWriteCoordinator.withWriter {
            val matchingKeys = streamCache.keys
                .mapNotNull { key ->
                    val parsed = parseStreamCacheKey(key) ?: return@mapNotNull null
                    key.takeIf { serverId == null || parsed.serverId == serverId }
                }

            matchingKeys.forEach(streamCache::removeResource)
            refreshSnapshot(forceEmit = true)
            matchingKeys.size
        }
    }

    private suspend fun cacheCollection(
        generation: Long,
        serverId: Long,
        songs: List<Song>,
        estimate: CollectionStreamCacheEstimate,
    ) {
        var processedSongCount = 0
        var failedSongCount = 0
        var retainedSongCount = 0
        var newlyCachedBytes = 0L

        try {
            touchCachedCollectionTargets(
                generation = generation,
                serverId = serverId,
                songs = songs,
                preferredQuality = estimate.quality,
            )

            songs.forEach { song ->
                currentCoroutineContext().ensureActive()
                if (!isCurrentCollectionTask(generation)) return

                updateCollectionTask(generation) { task ->
                    task.copy(currentSongTitle = song.title)
                }

                val wasAlreadyCached = isFullyCachedAtPreferredQuality(
                    serverId = serverId,
                    songId = song.id,
                    preferredQuality = estimate.quality,
                )
                if (wasAlreadyCached) {
                    retainedSongCount += 1
                } else {
                    val bytesBefore = cachedBytes(buildStreamCacheKey(serverId, song.id, estimate.quality))
                    val cached = runCatching {
                        cacheSongToStreamCache(
                            generation = generation,
                            serverId = serverId,
                            song = song,
                            quality = estimate.quality,
                            completedBytes = newlyCachedBytes,
                        )
                    }.getOrElse { throwable ->
                        if (throwable is CancellationException) throw throwable
                        false
                    }
                    val bytesAfter = cachedBytes(buildStreamCacheKey(serverId, song.id, estimate.quality))
                    newlyCachedBytes += (bytesAfter - bytesBefore).coerceAtLeast(0L)
                    if (cached) {
                        retainedSongCount += 1
                    } else {
                        failedSongCount += 1
                    }
                }

                processedSongCount += 1
                updateCollectionTask(generation) { task ->
                    task.copy(
                        processedSongCount = processedSongCount,
                        cachedSongCount = retainedSongCount,
                        failedSongCount = failedSongCount,
                        cachedBytes = newlyCachedBytes,
                    )
                }
                requestSnapshotRefresh()
            }

            val finalRetainedSongCount = songs.count { song ->
                isFullyCachedAtPreferredQuality(serverId, song.id, estimate.quality)
            }
            updateCollectionTask(generation) { task ->
                task.copy(
                    processedSongCount = songs.size,
                    cachedSongCount = finalRetainedSongCount,
                    failedSongCount = failedSongCount,
                    currentSongTitle = null,
                    cachedBytes = newlyCachedBytes,
                    status = if (failedSongCount == songs.size && songs.isNotEmpty()) {
                        CollectionStreamCacheTaskStatus.FAILED
                    } else {
                        CollectionStreamCacheTaskStatus.COMPLETED
                    },
                )
            }
            refreshSnapshot(forceEmit = true)
        } catch (exception: CancellationException) {
            if (isCurrentCollectionTask(generation)) {
                updateCollectionTask(generation) { task ->
                    task.copy(
                        status = CollectionStreamCacheTaskStatus.CANCELLED,
                        currentSongTitle = null,
                    )
                }
            }
            throw exception
        } finally {
            synchronized(collectionTaskLock) {
                if (collectionTaskGeneration == generation) {
                    activeCollectionCacheWriter = null
                    collectionCacheJob = null
                }
            }
        }
    }

    private suspend fun cacheSongToStreamCache(
        generation: Long,
        serverId: Long,
        song: Song,
        quality: StreamQuality,
        completedBytes: Long,
    ): Boolean {
        val request = subsonicRepository.buildStreamRequest(
            serverId = serverId,
            songId = song.id,
            maxBitRate = quality.maxBitRate,
            format = quality.format,
        )
        val cacheKey = buildStreamCacheKey(serverId, song.id, quality)
        var accumulatedNewBytes = 0L
        var lastProgressUpdateMs = 0L

        for (candidate in request.candidates) {
            currentCoroutineContext().ensureActive()
            if (!isCurrentCollectionTask(generation)) return false

            val dataSpec = DataSpec.Builder()
                .setUri(candidate.url)
                .setKey(cacheKey)
                .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                .build()
            val writer = CacheWriter(
                collectionCacheDataSourceFactory.createDataSource(),
                dataSpec,
                null,
            ) { _, _, newBytesCached ->
                accumulatedNewBytes += newBytesCached.coerceAtLeast(0L)
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgressUpdateMs >= COLLECTION_CACHE_PROGRESS_UPDATE_MS) {
                    lastProgressUpdateMs = now
                    updateCollectionTask(generation) { task ->
                        task.copy(cachedBytes = completedBytes + accumulatedNewBytes)
                    }
                }
            }
            synchronized(collectionTaskLock) {
                if (collectionTaskGeneration != generation) {
                    writer.cancel()
                    return false
                }
                activeCollectionCacheWriter = writer
            }

            try {
                streamCacheWriteCoordinator.withWriter {
                    runInterruptible(ioDispatcher) {
                        writer.cache()
                    }
                }
                endpointSelector.recordSuccess(serverId, candidate.endpoint)
                return isFullyCached(cacheKey)
            } catch (exception: IOException) {
                currentCoroutineContext().ensureActive()
                if (!isCurrentCollectionTask(generation)) {
                    throw CancellationException("Collection stream cache task was replaced", exception)
                }
                if (!exception.hasRetryableTransportCause()) {
                    return false
                }
                endpointSelector.invalidate(serverId, candidate.endpoint.id)
            } finally {
                synchronized(collectionTaskLock) {
                    if (activeCollectionCacheWriter === writer) {
                        activeCollectionCacheWriter = null
                    }
                }
            }
        }

        return isFullyCached(cacheKey)
    }

    private fun updateCollectionTask(
        generation: Long,
        transform: (CollectionStreamCacheTask) -> CollectionStreamCacheTask,
    ) {
        synchronized(collectionTaskLock) {
            if (collectionTaskGeneration != generation) return
            val task = collectionCacheTask.value ?: return
            collectionCacheTask.value = transform(task)
        }
    }

    private fun isCurrentCollectionTask(generation: Long): Boolean =
        synchronized(collectionTaskLock) { collectionTaskGeneration == generation }

    private fun isFullyCachedAtPreferredQuality(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): Boolean = fullyCachedKeyAtPreferredQuality(
        serverId = serverId,
        songId = songId,
        preferredQuality = preferredQuality,
    ) != null

    private fun fullyCachedKeyAtPreferredQuality(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): String? {
        val preferredIndex = StreamQuality.entries.indexOf(preferredQuality)
        return (0..preferredIndex)
            .asSequence()
            .map { index -> buildStreamCacheKey(serverId, songId, StreamQuality.entries[index]) }
            .firstOrNull(::isFullyCached)
    }

    private suspend fun touchCachedCollectionTargets(
        generation: Long,
        serverId: Long,
        songs: List<Song>,
        preferredQuality: StreamQuality,
    ) {
        streamCacheWriteCoordinator.withWriter {
            songs.forEach { song ->
                currentCoroutineContext().ensureActive()
                if (!isCurrentCollectionTask(generation)) return@withWriter
                val cacheKey = fullyCachedKeyAtPreferredQuality(
                    serverId = serverId,
                    songId = song.id,
                    preferredQuality = preferredQuality,
                ) ?: return@forEach
                touchCacheResource(cacheKey)
            }
        }
    }

    private fun touchCacheResource(cacheKey: String) {
        streamCache.getCachedSpans(cacheKey).toList().forEach { span ->
            runCatching {
                streamCache.startReadWriteNonBlocking(cacheKey, span.position, span.length)
            }.getOrNull()?.let { touchedSpan ->
                if (!touchedSpan.isCached) {
                    streamCache.releaseHoleSpan(touchedSpan)
                }
            }
        }
    }

    private fun isFullyCached(cacheKey: String): Boolean {
        val completeLength = completeStreamLength(streamCache.getContentMetadata(cacheKey)) ?: return false
        return streamCache.isCached(cacheKey, 0L, completeLength)
    }

    private fun cachedBytes(cacheKey: String): Long =
        streamCache.getCachedSpans(cacheKey).sumOf { span -> span.length }

    private fun recordStreamCacheEof(dataSpec: DataSpec, eofPosition: Long) {
        val cacheKey = dataSpec.key ?: return
        if (eofPosition <= 0L || parseStreamCacheKey(cacheKey) == null) return
        runCatching {
            streamCache.applyContentMetadataMutations(
                cacheKey,
                ContentMetadataMutations().set(STREAM_CACHE_EOF_LENGTH_METADATA_KEY, eofPosition),
            )
        }.onSuccess {
            requestSnapshotRefresh()
        }
    }

    private suspend fun refreshSnapshot(forceEmit: Boolean = false) {
        snapshotRefreshMutex.withLock {
            val snapshot = buildSnapshot()
            if (forceEmit || snapshot != lastSnapshot) {
                lastSnapshot = snapshot
                cacheVersion.update { version -> version + 1 }
            }
        }
    }

    private fun snapshotForQueries(): StreamCacheSnapshot {
        return if (cacheVersion.value == 0L) {
            buildSnapshot()
        } else {
            lastSnapshot
        }
    }

    private fun buildSnapshot(): StreamCacheSnapshot {
        val cachedSongIdsByServerAndQuality = mutableMapOf<Long, MutableMap<String, MutableSet<String>>>()
        val bytesByServer = mutableMapOf<Long, Long>()

        streamCache.keys.forEach { key ->
            val parsed = parseStreamCacheKey(key) ?: return@forEach
            val cachedBytes = streamCache.getCachedSpans(key).sumOf { span -> span.length }
            bytesByServer[parsed.serverId] = (bytesByServer[parsed.serverId] ?: 0L) + cachedBytes

            val completeLength = completeStreamLength(streamCache.getContentMetadata(key))
            val isFullyCached = completeLength != null && streamCache.isCached(key, 0L, completeLength)
            if (isFullyCached && parsed.variantKey == null) {
                cachedSongIdsByServerAndQuality
                    .getOrPut(parsed.serverId) { mutableMapOf() }
                    .getOrPut(parsed.qualityKey) { mutableSetOf() }
                    .add(parsed.songId)
            }
        }

        return StreamCacheSnapshot(
            cachedSongIdsByServerAndQuality = cachedSongIdsByServerAndQuality.mapValues { (_, byQuality) ->
                byQuality.mapValues { (_, ids) -> ids.toSet() }
            },
            bytesByServer = bytesByServer.toMap(),
        )
    }

    private fun completeStreamLength(metadata: ContentMetadata): Long? {
        val contentLength = ContentMetadata.getContentLength(metadata)
            .takeIf { length -> length != C.LENGTH_UNSET.toLong() && length > 0L }
        return contentLength ?: metadata.get(STREAM_CACHE_EOF_LENGTH_METADATA_KEY, C.LENGTH_UNSET.toLong())
            .takeIf { length -> length != C.LENGTH_UNSET.toLong() && length > 0L }
    }
}

private data class StreamCacheSnapshot(
    val cachedSongIdsByServerAndQuality: Map<Long, Map<String, Set<String>>> = emptyMap(),
    val bytesByServer: Map<Long, Long> = emptyMap(),
)

private const val STREAM_CACHE_SNAPSHOT_INITIAL_DELAY_MS = 5_000L
private const val STREAM_CACHE_SNAPSHOT_REQUEST_DEBOUNCE_MS = 250L
private const val STREAM_CACHE_SNAPSHOT_REFRESH_MS = 30_000L
private const val COLLECTION_CACHE_PROGRESS_UPDATE_MS = 250L

internal fun Throwable.hasRetryableTransportCause(): Boolean =
    generateSequence(this) { throwable -> throwable.cause }.any { throwable ->
        throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException ||
            throwable is NoRouteToHostException ||
            throwable is SocketException ||
            throwable is EOFException ||
            throwable.javaClass.name == OKHTTP_STREAM_RESET_EXCEPTION_CLASS
    }

private const val OKHTTP_STREAM_RESET_EXCEPTION_CLASS =
    "okhttp3.internal.http2.StreamResetException"

private fun Collection<Set<String>>.flattenToSet(): Set<String> {
    return mutableSetOf<String>().apply {
        this@flattenToSet.forEach { ids -> addAll(ids) }
    }
}
