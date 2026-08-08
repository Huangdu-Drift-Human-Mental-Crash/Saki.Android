package org.hdhmc.saki.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hdhmc.saki.domain.model.PlaybackFailure
import org.hdhmc.saki.domain.model.PlaybackFailureKind

@Singleton
class PlaybackFailureReporter @Inject constructor() {
    private val mutableFailure = MutableStateFlow<PlaybackFailure?>(null)
    private var nextEventId = 0L

    val failure: StateFlow<PlaybackFailure?> = mutableFailure.asStateFlow()

    @Synchronized
    fun report(
        kind: PlaybackFailureKind,
        trackTitle: String?,
        formatLabel: String?,
    ) {
        nextEventId += 1L
        mutableFailure.value = PlaybackFailure(
            eventId = nextEventId,
            kind = kind,
            trackTitle = trackTitle,
            formatLabel = formatLabel,
        )
    }

    @Synchronized
    fun clear() {
        mutableFailure.value = null
    }
}
