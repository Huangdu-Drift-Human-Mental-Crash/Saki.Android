package org.hdhmc.saki.data.repository

import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheSummaryTest {
    @Test
    fun `summary includes forced variants without double counting normal cache`() {
        val songIds = combinedStreamCachedSongIds(
            normalByQuality = mapOf(
                StreamQuality.KBPS_320.storageKey to setOf("normal", "both"),
            ),
            variantsByQuality = mapOf(
                StreamQuality.KBPS_320.storageKey to mapOf(
                    "forced" to "forced-key",
                    "both" to "forced-key-for-same-song",
                ),
            ),
            quality = null,
        )

        assertEquals(setOf("normal", "forced", "both"), songIds)
    }

    @Test
    fun `quality filtered summary includes only matching normal and variant resources`() {
        val songIds = combinedStreamCachedSongIds(
            normalByQuality = mapOf(
                StreamQuality.KBPS_128.storageKey to setOf("normal-128"),
                StreamQuality.KBPS_320.storageKey to setOf("normal-320"),
            ),
            variantsByQuality = mapOf(
                StreamQuality.KBPS_128.storageKey to mapOf("forced-128" to "forced-128-key"),
                StreamQuality.KBPS_320.storageKey to mapOf("forced-320" to "forced-320-key"),
            ),
            quality = StreamQuality.KBPS_320,
        )

        assertEquals(setOf("normal-320", "forced-320"), songIds)
    }
}
