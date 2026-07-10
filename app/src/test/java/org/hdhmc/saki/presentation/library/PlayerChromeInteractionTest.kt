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
    fun controlPriorityTransportAdaptsAtNarrowWindowBoundaries() {
        assertEquals(
            NowPlayingTransportLayout.Standard,
            controlPriorityNowPlayingTransportLayout(296.dp),
        )
        assertEquals(
            NowPlayingTransportLayout.CompactIconOnly,
            controlPriorityNowPlayingTransportLayout(280.dp),
        )
        assertEquals(
            NowPlayingTransportLayout.CompactIconOnly,
            controlPriorityNowPlayingTransportLayout(248.dp),
        )
        assertEquals(
            NowPlayingTransportLayout.Stacked,
            controlPriorityNowPlayingTransportLayout(200.dp),
        )
    }

    @Test
    fun resizingIntoControlPriorityLayoutDismissesLyrics() {
        assertTrue(
            shouldDismissLyricsForControlPriorityLayout(
                showLyrics = true,
                useControlPriorityLayout = true,
            ),
        )
        assertFalse(
            shouldDismissLyricsForControlPriorityLayout(
                showLyrics = true,
                useControlPriorityLayout = false,
            ),
        )
        assertFalse(
            shouldDismissLyricsForControlPriorityLayout(
                showLyrics = false,
                useControlPriorityLayout = true,
            ),
        )
    }

    @Test
    fun nowPlayingAutoScrollUsesSharedVelocityAndMinimumDuration() {
        assertEquals(1_000, nowPlayingAutoScrollDurationMillis(distancePx = 32, speedPxPerMs = 0.032f))
        assertEquals(350, nowPlayingAutoScrollDurationMillis(distancePx = 1, speedPxPerMs = 0.032f))
        assertEquals(350, nowPlayingAutoScrollDurationMillis(distancePx = 32, speedPxPerMs = 0f))
    }

    @Test
    fun tooNarrowLandscapeFallsBackToControlPriorityLayout() {
        assertFalse(supportsLargeScreenNowPlayingLayout(450.dp, 400.dp))
        assertFalse(supportsCompactLandscapeNowPlayingLayout(450.dp, 400.dp))
    }
}
