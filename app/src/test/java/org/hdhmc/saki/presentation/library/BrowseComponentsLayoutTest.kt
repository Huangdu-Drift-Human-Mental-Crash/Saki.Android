package org.hdhmc.saki.presentation.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseComponentsLayoutTest {
    @Test
    fun landscapeDetailUsesTwoPanesWhenBothPanesRemainUsable() {
        assertTrue(supportsLibraryDetailTwoPane(width = 800.dp, height = 400.dp))
        assertTrue(supportsLibraryDetailTwoPane(width = 1152.dp, height = 720.dp))
        assertFalse(supportsLibraryDetailTwoPane(width = 700.dp, height = 400.dp))
        assertFalse(supportsLibraryDetailTwoPane(width = 800.dp, height = 300.dp))
        assertFalse(supportsLibraryDetailTwoPane(width = 800.dp, height = 800.dp))
    }

    @Test
    fun twoPaneMetricsPrioritizeTheTrackListAndCapTabletLineLength() {
        val phone = calculateLibraryDetailTwoPaneMetrics(800.dp)
        assertEquals(272.dp, phone.infoPaneWidth)
        assertEquals(508.dp, phone.contentPaneWidth)
        assertEquals(800.dp, phone.stageWidth)

        val tablet = calculateLibraryDetailTwoPaneMetrics(1280.dp)
        assertEquals(320.dp, tablet.infoPaneWidth)
        assertEquals(640.dp, tablet.contentPaneWidth)
        assertEquals(980.dp, tablet.stageWidth)
    }

    @Test
    fun artistHeaderCompactsOnlyInShortLandscapePanes() {
        assertTrue(shouldUseCompactArtistDetailHeader(400.dp))
        assertFalse(shouldUseCompactArtistDetailHeader(520.dp))
        assertFalse(shouldUseCompactArtistDetailHeader(720.dp))
    }

    @Test
    fun detailHeroIsCappedByCompactLandscapeHeight() {
        assertEquals(
            269.dp,
            calculateLibraryDetailHeroWidth(paneWidth = 296.dp, usableHeight = 295.dp),
        )
        assertEquals(
            320.dp,
            calculateLibraryDetailHeroWidth(paneWidth = 320.dp, usableHeight = 616.dp),
        )
    }
}
