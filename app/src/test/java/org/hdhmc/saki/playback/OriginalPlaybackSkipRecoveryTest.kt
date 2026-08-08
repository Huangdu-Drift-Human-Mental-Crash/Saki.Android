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

    @Test
    fun `giving up resets the failure budget for a later manual retry`() {
        val recovery = OriginalPlaybackSkipRecovery()

        assertEquals(1, recovery.recordFailure())
        recovery.reset()

        // A newly enlarged queue gets a complete recovery pass on the next user retry.
        assertEquals(1, recovery.recordFailure())
        assertTrue(canContinueOriginalPlaybackSkip(failureCount = 1, queueSize = 2))
    }

    @Test
    fun `deferred shuffled recovery preserves the existing permutation`() {
        val queueSize = 8
        val seed = 73L
        val originalAnchor = 2
        val originalOrder = SakiShuffleOrder(queueSize, seed, originalAnchor).toDisplayOrder()
        val recoveryTarget = originalOrder[1]

        val recoveryAnchor = deferredShuffleAnchorAfterSelection(
            currentAnchorIndex = originalAnchor,
            selectedSongIndex = recoveryTarget,
            isFailureRecovery = true,
        )

        assertEquals(originalAnchor, recoveryAnchor)
        assertEquals(
            originalOrder,
            SakiShuffleOrder(queueSize, seed, recoveryAnchor).toDisplayOrder(),
        )
    }

    @Test
    fun `manual deferred selection may establish a new shuffle anchor`() {
        assertEquals(
            5,
            deferredShuffleAnchorAfterSelection(
                currentAnchorIndex = 2,
                selectedSongIndex = 5,
                isFailureRecovery = false,
            ),
        )
    }

    @Test
    fun `virtual recovery extends beyond the active media window`() {
        assertEquals(
            VirtualQueueRecoveryAction.EXTEND_FORWARD,
            planVirtualQueueRecovery(
                failureCount = 121,
                logicalItemCount = null,
                hasNextInWindow = false,
                hasMoreAfter = true,
                repeatEnabled = false,
            ),
        )
        assertEquals(
            VirtualQueueRecoveryAction.ADVANCE_IN_WINDOW,
            planVirtualQueueRecovery(
                failureCount = 122,
                logicalItemCount = null,
                hasNextInWindow = true,
                hasMoreAfter = true,
                repeatEnabled = false,
            ),
        )
    }

    @Test
    fun `virtual recovery uses the discovered logical queue boundary`() {
        assertEquals(
            VirtualQueueRecoveryAction.WRAP_TO_START,
            planVirtualQueueRecovery(
                failureCount = 2_000,
                logicalItemCount = 5_000,
                hasNextInWindow = false,
                hasMoreAfter = false,
                repeatEnabled = true,
            ),
        )
        assertEquals(
            VirtualQueueRecoveryAction.STOP,
            planVirtualQueueRecovery(
                failureCount = 5_000,
                logicalItemCount = 5_000,
                hasNextInWindow = true,
                hasMoreAfter = true,
                repeatEnabled = true,
            ),
        )
    }
}
