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

    @Test
    fun compactFastScrollUsesPredictableAlphabetAnchors() {
        val labels = ('A'..'Z').map(Char::toString)
        assertEquals(
            listOf("A", "·", "N", "·", "Z"),
            fastScrollDisplayLabels(
                labels = labels,
                availableHeight = 72.dp,
                minimumLabelSlotHeight = 12.dp,
            ).map(FastScrollDisplayLabel::text),
        )
    }

    @Test
    fun compactFastScrollPreservesSpecialSections() {
        val labels = listOf("#") + ('A'..'Z').map(Char::toString) + "…"
        assertEquals(
            listOf("#", "A", "·", "N", "·", "Z", "…"),
            fastScrollDisplayLabels(
                labels = labels,
                availableHeight = 96.dp,
                minimumLabelSlotHeight = 12.dp,
            ).map(FastScrollDisplayLabel::text),
        )
    }

    @Test
    fun fastScrollKeepsEveryLabelWhenHeightAllows() {
        val labels = ('A'..'H').map(Char::toString)
        assertEquals(
            labels,
            fastScrollDisplayLabels(
                labels = labels,
                availableHeight = 96.dp,
                minimumLabelSlotHeight = 12.dp,
            ).map(FastScrollDisplayLabel::text),
        )
    }

    @Test
    fun veryShortFastScrollStillExposesBothEnds() {
        assertEquals(
            listOf("A", "Z"),
            fastScrollDisplayLabels(
                labels = ('A'..'Z').map(Char::toString),
                availableHeight = 8.dp,
                minimumLabelSlotHeight = 12.dp,
            ).map(FastScrollDisplayLabel::text),
        )
    }

    @Test
    fun adaptiveFastScrollUsesTheUnobstructedScreenEdge() {
        assertEquals(96.dp, fastScrollBottomOverlayPadding(width = 599.dp, overlayPadding = 96.dp))
        assertEquals(16.dp, fastScrollBottomOverlayPadding(width = 600.dp, overlayPadding = 96.dp))
        assertEquals(16.dp, fastScrollBottomOverlayPadding(width = 869.dp, overlayPadding = 96.dp))
        assertEquals(8.dp, fastScrollBottomOverlayPadding(width = 869.dp, overlayPadding = 8.dp))
    }

    @Test
    fun compactFastScrollHitRegionsFollowDisplayedAnchors() {
        val displayLabels = fastScrollDisplayLabels(
            labels = ('A'..'Z').map(Char::toString),
            availableHeight = 72.dp,
            minimumLabelSlotHeight = 12.dp,
        )

        assertEquals(0, fastScrollSourceIndexAtPosition(10f, 100f, displayLabels))
        assertEquals(7, fastScrollSourceIndexAtPosition(30f, 100f, displayLabels))
        assertEquals(13, fastScrollSourceIndexAtPosition(50f, 100f, displayLabels))
        assertEquals(19, fastScrollSourceIndexAtPosition(70f, 100f, displayLabels))
        assertEquals(25, fastScrollSourceIndexAtPosition(90f, 100f, displayLabels))
    }

    @Test
    fun compactFastScrollHighlightStaysInTheTouchedDisplaySlot() {
        assertEquals(0, fastScrollDisplayIndexAtPosition(10f, 100f, 5))
        assertEquals(1, fastScrollDisplayIndexAtPosition(30f, 100f, 5))
        assertEquals(2, fastScrollDisplayIndexAtPosition(50f, 100f, 5))
        assertEquals(4, fastScrollDisplayIndexAtPosition(100f, 100f, 5))
    }

    @Test
    fun fullFastScrollHitRegionsMapDirectlyToTheirRows() {
        val displayLabels = ('A'..'D').mapIndexed { index, label ->
            FastScrollDisplayLabel(label.toString(), sourceIndex = index)
        }

        assertEquals(0, fastScrollSourceIndexAtPosition(0f, 100f, displayLabels))
        assertEquals(1, fastScrollSourceIndexAtPosition(25f, 100f, displayLabels))
        assertEquals(3, fastScrollSourceIndexAtPosition(100f, 100f, displayLabels))
    }

}
