package org.hdhmc.saki.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Custom [DefaultLoadControl] wrapper used to keep remote stream buffering
 * within a predictable Java heap budget.
 *
 * CUSTOM buffering uses a short player buffer plus disk prefetching. The
 * byte cap must therefore remain effective; otherwise ExoPlayer may keep
 * loading forward data into on-heap allocator segments until [maxBufferMs].
 */
@UnstableApi
class SakiLoadControl(
    minBufferMs: Int,
    maxBufferMs: Int,
    bufferForPlaybackMs: Int,
    bufferForPlaybackAfterRebufferMs: Int,
    targetBufferBytes: Int = DEFAULT_TARGET_BUFFER_BYTES,
    prioritizeTimeOverSizeThresholds: Boolean = DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    minBufferMs,
    minBufferMs,
    maxBufferMs,
    maxBufferMs,
    bufferForPlaybackMs,
    bufferForPlaybackMs,
    bufferForPlaybackAfterRebufferMs,
    bufferForPlaybackAfterRebufferMs,
    targetBufferBytes,
    prioritizeTimeOverSizeThresholds,
    DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS_FOR_LOCAL_PLAYBACK,
    DEFAULT_BACK_BUFFER_DURATION_MS,
    DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
) {
    companion object {
        private const val MIN_CUSTOM_TARGET_BUFFER_BYTES = 16 * 1024 * 1024
        private const val MAX_CUSTOM_TARGET_BUFFER_BYTES = 96 * 1024 * 1024

        fun customTargetBufferBytes(): Int {
            val heapQuarter = Runtime.getRuntime().maxMemory() / 4L
            return heapQuarter
                .coerceIn(
                    MIN_CUSTOM_TARGET_BUFFER_BYTES.toLong(),
                    MAX_CUSTOM_TARGET_BUFFER_BYTES.toLong(),
                )
                .toInt()
        }
    }
}
