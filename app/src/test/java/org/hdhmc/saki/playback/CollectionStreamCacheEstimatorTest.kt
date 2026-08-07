package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.CollectionStreamCacheEstimate
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionStreamCacheEstimatorTest {
    @Test
    fun originalQualityPrefersServerFileSize() {
        val song = song(
            durationSeconds = 240,
            bitRate = 320,
            sizeBytes = 8_000_000L,
        )

        assertEquals(8_000_000L, estimateSongStreamBytes(song, StreamQuality.ORIGINAL))
    }

    @Test
    fun cappedQualityUsesTargetBitrateForConfirmedTranscode() {
        val song = song(
            durationSeconds = 200,
            bitRate = 1_000,
            sizeBytes = 25_000_000L,
        )

        assertEquals(3_296_000L, estimateSongStreamBytes(song, StreamQuality.KBPS_128))
    }

    @Test
    fun cappedQualityKeepsOriginalSizeWhenSourceIsBelowLimit() {
        val song = song(
            durationSeconds = 200,
            bitRate = 96,
            sizeBytes = 2_400_000L,
        )

        assertEquals(2_400_000L, estimateSongStreamBytes(song, StreamQuality.KBPS_128))
    }

    @Test
    fun missingDurationAndSizeCannotBeEstimated() {
        assertNull(
            estimateSongStreamBytes(
                song(durationSeconds = null, bitRate = null, sizeBytes = null),
                StreamQuality.KBPS_128,
            ),
        )
    }

    @Test
    fun estimateReportsEvictionAndHardLimitSeparately() {
        val fitsCollection = CollectionStreamCacheEstimate(
            quality = StreamQuality.KBPS_320,
            songCount = 10,
            alreadyCachedSongCount = 0,
            unknownSizeSongCount = 0,
            estimatedCollectionBytes = 600L,
            estimatedAdditionalBytes = 600L,
            currentCacheBytes = 700L,
            cacheLimitBytes = 1_000L,
        )
        assertEquals(300L, fitsCollection.estimatedEvictionBytes)
        assertFalse(fitsCollection.exceedsCacheLimit)

        val exceedsCollection = fitsCollection.copy(estimatedCollectionBytes = 1_200L)
        assertTrue(exceedsCollection.exceedsCacheLimit)
    }

    private fun song(
        durationSeconds: Int?,
        bitRate: Int?,
        sizeBytes: Long?,
    ): Song = Song(
        id = "song",
        parentId = null,
        title = "Song",
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
        suffix = null,
        contentType = null,
        sizeBytes = sizeBytes,
        path = null,
        created = null,
    )
}
