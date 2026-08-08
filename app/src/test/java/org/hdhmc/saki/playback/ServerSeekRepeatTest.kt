package org.hdhmc.saki.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import org.hdhmc.saki.data.remote.NetworkType
import org.hdhmc.saki.domain.model.PlaybackPreferences
import org.hdhmc.saki.domain.model.PlaybackFailureKind
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSeekRepeatTest {
    @Test
    fun `repeating the current offset stream pauses at its end`() {
        assertTrue(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_ONE, mediaItemCount = 3))
        assertTrue(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_ALL, mediaItemCount = 1))
    }

    @Test
    fun `repeat all with a following item does not pause the offset stream`() {
        assertFalse(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_ALL, mediaItemCount = 3))
        assertFalse(shouldPauseOffsetStreamAtEnd(Player.REPEAT_MODE_OFF, mediaItemCount = 1))
    }

    @Test
    fun `server-side seek requires confirmed transcoding`() {
        assertTrue(isConfirmedTranscode(sourceBitRate = 320, requestedBitRate = 128))
        assertFalse(isConfirmedTranscode(sourceBitRate = 128, requestedBitRate = 320))
        assertFalse(isConfirmedTranscode(sourceBitRate = null, requestedBitRate = 128))
        assertFalse(isConfirmedTranscode(sourceBitRate = 320, requestedBitRate = null))
    }

    @Test
    fun `server-side seek follows the opened stream quality`() {
        assertTrue(
            supportsTranscodedServerSeek(
                isCached = false,
                sourceBitRate = 320,
                openedStreamQuality = StreamQuality.KBPS_128,
            ),
        )
        assertFalse(
            supportsTranscodedServerSeek(
                isCached = false,
                sourceBitRate = 320,
                openedStreamQuality = StreamQuality.ORIGINAL,
            ),
        )
        assertTrue(
            supportsTranscodedServerSeek(
                isCached = true,
                sourceBitRate = 96,
                openedStreamQuality = StreamQuality.KBPS_320,
                forcedTranscode = true,
            ),
        )
    }

    @Test
    fun `original playback actions only handle format and decoding failures`() {
        assertTrue(isOriginalPlaybackFailure(PlaybackFailureKind.UNSUPPORTED_FORMAT))
        assertTrue(isOriginalPlaybackFailure(PlaybackFailureKind.DECODING_FAILED))
        assertFalse(isOriginalPlaybackFailure(PlaybackFailureKind.SOURCE_UNAVAILABLE))
        assertFalse(isOriginalPlaybackFailure(PlaybackFailureKind.UNKNOWN))
    }

    @Test
    fun `original playback action requires an original non-forced stream`() {
        assertTrue(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.UNSUPPORTED_FORMAT,
                openedStreamQuality = StreamQuality.ORIGINAL,
                requestedStreamQuality = StreamQuality.KBPS_128,
                sourceBitRate = null,
                sourceSuffix = "wma",
                sourceContentType = "audio/x-ms-wma",
                forcedTranscode = false,
            ),
        )
        assertFalse(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.DECODING_FAILED,
                openedStreamQuality = StreamQuality.KBPS_128,
                requestedStreamQuality = StreamQuality.ORIGINAL,
                sourceBitRate = 320,
                sourceSuffix = "mp3",
                sourceContentType = "audio/mpeg",
                forcedTranscode = false,
            ),
        )
        assertFalse(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.DECODING_FAILED,
                openedStreamQuality = StreamQuality.ORIGINAL,
                requestedStreamQuality = StreamQuality.ORIGINAL,
                sourceBitRate = null,
                sourceSuffix = "wma",
                sourceContentType = "audio/x-ms-wma",
                forcedTranscode = true,
            ),
        )
    }

    @Test
    fun `capped pass-through stream is treated as original`() {
        assertTrue(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.UNSUPPORTED_FORMAT,
                openedStreamQuality = StreamQuality.KBPS_320,
                requestedStreamQuality = StreamQuality.KBPS_320,
                sourceBitRate = 192,
                sourceSuffix = "wma",
                sourceContentType = "audio/x-ms-wma",
                forcedTranscode = false,
            ),
        )
        assertFalse(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.DECODING_FAILED,
                openedStreamQuality = StreamQuality.KBPS_128,
                requestedStreamQuality = StreamQuality.KBPS_128,
                sourceBitRate = 320,
                sourceSuffix = "mp3",
                sourceContentType = "audio/mpeg",
                forcedTranscode = false,
            ),
        )
    }

    @Test
    fun `known unsupported container is pass-through evidence without bitrate`() {
        assertTrue(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.UNSUPPORTED_FORMAT,
                openedStreamQuality = StreamQuality.KBPS_320,
                requestedStreamQuality = StreamQuality.KBPS_320,
                sourceBitRate = null,
                sourceSuffix = "WMA",
                sourceContentType = null,
                forcedTranscode = false,
            ),
        )
        assertFalse(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.DECODING_FAILED,
                openedStreamQuality = StreamQuality.KBPS_320,
                requestedStreamQuality = StreamQuality.KBPS_320,
                sourceBitRate = null,
                sourceSuffix = "mp3",
                sourceContentType = "audio/mpeg",
                forcedTranscode = false,
            ),
        )
    }

    @Test
    fun `downloaded stream uses its persisted quality for original fallback`() {
        assertTrue(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.UNSUPPORTED_FORMAT,
                openedStreamQuality = null,
                requestedStreamQuality = StreamQuality.KBPS_320,
                sourceBitRate = 1_411,
                sourceSuffix = "wma",
                sourceContentType = "audio/x-ms-wma",
                forcedTranscode = false,
                localStreamQuality = StreamQuality.ORIGINAL,
            ),
        )
        assertFalse(
            shouldApplyOriginalPlaybackFailureAction(
                kind = PlaybackFailureKind.DECODING_FAILED,
                openedStreamQuality = null,
                requestedStreamQuality = StreamQuality.ORIGINAL,
                sourceBitRate = 320,
                sourceSuffix = "mp3",
                sourceContentType = "audio/mpeg",
                forcedTranscode = false,
                localStreamQuality = StreamQuality.KBPS_320,
            ),
        )
    }

    @Test
    fun `pending queue skip advances and respects end repeat mode`() {
        assertEquals(3, nextPendingQueueDisplayIndex(2, 5, Player.REPEAT_MODE_OFF))
        assertEquals(null, nextPendingQueueDisplayIndex(4, 5, Player.REPEAT_MODE_OFF))
        assertEquals(0, nextPendingQueueDisplayIndex(4, 5, Player.REPEAT_MODE_ALL))
        assertEquals(0, nextPendingQueueDisplayIndex(4, 5, Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `downloaded original can retry remotely only while server is reachable`() {
        assertTrue(
            canRetryOriginalWithForcedTranscode(
                usesLocalSource = true,
                isOfflineDegraded = false,
            ),
        )
        assertFalse(
            canRetryOriginalWithForcedTranscode(
                usesLocalSource = true,
                isOfflineDegraded = true,
            ),
        )
        assertTrue(
            canRetryOriginalWithForcedTranscode(
                usesLocalSource = false,
                isOfflineDegraded = true,
            ),
        )
    }

    @Test
    fun `only whole-resource EOF marks a stream complete`() {
        assertTrue(isResourceEof(requestLength = C.LENGTH_UNSET.toLong(), bytesRead = 8_192L))
        assertTrue(isResourceEof(requestLength = 8_192L, bytesRead = 4_096L))
        assertFalse(isResourceEof(requestLength = 8_192L, bytesRead = 8_192L))
    }

    @Test
    fun `adaptive quality overrides the queue time stream quality`() {
        val prefs = PlaybackPreferences(
            adaptiveQualityEnabled = true,
            wifiStreamQuality = StreamQuality.KBPS_320,
            mobileStreamQuality = StreamQuality.KBPS_128,
        )

        val effectiveQuality = selectEffectiveStreamQuality(
            prefs = prefs,
            networkType = NetworkType.MOBILE,
            fallbackMaxBitRate = StreamQuality.KBPS_320.maxBitRate,
        )

        assertEquals(StreamQuality.KBPS_128, effectiveQuality)
    }
}
