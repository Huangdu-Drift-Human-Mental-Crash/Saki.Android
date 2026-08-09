package org.hdhmc.saki.data.download

import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDownloadPlannerTest {
    @Test
    fun `estimate deduplicates songs and skips qualifying downloads`() {
        val first = song(id = "a", sizeBytes = 1_000L)
        val duplicate = first.copy(title = "duplicate")
        val second = song(id = "b", sizeBytes = 2_000L)

        val estimate = calculatePlaylistDownloadEstimate(
            serverId = 7L,
            songs = listOf(first, duplicate, second),
            quality = StreamQuality.ORIGINAL,
            qualifyingDownloadedSongIds = setOf("a", "not-in-playlist"),
            availableBytes = 5_000L,
        )

        assertEquals(2, estimate.totalSongCount)
        assertEquals(7L, estimate.serverId)
        assertEquals(1, estimate.alreadyDownloadedSongCount)
        assertEquals(1, estimate.pendingSongCount)
        assertEquals(2_000L, estimate.estimatedAdditionalBytes)
        assertTrue(estimate.hasEnoughSpace)
    }

    @Test
    fun `estimate reports unknown sizes and insufficient space`() {
        val estimate = calculatePlaylistDownloadEstimate(
            serverId = 7L,
            songs = listOf(
                song(id = "known", sizeBytes = 4_000L),
                song(id = "unknown", sizeBytes = null, durationSeconds = null, bitRate = null),
            ),
            quality = StreamQuality.ORIGINAL,
            qualifyingDownloadedSongIds = emptySet(),
            availableBytes = 3_000L,
        )

        assertEquals(1, estimate.unknownSizeSongCount)
        assertEquals(4_000L, estimate.estimatedAdditionalBytes)
        assertFalse(estimate.hasEnoughSpace)
    }
}

internal fun song(
    id: String,
    sizeBytes: Long? = 1_000L,
    durationSeconds: Int? = 10,
    bitRate: Int? = 128,
): Song = Song(
    id = id,
    parentId = null,
    title = "Song $id",
    album = null,
    albumId = null,
    artist = null,
    artistId = null,
    coverArtId = null,
    durationSeconds = durationSeconds,
    track = null,
    discNumber = null,
    year = null,
    genre = null,
    bitRate = bitRate,
    sampleRate = null,
    suffix = "mp3",
    contentType = "audio/mpeg",
    sizeBytes = sizeBytes,
    path = null,
    created = null,
)
