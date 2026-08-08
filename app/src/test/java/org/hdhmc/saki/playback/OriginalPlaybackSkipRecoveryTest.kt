package org.hdhmc.saki.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalPlaybackSkipRecoveryTest {
    @Test
    fun `skip recovery is bounded to one queue pass`() {
        assertTrue(canContinueOriginalPlaybackSkip(failureCount = 1, queueSize = 3))
        assertTrue(canContinueOriginalPlaybackSkip(failureCount = 2, queueSize = 3))
        assertFalse(canContinueOriginalPlaybackSkip(failureCount = 3, queueSize = 3))
        assertFalse(canContinueOriginalPlaybackSkip(failureCount = 1, queueSize = 1))
    }

    @Test
    fun `all failed repeat queue stops before wrapping a second cycle`() {
        val recovery = OriginalPlaybackSkipRecovery()
        val queueSize = 3
        val second = PlaybackRecoveryItemKey(serverId = 7L, songId = "second")
        val third = PlaybackRecoveryItemKey(serverId = 7L, songId = "third")

        val firstFailure = recovery.recordFailure()
        assertTrue(canContinueOriginalPlaybackSkip(firstFailure, queueSize))
        recovery.expectAutomaticTransition(second)
        recovery.onMediaItemTransition(second)

        val secondFailure = recovery.recordFailure()
        assertTrue(canContinueOriginalPlaybackSkip(secondFailure, queueSize))
        recovery.expectAutomaticTransition(third)
        recovery.onMediaItemTransition(third)

        val thirdFailure = recovery.recordFailure()
        assertFalse(canContinueOriginalPlaybackSkip(thirdFailure, queueSize))
    }

    @Test
    fun `automatic transitions preserve the current recovery cycle`() {
        val recovery = OriginalPlaybackSkipRecovery()
        val second = PlaybackRecoveryItemKey(serverId = 7L, songId = "second")
        val third = PlaybackRecoveryItemKey(serverId = 7L, songId = "third")

        assertEquals(1, recovery.recordFailure())
        recovery.expectAutomaticTransition(second)
        recovery.onMediaItemTransition(second)
        assertEquals(2, recovery.recordFailure())
        recovery.expectAutomaticTransition(third)
        recovery.onMediaItemTransition(third)
        assertEquals(3, recovery.recordFailure())
    }

    @Test
    fun `user or natural transitions start a new recovery cycle`() {
        val recovery = OriginalPlaybackSkipRecovery()
        val automaticTarget = PlaybackRecoveryItemKey(serverId = 7L, songId = "automatic")
        val userTarget = PlaybackRecoveryItemKey(serverId = 7L, songId = "user")

        assertEquals(1, recovery.recordFailure())
        recovery.expectAutomaticTransition(automaticTarget)
        recovery.onMediaItemTransition(userTarget)
        assertEquals(1, recovery.recordFailure())

        recovery.onMediaItemTransition(null)
        assertEquals(1, recovery.recordFailure())
    }
}
