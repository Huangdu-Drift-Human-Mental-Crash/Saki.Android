package org.hdhmc.saki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlacDecoderModeTest {
    @Test
    fun storageKeysRoundTrip() {
        AlacDecoderMode.entries.forEach { mode ->
            assertEquals(mode, AlacDecoderMode.fromStorageKey(mode.storageKey))
        }
    }

    @Test
    fun missingOrUnknownStorageKeyUsesAuto() {
        assertEquals(AlacDecoderMode.AUTO, AlacDecoderMode.fromStorageKey(null))
        assertEquals(AlacDecoderMode.AUTO, AlacDecoderMode.fromStorageKey("future-mode"))
    }
}
