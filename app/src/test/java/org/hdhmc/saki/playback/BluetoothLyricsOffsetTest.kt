package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.normalizeBluetoothLyricsOffsetMs
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothLyricsOffsetTest {
    @Test
    fun `Bluetooth lyrics offset defaults to no adjustment`() {
        assertEquals(0, PlaybackPreferences().bluetoothLyricsOffsetMs)
    }

    @Test
    fun `Bluetooth lyrics offset is clamped and snapped to quarter seconds`() {
        assertEquals(0, normalizeBluetoothLyricsOffsetMs(-1))
        assertEquals(0, normalizeBluetoothLyricsOffsetMs(124))
        assertEquals(250, normalizeBluetoothLyricsOffsetMs(125))
        assertEquals(500, normalizeBluetoothLyricsOffsetMs(376))
        assertEquals(2_000, normalizeBluetoothLyricsOffsetMs(2_001))
    }

    @Test
    fun `Bluetooth lyrics lookup advances only its local lookup position`() {
        val playbackPositionMs = 10_000L

        assertEquals(
            playbackPositionMs,
            bluetoothLyricsLookupPositionMs(playbackPositionMs, offsetMs = 0),
        )
        assertEquals(
            10_250L,
            bluetoothLyricsLookupPositionMs(playbackPositionMs, offsetMs = 250),
        )
    }

    @Test
    fun `Bluetooth lyrics polling follows nearby line boundaries without busy polling`() {
        assertEquals(250L, bluetoothLyricsPollDelayMs(lookupPositionMs = 10_000L, nextLineStartMs = 10_250L))
        assertEquals(50L, bluetoothLyricsPollDelayMs(lookupPositionMs = 10_000L, nextLineStartMs = 9_900L))
        assertEquals(500L, bluetoothLyricsPollDelayMs(lookupPositionMs = 10_000L, nextLineStartMs = 12_000L))
        assertEquals(500L, bluetoothLyricsPollDelayMs(lookupPositionMs = 10_000L, nextLineStartMs = null))
    }
}
