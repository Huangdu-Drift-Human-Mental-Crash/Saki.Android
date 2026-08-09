package org.hdhmc.saki.data.download

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hdhmc.saki.data.repository.hasRetryableTransportCause
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.repository.CachedSongRepository

@Singleton
class PlaylistDownloadRunner @Inject constructor(
    private val cachedSongRepository: CachedSongRepository,
) {
    internal suspend fun run(
        input: PlaylistDownloadWorkInput,
        planSongs: List<Song>,
        onProgress: suspend (PlaylistDownloadProgress) -> Unit,
    ): PlaylistDownloadRunResult = coroutineScope {
        val songs = planSongs.distinctBy(Song::id)
        val existing = cachedSongRepository.getPlayableCachedSongs(
            serverId = input.serverId,
            songIds = songs.map(Song::id),
            preferredQuality = input.quality,
        )
        val state = MutableRunState(
            totalSongCount = songs.size,
            processedSongCount = existing.size,
            skippedSongCount = existing.size,
        )
        val stateMutex = Mutex()
        onProgress(state.snapshot())

        val pendingSongs = songs.filterNot { song -> song.id in existing }
        pendingSongs.chunkedForWorkers(MAX_CONCURRENT_TRANSFERS).map { lane ->
            async {
                lane.forEach { song ->
                    stateMutex.withLock {
                        state.activeSongIds += song.id
                        onProgress(state.snapshot())
                    }

                    val result = try {
                        val cached = cachedSongRepository.getPlayableCachedSong(
                            serverId = input.serverId,
                            songId = song.id,
                            preferredQuality = input.quality,
                        )
                        if (cached != null) {
                            SongRunResult.Skipped
                        } else {
                            val downloaded = cachedSongRepository.cacheSong(
                                serverId = input.serverId,
                                song = song,
                                quality = input.quality,
                            )
                            SongRunResult.Downloaded(downloaded.fileSizeBytes)
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        SongRunResult.Failed(exception.hasRetryableTransportCause())
                    }

                    stateMutex.withLock {
                        state.activeSongIds -= song.id
                        state.processedSongCount += 1
                        when (result) {
                            is SongRunResult.Downloaded -> {
                                state.downloadedSongCount += 1
                                state.downloadedBytes += result.bytes
                            }
                            SongRunResult.Skipped -> state.skippedSongCount += 1
                            is SongRunResult.Failed -> {
                                state.failedSongCount += 1
                                if (result.retryable) state.retryableFailureCount += 1
                            }
                        }
                        onProgress(state.snapshot())
                    }
                }
            }
        }.awaitAll()

        PlaylistDownloadRunResult(
            progress = state.snapshot(),
            retryableFailureCount = state.retryableFailureCount,
        )
    }
}

internal data class PlaylistDownloadRunResult(
    val progress: PlaylistDownloadProgress,
    val retryableFailureCount: Int,
)

private sealed interface SongRunResult {
    data class Downloaded(val bytes: Long) : SongRunResult
    data object Skipped : SongRunResult
    data class Failed(val retryable: Boolean) : SongRunResult
}

private data class MutableRunState(
    val totalSongCount: Int,
    var processedSongCount: Int = 0,
    var downloadedSongCount: Int = 0,
    var skippedSongCount: Int = 0,
    var failedSongCount: Int = 0,
    var activeSongIds: Set<String> = emptySet(),
    var downloadedBytes: Long = 0L,
    var retryableFailureCount: Int = 0,
) {
    fun snapshot() = PlaylistDownloadProgress(
        totalSongCount = totalSongCount,
        processedSongCount = processedSongCount,
        downloadedSongCount = downloadedSongCount,
        skippedSongCount = skippedSongCount,
        failedSongCount = failedSongCount,
        activeSongIds = activeSongIds,
        downloadedBytes = downloadedBytes,
    )
}

private const val MAX_CONCURRENT_TRANSFERS = 2

private fun <T> List<T>.chunkedForWorkers(workerCount: Int): List<List<T>> {
    if (isEmpty()) return emptyList()
    val lanes = List(workerCount.coerceAtMost(size)) { mutableListOf<T>() }
    forEachIndexed { index, item -> lanes[index % lanes.size] += item }
    return lanes
}
