package org.hdhmc.saki.presentation.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChromeInteractionTest {
    @Test
    fun accessibilityPagerScrollArmsAndSelectsPlaybackPage() {
        val armed = shouldArmArtworkPagerNavigation(
            isScrollInProgress = true,
            isProgrammaticSync = false,
        )

        assertTrue(armed)
        assertEquals(
            2,
            settledArtworkPagerSelection(
                page = 2,
                currentPlaybackIndex = 1,
                queueSize = 4,
                userNavigationArmed = armed,
            ),
        )
    }

    @Test
    fun resizePageChangeDoesNotSelectPlaybackPage() {
        val armed = shouldArmArtworkPagerNavigation(
            isScrollInProgress = false,
            isProgrammaticSync = false,
        )

        assertFalse(armed)
        assertNull(
            settledArtworkPagerSelection(
                page = 2,
                currentPlaybackIndex = 1,
                queueSize = 4,
                userNavigationArmed = armed,
            ),
        )
    }

    @Test
    fun programmaticPagerScrollDoesNotArmUserSelection() {
        assertFalse(
            shouldArmArtworkPagerNavigation(
                isScrollInProgress = true,
                isProgrammaticSync = true,
            ),
        )
    }

    @Test
    fun compactLandscapeKeepsMinimumControlBoundsAtFiveHundredByFourHundred() {
        assertTrue(supportsCompactLandscapeNowPlayingLayout(500.dp, 400.dp))
        val metrics = calculateCompactLandscapeStageMetrics(500.dp, 400.dp)

        assertTrue(metrics.artworkSize > 0.dp)
        assertTrue(metrics.controlWidth >= 280.dp)
        assertTrue(metrics.controlHeight >= 280.dp)
    }

    @Test
    fun nearSquareWindowDoesNotEnterLargeScreenLayout() {
        assertFalse(supportsLargeScreenNowPlayingLayout(601.dp, 600.dp))
        assertTrue(supportsCompactLandscapeNowPlayingLayout(601.dp, 600.dp))
        val metrics = calculateCompactLandscapeStageMetrics(601.dp, 600.dp)

        assertTrue(metrics.controlWidth >= 280.dp)
        assertTrue(metrics.controlHeight >= 280.dp)
    }

    @Test
    fun tabletWindowUsesLargeScreenLayout() {
        assertTrue(supportsLargeScreenNowPlayingLayout(1152.dp, 720.dp))
    }

    @Test
    fun tooNarrowLandscapeFallsBackToControlPriorityLayout() {
        assertFalse(supportsLargeScreenNowPlayingLayout(450.dp, 400.dp))
        assertFalse(supportsCompactLandscapeNowPlayingLayout(450.dp, 400.dp))
    }
}
