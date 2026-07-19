package org.hdhmc.saki.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import org.hdhmc.saki.data.remote.NetworkType
import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSeekRepeatTest {
    @Test
    fun `repeating the current offset stream pauses at its end`() {
        assertTrue(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_ONE, mediaItemCount = 3))
        assertTrue(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_ALL, mediaItemCount = 1))
    }

    @Test
    fun `repeat all with a following item does not pause the offset stream`() {
        assertFalse(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_ALL, mediaItemCount = 3))
        assertFalse(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_OFF, mediaItemCount = 1))
    }

    @Test
    fun `server-side seek requires confirmed transcoding`() {
        assertTrue(isConfirmedTranscode(sourceBitRate = 320, requestedBitRate = 128))
        assertFalse(isConfirmedTranscode(sourceBitRate = 128, requestedBitRate = 320))
        assertFalse(isConfirmedTranscode(sourceBitRate = null, requestedBitRate = 128))
        assertFalse(isConfirmedTranscode(sourceBitRate = 320, requestedBitRate = null))
    }

    @Test
    fun `only whole-resource EOF marks a stream complete`() {
        assertTrue(isResourceEof(requestLength = C.LENGTH_UNSET.toLong(), bytesRead = 8_192L))
        assertTrue(isResourceEof(requestLength = 8_192L, bytesRead = 4_096L))
        assertFalse(isResourceEof(requestLength = 8_192L, bytesRead = 8_192L))
    }

    @Test
    fun `adaptive quality overrides the queue time stream quality`() {
        val prefs = PlaybackPreferences(
            adaptiveQualityEnabled = true,
            wifiStreamQuality = StreamQuality.KBPS_320,
            mobileStreamQuality = StreamQuality.KBPS_128,
        )

        val effectiveQuality = selectEffectiveStreamQuality(
            prefs = prefs,
            networkType = NetworkType.MOBILE,
            fallbackMaxBitRate = StreamQuality.KBPS_320.maxBitRate,
        )

        assertEquals(StreamQuality.KBPS_128, effectiveQuality)
    }
}
