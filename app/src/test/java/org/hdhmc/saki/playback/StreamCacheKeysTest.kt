package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCacheKeysTest {
    @Test
    fun buildKeepsV1KeyWhenSongIdDoesNotContainDelimiter() {
        val key = buildStreamCacheKey(
            serverId = 7L,
            songId = "legacy-song",
            quality = StreamQuality.ORIGINAL,
        )

        assertEquals("saki.stream.v1|7|legacy-song|original", key)
        assertEquals(
            StreamCacheResourceKey(
                serverId = 7L,
                songId = "legacy-song",
                qualityKey = StreamQuality.ORIGINAL.storageKey,
            ),
            parseStreamCacheKey(key),
        )
    }

    @Test
    fun buildAndParseRoundTripWhenSongIdContainsDelimiter() {
        val songId = "folder|disc|track"

        val key = buildStreamCacheKey(
            serverId = 42L,
            songId = songId,
            quality = StreamQuality.KBPS_320,
        )

        val parts = key.split('|')
        assertEquals("saki.stream.v2", parts[0])
        assertEquals(4, parts.size)
        assertFalse(parts[2].contains('|'))
        assertEquals(
            StreamCacheResourceKey(
                serverId = 42L,
                songId = songId,
                qualityKey = StreamQuality.KBPS_320.storageKey,
            ),
            parseStreamCacheKey(key),
        )
    }

    @Test
    fun buildAndParseRoundTripWhenEncodedSongIdContainsPercent() {
        val songId = "folder%disc|track"

        val key = buildStreamCacheKey(
            serverId = 42L,
            songId = songId,
            quality = StreamQuality.KBPS_320,
        )

        assertEquals(
            StreamCacheResourceKey(
                serverId = 42L,
                songId = songId,
                qualityKey = StreamQuality.KBPS_320.storageKey,
            ),
            parseStreamCacheKey(key),
        )
    }

    @Test
    fun parseKeepsV1KeysCompatible() {
        val key = "saki.stream.v1|7|legacy-song|original"

        assertEquals(
            StreamCacheResourceKey(
                serverId = 7L,
                songId = "legacy-song",
                qualityKey = StreamQuality.ORIGINAL.storageKey,
            ),
            parseStreamCacheKey(key),
        )
    }

    @Test
    fun parseRejectsV1KeysWithDelimiterInSongId() {
        val key = "saki.stream.v1|7|folder|track|original"

        assertNull(parseStreamCacheKey(key))
    }

    @Test
    fun parseRejectsInvalidV2EncodedSongId() {
        val key = "saki.stream.v2|7|%%%|original"

        assertNull(parseStreamCacheKey(key))
    }
}
