package org.hdhmc.saki.data.download

import org.hdhmc.saki.domain.model.PlaylistDownloadEstimate
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.playback.estimateSongStreamBytes

internal fun calculatePlaylistDownloadEstimate(
    serverId: Long,
    songs: List<Song>,
    quality: StreamQuality,
    qualifyingDownloadedSongIds: Set<String>,
    availableBytes: Long,
): PlaylistDownloadEstimate {
    val uniqueSongs = songs.distinctBy(Song::id)
    val uniqueSongIds = uniqueSongs.mapTo(mutableSetOf(), Song::id)
    var unknownSizeSongCount = 0
    var estimatedAdditionalBytes = 0L

    uniqueSongs.forEach { song ->
        if (song.id in qualifyingDownloadedSongIds) return@forEach
        val estimate = estimateSongStreamBytes(song, quality)
        if (estimate == null) {
            unknownSizeSongCount += 1
        } else {
            estimatedAdditionalBytes += estimate
        }
    }

    return PlaylistDownloadEstimate(
        serverId = serverId,
        quality = quality,
        totalSongCount = uniqueSongs.size,
        alreadyDownloadedSongCount = qualifyingDownloadedSongIds.count(uniqueSongIds::contains),
        unknownSizeSongCount = unknownSizeSongCount,
        estimatedAdditionalBytes = estimatedAdditionalBytes,
        availableBytes = availableBytes.coerceAtLeast(0L),
    )
}
