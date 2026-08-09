package org.hdhmc.saki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheSizeTest {
    @Test
    fun `default and supported range remain bounded`() {
        assertEquals(2_048, DEFAULT_STREAM_CACHE_SIZE_MB)
        assertEquals(256, STREAM_CACHE_SIZE_OPTIONS_MB.first())
        assertEquals(32_768, STREAM_CACHE_SIZE_OPTIONS_MB.last())
        assertEquals(MIN_STREAM_CACHE_SIZE_MB, STREAM_CACHE_SIZE_OPTIONS_MB.first())
        assertEquals(MAX_STREAM_CACHE_SIZE_MB, STREAM_CACHE_SIZE_OPTIONS_MB.last())
    }

    @Test
    fun `all legacy cache sizes remain unchanged`() {
        (256..8_192 step 256).forEach { legacySizeMb ->
            assertEquals(legacySizeMb, normalizeStreamCacheSizeMb(legacySizeMb))
        }
    }

    @Test
    fun `high range uses one GiB increments`() {
        val highRange = STREAM_CACHE_SIZE_OPTIONS_MB.dropWhile { it <= 8_192 }

        assertEquals((9_216..32_768 step 1_024).toList(), highRange)
        highRange.forEach { sizeMb ->
            assertEquals(sizeMb, normalizeStreamCacheSizeMb(sizeMb))
        }
    }

    @Test
    fun `values outside range clamp to nearest endpoint`() {
        assertEquals(MIN_STREAM_CACHE_SIZE_MB, normalizeStreamCacheSizeMb(Int.MIN_VALUE))
        assertEquals(MIN_STREAM_CACHE_SIZE_MB, normalizeStreamCacheSizeMb(0))
        assertEquals(MAX_STREAM_CACHE_SIZE_MB, normalizeStreamCacheSizeMb(40_000))
        assertEquals(MAX_STREAM_CACHE_SIZE_MB, normalizeStreamCacheSizeMb(Int.MAX_VALUE))
    }

    @Test
    fun `values normalize to nearest option across tier boundary`() {
        assertEquals(7_936, normalizeStreamCacheSizeMb(8_000))
        assertEquals(8_192, normalizeStreamCacheSizeMb(8_191))
        assertEquals(8_192, normalizeStreamCacheSizeMb(8_703))
        assertEquals(9_216, normalizeStreamCacheSizeMb(8_704))
        assertEquals(9_216, normalizeStreamCacheSizeMb(9_700))
        assertEquals(10_240, normalizeStreamCacheSizeMb(9_728))
    }

    @Test
    fun `option indices round trip supported sizes and clamp`() {
        STREAM_CACHE_SIZE_OPTIONS_MB.forEachIndexed { index, sizeMb ->
            assertEquals(index, streamCacheSizeOptionIndex(sizeMb))
            assertEquals(sizeMb, streamCacheSizeMbAtOptionIndex(index))
        }

        assertEquals(MIN_STREAM_CACHE_SIZE_MB, streamCacheSizeMbAtOptionIndex(-1))
        assertEquals(MAX_STREAM_CACHE_SIZE_MB, streamCacheSizeMbAtOptionIndex(Int.MAX_VALUE))
    }
}
