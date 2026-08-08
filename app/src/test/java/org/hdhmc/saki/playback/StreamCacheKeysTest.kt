package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun forcedTranscodeUsesDistinctParseableCacheKey() {
        val normalKey = buildStreamCacheKey(
            serverId = 42L,
            songId = "folder|track",
            quality = StreamQuality.KBPS_320,
        )
        val forcedKey = buildForcedTranscodeStreamCacheKey(
            serverId = 42L,
            songId = "folder|track",
            quality = StreamQuality.KBPS_320,
            format = "MP3",
        )

        assertFalse(normalKey == forcedKey)
        assertEquals(
            StreamCacheResourceKey(
                serverId = 42L,
                songId = "folder|track",
                qualityKey = StreamQuality.KBPS_320.storageKey,
                variantKey = "forced-mp3",
            ),
            parseStreamCacheKey(forcedKey),
        )
    }

    @Test
    fun forcedTranscodePrefetchUsesTheForcedVariantCacheIdentity() {
        val variant = buildStreamPrefetchVariant(
            serverId = 42L,
            songId = "unsupported-wma",
            requestedQuality = StreamQuality.ORIGINAL,
            forcedQuality = StreamQuality.KBPS_320,
            forcedFormat = "MP3",
        )

        assertEquals(StreamQuality.KBPS_320, variant.quality)
        assertEquals("MP3", variant.format)
        assertEquals("320:forced-mp3", variant.targetQualityKey)
        assertEquals(
            buildForcedTranscodeStreamCacheKey(
                serverId = 42L,
                songId = "unsupported-wma",
                quality = StreamQuality.KBPS_320,
                format = "MP3",
            ),
            variant.cacheKey,
        )
    }

    @Test
    fun normalPrefetchKeepsTheNormalQualityCacheIdentity() {
        val variant = buildStreamPrefetchVariant(
            serverId = 42L,
            songId = "normal-flac",
            requestedQuality = StreamQuality.ORIGINAL,
        )

        assertEquals(StreamQuality.ORIGINAL, variant.quality)
        assertNull(variant.format)
        assertEquals(StreamQuality.ORIGINAL.storageKey, variant.targetQualityKey)
        assertEquals(
            buildStreamCacheKey(42L, "normal-flac", StreamQuality.ORIGINAL),
            variant.cacheKey,
        )
    }

    @Test
    fun incompleteForcedPrefetchMetadataFallsBackToTheRequestedVariant() {
        val variant = buildStreamPrefetchVariant(
            serverId = 42L,
            songId = "normal-flac",
            requestedQuality = StreamQuality.ORIGINAL,
            forcedQuality = StreamQuality.KBPS_320,
            forcedFormat = null,
        )

        assertEquals(StreamQuality.ORIGINAL, variant.quality)
        assertFalse(variant.isForcedTranscode)
        assertEquals(
            buildStreamCacheKey(42L, "normal-flac", StreamQuality.ORIGINAL),
            variant.cacheKey,
        )
    }

    @Test
    fun forcedTranscodeOffsetUsesAParseableVariantKey() {
        val baseKey = buildForcedTranscodeStreamCacheKey(
            serverId = 42L,
            songId = "folder|track",
            quality = StreamQuality.KBPS_320,
            format = "mp3",
        )
        val offsetKey = buildStreamOffsetCacheKey(baseKey, timeOffsetSeconds = 83)

        assertEquals(5, offsetKey.split('|').size)
        assertEquals(
            StreamCacheResourceKey(
                serverId = 42L,
                songId = "folder|track",
                qualityKey = StreamQuality.KBPS_320.storageKey,
                variantKey = "forced-mp3",
                streamOffsetSeconds = 83,
            ),
            parseStreamCacheKey(offsetKey),
        )
        assertFalse(requireNotNull(parseStreamCacheKey(offsetKey)).isOfflinePlayableForcedTranscode())
    }

    @Test
    fun normalStreamOffsetAlsoUsesAParseableVariantKey() {
        val baseKey = buildStreamCacheKey(
            serverId = 7L,
            songId = "normal-song",
            quality = StreamQuality.KBPS_128,
        )

        assertEquals(
            StreamCacheResourceKey(
                serverId = 7L,
                songId = "normal-song",
                qualityKey = StreamQuality.KBPS_128.storageKey,
                variantKey = "stream",
                streamOffsetSeconds = 12,
            ),
            parseStreamCacheKey(buildStreamOffsetCacheKey(baseKey, 12)),
        )
    }

    @Test
    fun legacyAppendedOffsetKeysRemainDiscoverableForCleanup() {
        val legacyKey = "saki.stream.v3|42|legacy-song|320|forced-mp3|seek=83"

        assertEquals(
            StreamCacheResourceKey(
                serverId = 42L,
                songId = "legacy-song",
                qualityKey = StreamQuality.KBPS_320.storageKey,
                variantKey = "forced-mp3",
                streamOffsetSeconds = 83,
            ),
            parseStreamCacheKey(legacyKey),
        )
    }

    @Test
    fun completeForcedBaseVariantIsOfflinePlayable() {
        val parsed = requireNotNull(
            parseStreamCacheKey(
                buildForcedTranscodeStreamCacheKey(
                    serverId = 42L,
                    songId = "unsupported-wma",
                    quality = StreamQuality.KBPS_320,
                    format = "mp3",
                ),
            ),
        )

        assertTrue(parsed.isOfflinePlayableForcedTranscode())
        assertEquals("mp3", parsed.forcedTranscodeFormat())
    }
}
