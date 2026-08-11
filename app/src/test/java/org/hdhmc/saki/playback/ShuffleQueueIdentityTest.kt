package org.hdhmc.saki.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleQueueIdentityTest {
    private val queue = listOf(
        ShuffleQueueItemIdentity(serverId = 7L, songId = "one"),
        ShuffleQueueItemIdentity(serverId = 7L, songId = "two"),
        ShuffleQueueItemIdentity(serverId = 7L, songId = "three"),
    )

    @Test
    fun `same-length old generation cannot consume a new shuffle request`() {
        val identity = shuffleQueueIdentity(queue)
        val target = ShuffleQueueTarget(
            itemCount = queue.size,
            identity = identity,
            generation = 12L,
        )

        assertFalse(
            target.matches(
                ShuffleQueueSnapshot(
                    itemCount = queue.size,
                    identity = identity,
                    generation = 11L,
                ),
            ),
        )
        assertTrue(
            target.matches(
                ShuffleQueueSnapshot(
                    itemCount = queue.size,
                    identity = identity,
                    generation = 12L,
                ),
            ),
        )
    }

    @Test
    fun `partial and reordered queues do not match the requested target`() {
        val identity = shuffleQueueIdentity(queue)
        val target = ShuffleQueueTarget(queue.size, identity, generation = 9L)

        assertFalse(
            target.matches(
                ShuffleQueueSnapshot(
                    itemCount = queue.size - 1,
                    identity = shuffleQueueIdentity(queue.dropLast(1)),
                    generation = 9L,
                ),
            ),
        )
        val reorderedIdentity = shuffleQueueIdentity(queue.reversed())
        assertNotEquals(identity, reorderedIdentity)
        assertFalse(
            target.matches(
                ShuffleQueueSnapshot(queue.size, reorderedIdentity, generation = 9L),
            ),
        )
    }

    @Test
    fun `identity remains a safe fallback for queues without generation metadata`() {
        val identity = shuffleQueueIdentity(queue)
        val target = ShuffleQueueTarget(queue.size, identity, generation = null)

        assertTrue(
            target.matches(
                ShuffleQueueSnapshot(queue.size, identity, generation = null),
            ),
        )
        assertFalse(
            target.matches(
                ShuffleQueueSnapshot(queue.size, identity, generation = 1L),
            ),
        )
        assertFalse(
            target.matches(
                ShuffleQueueSnapshot(
                    queue.size,
                    shuffleQueueIdentity(queue.reversed()),
                    generation = null,
                ),
            ),
        )
    }
}
