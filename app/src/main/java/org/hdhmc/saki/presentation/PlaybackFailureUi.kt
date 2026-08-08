package org.hdhmc.saki.presentation

import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.PlaybackFailure
import org.hdhmc.saki.domain.model.PlaybackFailureKind

fun PlaybackFailure.toUiText(): UiText {
    val title = trackTitle?.takeIf(String::isNotBlank)
        ?: return UiText.resource(R.string.error_playback_failed)
    return when (kind) {
        PlaybackFailureKind.UNSUPPORTED_FORMAT -> {
            val format = formatLabel?.takeIf(String::isNotBlank)
            if (format != null) {
                UiText.resource(R.string.error_playback_unsupported_format, title, format)
            } else {
                UiText.resource(R.string.error_playback_unsupported, title)
            }
        }
        PlaybackFailureKind.SOURCE_UNAVAILABLE ->
            UiText.resource(R.string.error_playback_source_unavailable, title)
        PlaybackFailureKind.DECODING_FAILED ->
            UiText.resource(R.string.error_playback_decoding_failed, title)
        PlaybackFailureKind.UNKNOWN ->
            UiText.resource(R.string.error_playback_failed_for_song, title)
    }
}
