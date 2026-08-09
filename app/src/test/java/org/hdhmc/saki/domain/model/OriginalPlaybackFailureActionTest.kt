package org.hdhmc.saki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalPlaybackFailureActionTest {
    @Test
    fun `playback failure action defaults to stop`() {
        assertEquals(
            OriginalPlaybackFailureAction.STOP,
            PlaybackPreferences().originalPlaybackFailureAction,
        )
        assertEquals(
            OriginalPlaybackFailureAction.STOP,
            OriginalPlaybackFailureAction.fromStorageKey(null),
        )
        assertEquals(
            OriginalPlaybackFailureAction.STOP,
            OriginalPlaybackFailureAction.fromStorageKey("future_value"),
        )
    }

    @Test
    fun `playback failure action restores persisted value`() {
        OriginalPlaybackFailureAction.entries.forEach { action ->
            assertEquals(
                action,
                OriginalPlaybackFailureAction.fromStorageKey(action.storageKey),
            )
        }
    }
}
