package org.hdhmc.saki.data.download

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.ArtistRef
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.Song

@Singleton
class PlaylistDownloadPlanStore internal constructor(
    private val directory: File,
    moshi: Moshi,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val adapter = moshi.adapter(PlaylistDownloadPlanDto::class.java)
    private val mutex = Mutex()

    @Inject
    constructor(
        @ApplicationContext context: Context,
        moshi: Moshi,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        directory = File(context.filesDir, PLAN_DIRECTORY),
        moshi = moshi,
        ioDispatcher = ioDispatcher,
    )

    internal fun createPlanId(): String = UUID.randomUUID().toString()

    internal suspend fun write(
        planId: String,
        serverId: Long,
        playlist: Playlist,
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val plan = PlaylistDownloadPlan(
                serverId = serverId,
                playlistId = playlist.id,
                title = playlist.name,
                songs = playlist.songs.distinctBy(Song::id),
            )
            directory.mkdirs()
            check(directory.isDirectory) { "Unable to create the playlist download plan directory." }
            val target = planFile(planId) ?: error("Invalid generated playlist download plan ID.")
            val temp = File.createTempFile("playlist_$planId.", ".tmp", directory)
            try {
                temp.writeText(adapter.toJson(plan.toDto()))
                check(temp.renameTo(target)) { "Unable to persist the playlist download plan." }
            } finally {
                temp.delete()
            }
        }
    }

    internal suspend fun read(planId: String): PlaylistDownloadPlan? = withContext(ioDispatcher) {
        mutex.withLock {
            val file = planFile(planId)?.takeIf(File::isFile) ?: return@withLock null
            runCatching {
                adapter.fromJson(file.readText())
                    ?.takeIf { dto -> dto.version == CURRENT_PLAN_VERSION }
                    ?.toDomain()
            }.getOrNull()
        }
    }

    internal suspend fun delete(planId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            planFile(planId)?.delete()
        }
    }

    internal suspend fun deleteAll(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            directory.listFiles()
                .orEmpty()
                .filter { file ->
                    file.name.startsWith(PLAN_FILE_PREFIX) &&
                        (file.name.endsWith(PLAN_FILE_SUFFIX) || file.name.endsWith(".tmp"))
                }
                .forEach(File::delete)
        }
    }

    internal suspend fun deleteUnreferenced(
        referencedPlanIds: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            directory.listFiles()
                .orEmpty()
                .filter { file ->
                    file.name.startsWith(PLAN_FILE_PREFIX) &&
                        file.name.endsWith(PLAN_FILE_SUFFIX) &&
                        nowMillis - file.lastModified() >= ORPHAN_GRACE_PERIOD_MILLIS
                }
                .filter { file ->
                    file.name.removePrefix(PLAN_FILE_PREFIX).removeSuffix(PLAN_FILE_SUFFIX) !in
                        referencedPlanIds
                }
                .forEach(File::delete)
        }
    }

    private fun planFile(planId: String): File? {
        val canonicalId = runCatching { UUID.fromString(planId).toString() }.getOrNull()
            ?: return null
        if (canonicalId != planId.lowercase()) return null
        return File(directory, "$PLAN_FILE_PREFIX$canonicalId$PLAN_FILE_SUFFIX")
    }

    private companion object {
        const val PLAN_DIRECTORY = "offline/download-plans"
        const val PLAN_FILE_PREFIX = "playlist_"
        const val PLAN_FILE_SUFFIX = ".json"
        const val CURRENT_PLAN_VERSION = 1
        const val ORPHAN_GRACE_PERIOD_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

internal data class PlaylistDownloadPlan(
    val serverId: Long,
    val playlistId: String,
    val title: String,
    val songs: List<Song>,
)

@JsonClass(generateAdapter = true)
internal data class PlaylistDownloadPlanDto(
    val version: Int,
    val serverId: Long,
    val playlistId: String,
    val title: String,
    val songs: List<PlaylistDownloadSongDto>,
)

@JsonClass(generateAdapter = true)
internal data class PlaylistDownloadSongDto(
    val id: String,
    val parentId: String?,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val artists: List<PlaylistDownloadArtistRefDto>,
    val coverArtId: String?,
    val durationSeconds: Int?,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val bitRate: Int?,
    val sampleRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val sizeBytes: Long?,
    val path: String?,
    val created: String?,
)

@JsonClass(generateAdapter = true)
internal data class PlaylistDownloadArtistRefDto(
    val id: String,
    val name: String,
)

private fun PlaylistDownloadPlan.toDto() = PlaylistDownloadPlanDto(
    version = 1,
    serverId = serverId,
    playlistId = playlistId,
    title = title,
    songs = songs.map(Song::toPlaylistDownloadDto),
)

private fun PlaylistDownloadPlanDto.toDomain() = PlaylistDownloadPlan(
    serverId = serverId,
    playlistId = playlistId,
    title = title,
    songs = songs.map(PlaylistDownloadSongDto::toDomain),
)

private fun Song.toPlaylistDownloadDto() = PlaylistDownloadSongDto(
    id = id,
    parentId = parentId,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    artists = artists.map { artist ->
        PlaylistDownloadArtistRefDto(id = artist.id, name = artist.name)
    },
    coverArtId = coverArtId,
    durationSeconds = durationSeconds,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    bitRate = bitRate,
    sampleRate = sampleRate,
    suffix = suffix,
    contentType = contentType,
    sizeBytes = sizeBytes,
    path = path,
    created = created,
)

private fun PlaylistDownloadSongDto.toDomain() = Song(
    id = id,
    parentId = parentId,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    artists = artists.map { artist -> ArtistRef(id = artist.id, name = artist.name) },
    coverArtId = coverArtId,
    durationSeconds = durationSeconds,
    track = track,
    discNumber = discNumber,
    year = year,
    genre = genre,
    bitRate = bitRate,
    sampleRate = sampleRate,
    suffix = suffix,
    contentType = contentType,
    sizeBytes = sizeBytes,
    path = path,
    created = created,
)
