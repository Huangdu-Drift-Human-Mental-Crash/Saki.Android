package org.hdhmc.saki.data.download

import java.net.ConnectException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.hdhmc.saki.domain.model.CacheStorageSummary
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDownloadRunnerTest {
    @Test
    fun `runner captures quality deduplicates skips and retries only unfinished songs`() = runTest {
        val songs = listOf(song("a"), song("a"), song("b"), song("c"), song("d"))
        val cachedRepository = FakeCachedSongRepository(downloadDelayMs = 100L).apply {
            downloaded["b"] = cachedSong("b", StreamQuality.ORIGINAL)
            failuresRemaining["c"] = 1
        }
        val runner = PlaylistDownloadRunner(cachedRepository)
        val input = workInput(total = 4, quality = StreamQuality.KBPS_128)
        val firstProgress = mutableListOf<PlaylistDownloadProgress>()

        val first = runner.run(input, songs, firstProgress::add)

        assertEquals(4, first.progress.totalSongCount)
        assertEquals(4, first.progress.processedSongCount)
        assertEquals(2, first.progress.downloadedSongCount)
        assertEquals(1, first.progress.skippedSongCount)
        assertEquals(1, first.progress.failedSongCount)
        assertEquals(1, first.retryableFailureCount)
        assertEquals(listOf(StreamQuality.KBPS_128), cachedRepository.qualitiesFor("a"))
        assertEquals(0, cachedRepository.attemptCount("b"))
        assertEquals(1, cachedRepository.attemptCount("a"))
        assertEquals(1, cachedRepository.attemptCount("d"))
        assertEquals(firstProgress.map { it.processedSongCount }.sorted(), firstProgress.map { it.processedSongCount })
        assertTrue(cachedRepository.maxActive.get() <= 2)

        val second = runner.run(input, songs) {}

        assertEquals(1, second.progress.downloadedSongCount)
        assertEquals(3, second.progress.skippedSongCount)
        assertEquals(0, second.progress.failedSongCount)
        assertEquals(1, cachedRepository.attemptCount("a"))
        assertEquals(2, cachedRepository.attemptCount("c"))
        assertEquals(1, cachedRepository.attemptCount("d"))
    }

    @Test
    fun `cancellation stops the two bounded worker loops`() = runTest {
        val cachedRepository = FakeCachedSongRepository(downloadDelayMs = 10_000L)
        val songs = (1..20).map { song("song-$it") }
        val runner = PlaylistDownloadRunner(cachedRepository)
        val job = launch {
            runner.run(workInput(total = 20, quality = StreamQuality.KBPS_320), songs) {}
        }

        runCurrent()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(cachedRepository.totalAttemptCount() <= 2)
        assertTrue(cachedRepository.downloaded.isEmpty())
    }
}

private class FakeCachedSongRepository(
    private val downloadDelayMs: Long,
) : CachedSongRepository {
    val downloaded = ConcurrentHashMap<String, CachedSong>()
    val failuresRemaining = ConcurrentHashMap<String, Int>()
    val maxActive = AtomicInteger(0)
    private val active = AtomicInteger(0)
    private val attempts = ConcurrentHashMap<String, AtomicInteger>()
    private val attemptedQualities = ConcurrentHashMap<String, CopyOnWriteArrayList<StreamQuality>>()

    override fun observeCachedSongs(): Flow<List<CachedSong>> = flowOf(downloaded.values.toList())

    override suspend fun getCachedSong(serverId: Long, songId: String): CachedSong? = downloaded[songId]

    override suspend fun getPlayableCachedSong(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): CachedSong? = downloaded[songId]?.takeIf { it.quality.ordinal <= preferredQuality.ordinal }

    override suspend fun getPlayableCachedSongs(
        serverId: Long,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong> = downloaded.filterValues { it.quality.ordinal <= preferredQuality.ordinal }

    override suspend fun getPlayableCachedSongs(
        serverId: Long,
        songIds: List<String>,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong> = downloaded.filter { (id, cached) ->
        id in songIds && cached.quality.ordinal <= preferredQuality.ordinal
    }

    override suspend fun cacheSong(serverId: Long, song: org.hdhmc.saki.domain.model.Song): CachedSong {
        return cacheSong(serverId, song, StreamQuality.ORIGINAL)
    }

    override suspend fun cacheSong(
        serverId: Long,
        song: org.hdhmc.saki.domain.model.Song,
        quality: StreamQuality,
    ): CachedSong {
        attempts.computeIfAbsent(song.id) { AtomicInteger() }.incrementAndGet()
        attemptedQualities.computeIfAbsent(song.id) { CopyOnWriteArrayList() } += quality
        val activeCount = active.incrementAndGet()
        maxActive.updateAndGet { current -> maxOf(current, activeCount) }
        try {
            delay(downloadDelayMs)
            val failures = failuresRemaining[song.id] ?: 0
            if (failures > 0) {
                failuresRemaining[song.id] = failures - 1
                throw ConnectException("offline")
            }
            return cachedSong(song.id, quality).also { downloaded[song.id] = it }
        } finally {
            active.decrementAndGet()
        }
    }

    override suspend fun deleteCachedSong(cacheId: String) {
        downloaded.entries.removeIf { it.value.cacheId == cacheId }
    }

    override suspend fun clearCachedSongs(serverId: Long?): Int = downloaded.size.also { downloaded.clear() }

    override suspend fun getCacheStorageSummary(serverId: Long?): CacheStorageSummary = CacheStorageSummary(
        downloadedSongCount = downloaded.size,
        downloadedBytes = downloaded.values.sumOf(CachedSong::fileSizeBytes),
    )

    fun attemptCount(songId: String): Int = attempts[songId]?.get() ?: 0

    fun totalAttemptCount(): Int = attempts.values.sumOf(AtomicInteger::get)

    fun qualitiesFor(songId: String): List<StreamQuality> = attemptedQualities[songId].orEmpty()
}

private fun cachedSong(id: String, quality: StreamQuality) = CachedSong(
    cacheId = "1:$id",
    serverId = 1L,
    songId = id,
    title = "Song $id",
    album = null,
    albumId = null,
    artist = null,
    artistId = null,
    coverArtId = null,
    coverArtPath = null,
    localPath = "offline/$id",
    durationSeconds = 10,
    track = null,
    discNumber = null,
    suffix = "mp3",
    contentType = "audio/mpeg",
    bitRateKbps = quality.maxBitRate,
    sampleRate = null,
    quality = quality,
    fileSizeBytes = 1_000L,
    downloadedAt = 1L,
)

private fun workInput(total: Int, quality: StreamQuality) = PlaylistDownloadWorkInput(
    serverId = 1L,
    planId = "00000000-0000-0000-0000-000000000001",
    playlistId = "playlist",
    title = "Playlist",
    quality = quality,
    totalSongCount = total,
    estimatedAdditionalBytes = total * 1_000L,
    createdAtMillis = 1L,
)
