package org.hdhmc.saki.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class NavigationPreservingTimelineTest {
    @Test
    fun usesSourceNavigationWhileRetainingLogicalTimelineContent() {
        val logicalTimeline = StubTimeline(uidPrefix = "logical", durationUs = 123_000L)
        val sourceTimeline = ShuffledTimeline(uidPrefix = "source", durationUs = 456_000L)
        val timeline = NavigationPreservingTimeline(
            contentTimeline = logicalTimeline,
            navigationTimeline = sourceTimeline,
        )

        assertEquals(2, timeline.getFirstWindowIndex(true))
        assertEquals(1, timeline.getLastWindowIndex(true))
        assertEquals(3, timeline.getNextWindowIndex(0, Player.REPEAT_MODE_ALL, true))
        assertEquals(2, timeline.getPreviousWindowIndex(0, Player.REPEAT_MODE_ALL, true))
        assertEquals(2, timeline.getNextWindowIndex(1, Player.REPEAT_MODE_ALL, true))
        assertEquals(1, timeline.getNextWindowIndex(0, Player.REPEAT_MODE_ALL, false))

        assertEquals("logical-2", timeline.getUidOfPeriod(2))
        assertEquals(123_000L, timeline.getWindow(0, Timeline.Window()).durationUs)
    }

    private open class StubTimeline(
        private val uidPrefix: String,
        private val durationUs: Long,
    ) : Timeline() {
        override fun getWindowCount(): Int = 4

        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long,
        ): Window = window.apply {
            uid = "$uidPrefix-window-$windowIndex"
            durationUs = this@StubTimeline.durationUs
        }

        override fun getPeriodCount(): Int = 4

        override fun getPeriod(
            periodIndex: Int,
            period: Period,
            setIds: Boolean,
        ): Period = period

        override fun getIndexOfPeriod(uid: Any): Int =
            uid.toString().substringAfterLast('-').toIntOrNull() ?: C.INDEX_UNSET

        override fun getUidOfPeriod(periodIndex: Int): Any = "$uidPrefix-$periodIndex"
    }

    private class ShuffledTimeline(
        uidPrefix: String,
        durationUs: Long,
    ) : StubTimeline(uidPrefix, durationUs) {
        private val order = intArrayOf(2, 0, 3, 1)
        private val positions = IntArray(order.size).also { positionByIndex ->
            order.forEachIndexed { position, index -> positionByIndex[index] = position }
        }

        override fun getNextWindowIndex(
            windowIndex: Int,
            repeatMode: Int,
            shuffleModeEnabled: Boolean,
        ): Int {
            if (!shuffleModeEnabled) {
                return super.getNextWindowIndex(windowIndex, repeatMode, false)
            }
            if (repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
            val nextPosition = positions[windowIndex] + 1
            return when {
                nextPosition < order.size -> order[nextPosition]
                repeatMode == Player.REPEAT_MODE_ALL -> order.first()
                else -> C.INDEX_UNSET
            }
        }

        override fun getPreviousWindowIndex(
            windowIndex: Int,
            repeatMode: Int,
            shuffleModeEnabled: Boolean,
        ): Int {
            if (!shuffleModeEnabled) {
                return super.getPreviousWindowIndex(windowIndex, repeatMode, false)
            }
            if (repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
            val previousPosition = positions[windowIndex] - 1
            return when {
                previousPosition >= 0 -> order[previousPosition]
                repeatMode == Player.REPEAT_MODE_ALL -> order.last()
                else -> C.INDEX_UNSET
            }
        }

        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
            if (shuffleModeEnabled) order.first() else super.getFirstWindowIndex(false)

        override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int =
            if (shuffleModeEnabled) order.last() else super.getLastWindowIndex(false)
    }
}
