package org.hdhmc.saki.playback

import androidx.media3.common.Player
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
}
