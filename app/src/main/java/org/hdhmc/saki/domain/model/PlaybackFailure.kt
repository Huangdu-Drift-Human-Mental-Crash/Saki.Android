package org.hdhmc.saki.domain.model

enum class PlaybackFailureKind {
    UNSUPPORTED_FORMAT,
    SOURCE_UNAVAILABLE,
    DECODING_FAILED,
    UNKNOWN,
}

data class PlaybackFailure(
    val eventId: Long,
    val kind: PlaybackFailureKind,
    val trackTitle: String?,
    val formatLabel: String?,
)
