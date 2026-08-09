package org.hdhmc.saki.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hdhmc.saki.domain.model.PlaybackFailure
import org.hdhmc.saki.domain.model.PlaybackFailureKind

internal data class PlaybackRecoveryItemKey(
    val serverId: Long,
    val songId: String,
)

internal class OriginalPlaybackSkipRecovery {
    private var failureCount = 0
    private var expectedTransition: PlaybackRecoveryItemKey? = null

    fun recordFailure(): Int {
        expectedTransition = null
        if (failureCount < Int.MAX_VALUE) failureCount += 1
        return failureCount
    }

    fun expectAutomaticTransition(target: PlaybackRecoveryItemKey) {
        expectedTransition = target
    }

    fun onMediaItemTransition(item: PlaybackRecoveryItemKey?) {
        val continuesRecovery = expectedTransition != null && expectedTransition == item
        expectedTransition = null
        if (!continuesRecovery) failureCount = 0
    }

    fun reset() {
        failureCount = 0
        expectedTransition = null
    }
}

internal fun canContinueOriginalPlaybackSkip(
    failureCount: Int,
    queueSize: Int,
): Boolean = queueSize > 1 && failureCount in 1 until queueSize

@Singleton
class PlaybackFailureReporter @Inject constructor() {
    private val mutableFailure = MutableStateFlow<PlaybackFailure?>(null)
    private val originalPlaybackSkipRecovery = OriginalPlaybackSkipRecovery()
    private var nextEventId = 0L
    @Volatile
    private var originalPlaybackSkipHandler:
        (suspend (PlaybackRecoveryItemKey, Int) -> Boolean)? = null

    val failure: StateFlow<PlaybackFailure?> = mutableFailure.asStateFlow()

    internal fun setOriginalPlaybackSkipHandler(
        handler: suspend (PlaybackRecoveryItemKey, Int) -> Boolean,
    ) {
        originalPlaybackSkipHandler = handler
    }

    internal suspend fun requestOriginalPlaybackSkip(
        failedItem: PlaybackRecoveryItemKey,
        failureCount: Int,
    ): Boolean = originalPlaybackSkipHandler?.invoke(failedItem, failureCount) == true

    @Synchronized
    internal fun recordOriginalPlaybackSkipFailure(): Int = originalPlaybackSkipRecovery.recordFailure()

    @Synchronized
    internal fun expectAutomaticSkipTransition(target: PlaybackRecoveryItemKey) {
        originalPlaybackSkipRecovery.expectAutomaticTransition(target)
    }

    @Synchronized
    internal fun onMediaItemTransition(item: PlaybackRecoveryItemKey?) {
        originalPlaybackSkipRecovery.onMediaItemTransition(item)
    }

    @Synchronized
    internal fun resetOriginalPlaybackSkipRecovery() {
        originalPlaybackSkipRecovery.reset()
    }

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
