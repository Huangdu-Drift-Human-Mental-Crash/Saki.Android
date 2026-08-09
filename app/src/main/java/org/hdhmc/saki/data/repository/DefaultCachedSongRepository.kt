package org.hdhmc.saki.data.repository

import android.content.Context
import org.hdhmc.saki.data.download.CancellableBinaryDownloader
import org.hdhmc.saki.data.download.DownloadedBinary
import org.hdhmc.saki.data.local.dao.CachedSongDao
import org.hdhmc.saki.data.local.dao.LibraryCacheDao
import org.hdhmc.saki.data.local.entity.CachedSongEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataEntity
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.CacheStorageSummary
import org.hdhmc.saki.domain.model.CachedSong
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository
import org.hdhmc.saki.domain.repository.SubsonicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DefaultCachedSongRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val cachedSongDao: CachedSongDao,
    private val libraryCacheDao: LibraryCacheDao,
    private val binaryDownloader: CancellableBinaryDownloader,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subsonicRepository: SubsonicRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CachedSongRepository {
    private val songDownloadLocks = List(DOWNLOAD_LOCK_COUNT) { Mutex() }
    private val coverDownloadLocks = List(DOWNLOAD_LOCK_COUNT) { Mutex() }

    override fun observeCachedSongs(): Flow<List<CachedSong>> {
        return combine(
            cachedSongDao.observeCachedSongs(),
            libraryCacheDao.observeSongMetadataInvalidations(),
        ) { songs, _ -> songs }
            .map { songs -> songs.toDomainWithMetadata(libraryCacheDao) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getCachedSong(
        serverId: Long,
        songId: String,
    ): CachedSong? = withContext(ioDispatcher) {
        cachedSongDao.getCachedSong(serverId, songId)
            ?.let { entity -> entity.toDomainWithMetadata(libraryCacheDao) }
    }

    override suspend fun getPlayableCachedSong(
        serverId: Long,
        songId: String,
        preferredQuality: StreamQuality,
    ): CachedSong? = withContext(ioDispatcher) {
        cachedSongDao.getCachedSong(serverId, songId)
            ?.let { entity -> entity.toDomainWithMetadata(libraryCacheDao) }
            ?.takeIf { song -> song.canPlayAt(preferredQuality) }
    }

    override suspend fun getPlayableCachedSongs(
        serverId: Long,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong> = withContext(ioDispatcher) {
        cachedSongDao.getCachedSongsForServer(serverId)
            .toDomainWithMetadata(libraryCacheDao)
            .asSequence()
            .filter { song -> song.canPlayAt(preferredQuality) }
            .associateBy(CachedSong::songId)
    }

    override suspend fun getPlayableCachedSongs(
        serverId: Long,
        songIds: List<String>,
        preferredQuality: StreamQuality,
    ): Map<String, CachedSong> = withContext(ioDispatcher) {
        if (songIds.isEmpty()) return@withContext emptyMap()
        songIds.distinct()
            .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> cachedSongDao.getCachedSongs(serverId, chunk) }
            .toDomainWithMetadata(libraryCacheDao)
            .asSequence()
            .filter { song -> song.canPlayAt(preferredQuality) }
            .associateBy(CachedSong::songId)
    }

    override suspend fun cacheSong(
        serverId: Long,
        song: Song,
    ): CachedSong {
        val quality = playbackPreferencesRepository.getPreferences().downloadQuality
        return cacheSong(serverId, song, quality)
    }

    override suspend fun cacheSong(
        serverId: Long,
        song: Song,
        quality: StreamQuality,
    ): CachedSong = withContext(ioDispatcher) {
        songDownloadLocks.lockFor("$serverId:${song.id}").withLock {
            cacheSongLocked(serverId, song, quality)
        }
    }

    private suspend fun cacheSongLocked(
        serverId: Long,
        song: Song,
        quality: StreamQuality,
    ): CachedSong {
        val existing = cachedSongDao.getCachedSong(serverId, song.id)
        if (
            existing != null &&
            File(existing.localPath).isNonEmptyFile() &&
            StreamQuality.fromStorageKey(existing.qualityKey).isAtLeast(quality)
        ) {
            val existingQuality = StreamQuality.fromStorageKey(existing.qualityKey)
            val enriched = existing.withPlaybackMetadataFrom(song, existingQuality)
            if (enriched != existing) {
                cachedSongDao.upsertCachedSong(enriched)
            }
            return enriched.toDomain()
        }

        val audioDirectory = File(appContext.filesDir, "offline/audio/$serverId").apply {
            mkdirs()
        }
        val coverDirectory = File(appContext.filesDir, "offline/cover/$serverId").apply {
            mkdirs()
        }

        val audioDownload = downloadAudio(
            serverId = serverId,
            song = song,
            quality = quality,
            destinationDirectory = audioDirectory,
        )
        var entityCommitted = false
        suspend fun persistEntity(coverArtPath: String?): CachedSongEntity {
            val entity = CachedSongEntity(
                cacheId = existing?.cacheId ?: "$serverId:${song.id}",
                serverId = serverId,
                songId = song.id,
                title = song.title,
                album = song.album,
                albumId = song.albumId,
                artist = song.artist,
                artistId = song.artistId,
                coverArtId = song.coverArtId,
                coverArtPath = coverArtPath,
                localPath = audioDownload.file.absolutePath,
                durationSeconds = song.durationSeconds,
                track = song.track,
                discNumber = song.discNumber,
                suffix = audioDownload.suffix ?: song.suffix,
                contentType = audioDownload.contentType ?: song.contentType,
                bitRate = song.cachedBitRateKbps(quality),
                sampleRate = song.sampleRate,
                qualityKey = quality.storageKey,
                fileSizeBytes = audioDownload.file.length(),
                downloadedAt = System.currentTimeMillis(),
            )

            withContext(NonCancellable) {
                try {
                    cachedSongDao.upsertCachedSong(entity)
                } catch (exception: Exception) {
                    if (existing?.localPath != entity.localPath) {
                        entity.localPath.let(::File).delete()
                    }
                    throw exception
                }

                existing?.let { stale ->
                    if (stale.localPath != entity.localPath) {
                        File(stale.localPath).delete()
                    }
                }
                entityCommitted = true
            }
            return entity
        }

        val entity = try {
            val coverArtId = song.coverArtId
            if (coverArtId == null) {
                persistEntity(coverArtPath = null)
            } else {
                coverDownloadLocks.lockFor("$serverId:$coverArtId").withLock {
                    val coverArtPath = try {
                        getOrDownloadCoverArt(
                            serverId = serverId,
                            coverArtId = coverArtId,
                            destinationDirectory = coverDirectory,
                        ).absolutePath
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        null
                    }
                    persistEntity(coverArtPath)
                }
            }
        } catch (exception: CancellationException) {
            if (!entityCommitted) audioDownload.file.delete()
            throw exception
        }

        existing?.coverArtPath
            ?.takeIf { stalePath -> stalePath != entity.coverArtPath }
            ?.let { stalePath ->
                deleteCoverIfUnreferenced(
                    serverId = serverId,
                    coverArtId = existing.coverArtId,
                    coverArtPath = stalePath,
                )
            }

        return entity.toDomain()
    }

    private suspend fun deleteCoverIfUnreferenced(
        serverId: Long,
        coverArtId: String?,
        coverArtPath: String,
    ) {
        val lockKey = "$serverId:${coverArtId ?: coverArtPath}"
        coverDownloadLocks.lockFor(lockKey).withLock {
            withContext(NonCancellable) {
                if (cachedSongDao.countCachedSongsReferencingCover(coverArtPath) == 0) {
                    File(coverArtPath).delete()
                }
            }
        }
    }

    override suspend fun deleteCachedSong(cacheId: String): Unit = withContext(ioDispatcher) {
        val initial = cachedSongDao.getCachedSongById(cacheId)
        if (initial == null) {
            cachedSongDao.deleteCachedSong(cacheId)
            return@withContext
        }
        songDownloadLocks.lockFor("${initial.serverId}:${initial.songId}").withLock {
            val cachedSong = cachedSongDao.getCachedSongById(cacheId)
            if (cachedSong == null) {
                cachedSongDao.deleteCachedSong(cacheId)
                return@withLock
            }
            val deleteRecordAndFiles: suspend () -> Unit = {
                withContext(NonCancellable) {
                    File(cachedSong.localPath).delete()
                    cachedSongDao.deleteCachedSong(cacheId)
                    cachedSong.coverArtPath?.let { coverPath ->
                        if (cachedSongDao.countCachedSongsReferencingCover(coverPath) == 0) {
                            File(coverPath).delete()
                        }
                    }
                }
            }
            val coverArtPath = cachedSong.coverArtPath
            if (coverArtPath == null) {
                deleteRecordAndFiles()
            } else {
                val coverLockKey = "${cachedSong.serverId}:${cachedSong.coverArtId ?: coverArtPath}"
                coverDownloadLocks.lockFor(coverLockKey).withLock {
                    deleteRecordAndFiles()
                }
            }
        }
    }

    override suspend fun clearCachedSongs(serverId: Long?): Int = withContext(ioDispatcher) {
        songDownloadLocks.withAllLocks {
            withContext(NonCancellable) {
                val cachedSongs = cachedSongDao.getScopedCachedSongs(serverId)
                if (cachedSongs.isEmpty()) {
                    0
                } else {
                    cachedSongs
                        .map(CachedSongEntity::localPath)
                        .distinct()
                        .forEach { path -> File(path).delete() }
                    cachedSongs
                        .mapNotNull(CachedSongEntity::coverArtPath)
                        .distinct()
                        .forEach { path -> File(path).delete() }

                    if (serverId != null) {
                        cachedSongDao.deleteCachedSongsForServer(serverId)
                    } else {
                        cachedSongDao.deleteAllCachedSongs()
                    }

                    cachedSongs.size
                }
            }
        }
    }

    override suspend fun getCacheStorageSummary(serverId: Long?): CacheStorageSummary = withContext(ioDispatcher) {
        val cachedSongs = cachedSongDao.getScopedCachedSongs(serverId)
        val downloadedBytes = cachedSongs.sumOf { cachedSong ->
            File(cachedSong.localPath).length().takeIf { it > 0 } ?: cachedSong.fileSizeBytes
        } + cachedSongs
            .mapNotNull(CachedSongEntity::coverArtPath)
            .distinct()
            .sumOf { path -> File(path).length().takeIf { it > 0 } ?: 0L }

        CacheStorageSummary(
            downloadedSongCount = cachedSongs.size,
            downloadedBytes = downloadedBytes,
            streamCacheBytes = 0,
            hasStreamingCache = false,
        )
    }

    private suspend fun downloadAudio(
        serverId: Long,
        song: Song,
        quality: StreamQuality,
        destinationDirectory: File,
    ): DownloadedBinary {
        val candidates = if (quality.preferOriginalDownload) {
            subsonicRepository.buildDownloadRequest(
                serverId = serverId,
                songId = song.id,
            ).candidates
        } else {
            subsonicRepository.buildStreamRequest(
                serverId = serverId,
                songId = song.id,
                maxBitRate = quality.maxBitRate,
                format = quality.format,
            ).candidates
        }

        val requestedSuffix = when {
            quality.preferOriginalDownload -> song.suffix
            quality.format.isNullOrBlank() || quality.format == "raw" -> song.suffix
            else -> quality.format
        }

        return binaryDownloader.download(
            candidates = candidates,
            destinationDirectory = destinationDirectory,
            destinationBaseName = buildCacheFileStem(song.title, song.id, quality.storageKey),
            preferredSuffix = requestedSuffix,
        )
    }

    private suspend fun downloadCoverArt(
        serverId: Long,
        coverArtId: String,
        destinationDirectory: File,
    ): File {
        val request = subsonicRepository.buildCoverArtRequest(
            serverId = serverId,
            coverArtId = coverArtId,
            size = 720,
        )
        return binaryDownloader.download(
            candidates = request.candidates,
            destinationDirectory = destinationDirectory,
            destinationBaseName = buildCacheFileStem("cover", coverArtId, "art"),
            preferredSuffix = "jpg",
        ).file
    }

    private suspend fun getOrDownloadCoverArt(
        serverId: Long,
        coverArtId: String,
        destinationDirectory: File,
    ): File {
        val existing = File(
            destinationDirectory,
            "${buildCacheFileStem("cover", coverArtId, "art")}.jpg",
        )
        return existing.takeIf(File::isNonEmptyFile) ?: downloadCoverArt(
            serverId = serverId,
            coverArtId = coverArtId,
            destinationDirectory = destinationDirectory,
        )
    }

}

private suspend fun CachedSongDao.getScopedCachedSongs(serverId: Long?): List<CachedSongEntity> {
    return if (serverId != null) {
        getCachedSongsForServer(serverId)
    } else {
        getCachedSongs()
    }
}

private data class CachedSongMetadataKey(
    val serverId: Long,
    val songId: String,
)

private suspend fun CachedSongEntity.toDomainWithMetadata(
    libraryCacheDao: LibraryCacheDao,
): CachedSong {
    val metadata = libraryCacheDao.getSongMetadata(serverId, listOf(songId)).firstOrNull()
    return toDomain(metadata)
}

private suspend fun List<CachedSongEntity>.toDomainWithMetadata(
    libraryCacheDao: LibraryCacheDao,
): List<CachedSong> {
    if (isEmpty()) return emptyList()

    val metadataByKey = groupBy(CachedSongEntity::serverId)
        .flatMap { (serverId, songs) ->
            songs.map(CachedSongEntity::songId)
                .distinct()
                .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
                .flatMap { songIds ->
                    libraryCacheDao.getSongMetadata(serverId, songIds)
                        .map { metadata -> CachedSongMetadataKey(serverId, metadata.songId) to metadata }
                }
        }
        .toMap()

    return map { entity ->
        entity.toDomain(metadataByKey[CachedSongMetadataKey(entity.serverId, entity.songId)])
    }
}

private fun CachedSongEntity.toDomain(metadata: CachedSongMetadataEntity? = null): CachedSong {
    val quality = StreamQuality.fromStorageKey(qualityKey)
    return CachedSong(
        cacheId = cacheId,
        serverId = serverId,
        songId = songId,
        title = metadata?.title ?: title,
        album = metadata?.album ?: album,
        albumId = metadata?.albumId ?: albumId,
        artist = metadata?.artist ?: artist,
        artistId = metadata?.artistId ?: artistId,
        coverArtId = metadata?.coverArtId ?: coverArtId,
        coverArtPath = coverArtPath,
        localPath = localPath,
        durationSeconds = metadata?.durationSeconds ?: durationSeconds,
        track = metadata?.track ?: track,
        discNumber = metadata?.discNumber ?: discNumber,
        suffix = suffix ?: metadata?.suffix.takeIf { quality.preferOriginalDownload },
        contentType = contentType ?: metadata?.contentType.takeIf { quality.preferOriginalDownload },
        bitRateKbps = cachedDisplayBitRateKbps(
            storedBitRate = bitRate,
            sourceBitRate = metadata?.bitRate,
            quality = quality,
        ),
        sampleRate = sampleRate ?: metadata?.sampleRate,
        quality = quality,
        fileSizeBytes = fileSizeBytes,
        downloadedAt = downloadedAt,
    )
}

private fun CachedSong.canPlayAt(preferredQuality: StreamQuality): Boolean {
    return File(localPath).isNonEmptyFile() && quality.isAtLeast(preferredQuality)
}

private fun File.isNonEmptyFile(): Boolean = isFile && length() > 0L

private fun Song.cachedBitRateKbps(quality: StreamQuality): Int? {
    val requestedMaxBitRate = quality.maxBitRate
        ?.takeIf { bitrate -> bitrate > 0 && !quality.preferOriginalDownload }
        ?: return bitRate?.takeIf { bitrate -> bitrate > 0 }
    return bitRate?.takeIf { bitrate -> bitrate > 0 }?.coerceAtMost(requestedMaxBitRate)
        ?: requestedMaxBitRate
}

private fun cachedDisplayBitRateKbps(
    storedBitRate: Int?,
    sourceBitRate: Int?,
    quality: StreamQuality,
): Int? {
    val requestedMaxBitRate = quality.maxBitRate
        ?.takeIf { bitrate -> bitrate > 0 && !quality.preferOriginalDownload }
        ?: return storedBitRate?.takeIf { bitrate -> bitrate > 0 }
            ?: sourceBitRate?.takeIf { bitrate -> bitrate > 0 }
    val knownSourceBitRate = sourceBitRate?.takeIf { bitrate -> bitrate > 0 }
    if (knownSourceBitRate != null && knownSourceBitRate <= requestedMaxBitRate) {
        return knownSourceBitRate
    }
    return storedBitRate?.takeIf { bitrate -> bitrate > 0 } ?: requestedMaxBitRate
}

private fun CachedSongEntity.withPlaybackMetadataFrom(
    song: Song,
    quality: StreamQuality,
): CachedSongEntity {
    return copy(
        bitRate = bitRate ?: song.cachedBitRateKbps(quality),
        sampleRate = sampleRate ?: song.sampleRate,
    )
}

private fun StreamQuality.isAtLeast(preferredQuality: StreamQuality): Boolean {
    return ordinal <= preferredQuality.ordinal
}

private fun buildCacheFileStem(
    title: String,
    uniqueId: String,
    variant: String,
): String {
    val safeTitle = title.toSafeFileSegment(fallback = "track", maxLength = 48)
    val safeUniqueId = uniqueId.toSafeFileSegment(fallback = "id", maxLength = 64) +
        "_${uniqueId.sha256Prefix()}"
    val safeVariant = variant.toSafeFileSegment(fallback = "variant", maxLength = 24)
    return "${safeTitle}_${safeUniqueId}_$safeVariant"
}

private fun String.toSafeFileSegment(fallback: String, maxLength: Int): String {
    return replace(Regex("[^a-zA-Z0-9._-]+"), "_")
        .trim('_', '.')
        .take(maxLength)
        .ifBlank { fallback }
}

private fun String.sha256Prefix(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val IN_CLAUSE_QUERY_CHUNK_SIZE = 500
private const val DOWNLOAD_LOCK_COUNT = 32

private fun List<Mutex>.lockFor(key: String): Mutex {
    return this[Math.floorMod(key.hashCode(), size)]
}

private suspend fun <T> List<Mutex>.withAllLocks(block: suspend () -> T): T {
    var acquiredCount = 0
    try {
        forEach { mutex ->
            mutex.lock()
            acquiredCount += 1
        }
        return block()
    } finally {
        for (index in acquiredCount - 1 downTo 0) {
            this[index].unlock()
        }
    }
}
