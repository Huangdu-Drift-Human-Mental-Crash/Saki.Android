package org.hdhmc.saki.playback

import androidx.media3.common.PlaybackException
import org.hdhmc.saki.domain.model.PlaybackFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailureClassifierTest {
    @Test
    fun unsupportedContainerErrorIsReportedAsUnsupportedFormat() {
        assertEquals(
            PlaybackFailureKind.UNSUPPORTED_FORMAT,
            classifyPlaybackFailure(
                errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                suffix = null,
                contentType = null,
            ),
        )
    }

    @Test
    fun wmaParsingFailureIsReportedAsUnsupportedFormat() {
        assertEquals(
            PlaybackFailureKind.UNSUPPORTED_FORMAT,
            classifyPlaybackFailure(
                errorCode = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                suffix = "wma",
                contentType = "audio/x-ms-wma",
            ),
        )
    }

    @Test
    fun ioFailureIsReportedAsUnavailableSource() {
        assertEquals(
            PlaybackFailureKind.SOURCE_UNAVAILABLE,
            classifyPlaybackFailure(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                suffix = "mp3",
                contentType = "audio/mpeg",
            ),
        )
    }

    @Test
    fun decoderFailureIsReportedAsDecodingFailure() {
        assertEquals(
            PlaybackFailureKind.DECODING_FAILED,
            classifyPlaybackFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                suffix = "flac",
                contentType = "audio/flac",
            ),
        )
    }
}
