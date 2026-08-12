package org.hdhmc.saki.playback

import androidx.media3.exoplayer.source.ShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SakiShuffleOrderTest {
    @Test
    fun `cloneAndSet rebuilds a deterministic permutation around the requested start`() {
        val seed = 73L
        val replacement = SakiShuffleOrder(length = 8, seed = seed, anchorIndex = 2)
            .cloneAndSet(insertionCount = 8, startIndex = 5)

        assertTrue(replacement is SakiShuffleOrder)
        replacement as SakiShuffleOrder
        assertEquals(5, replacement.firstIndex)
        assertEquals((0 until 8).toList(), replacement.toDisplayOrder().sorted())
        assertEquals(
            SakiShuffleOrder(length = 8, seed = seed, anchorIndex = 5).toDisplayOrder(),
            replacement.toDisplayOrder(),
        )
    }

    @Test
    fun `cloneAndSet does not use the linear clear and insert fallback`() {
        val replacement = SakiShuffleOrder(length = 6, seed = 41L, anchorIndex = 1)
            .cloneAndSet(insertionCount = 6, startIndex = 4) as SakiShuffleOrder

        assertEquals(4, replacement.firstIndex)
        assertTrue(replacement.toDisplayOrder() != (0 until 6).toList())
    }

    @Test
    fun `shuffle mutations retain valid permutations`() {
        val initial = SakiShuffleOrder(length = 5, seed = 19L, anchorIndex = 3)
        val inserted = initial.cloneAndInsert(insertionIndex = 2, insertionCount = 2)
        val removed = inserted.cloneAndRemove(1, 3)

        assertPermutation(inserted, 7)
        assertPermutation(removed, 5)
        assertEquals(0, removed.cloneAndClear().length)
    }

    private fun assertPermutation(order: ShuffleOrder, length: Int) {
        val values = buildList {
            var index = order.firstIndex
            while (index != androidx.media3.common.C.INDEX_UNSET) {
                add(index)
                index = order.getNextIndex(index)
            }
        }
        assertEquals((0 until length).toList(), values.sorted())
    }
}
