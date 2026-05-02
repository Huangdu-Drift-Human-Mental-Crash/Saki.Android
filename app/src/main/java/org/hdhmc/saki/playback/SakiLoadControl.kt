package org.hdhmc.saki.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Custom [DefaultLoadControl] that raises the target-bytes cap so the
 * configured [maxBufferMs] actually drives loading.
 *
 * The default behaviour computes a target from track bitrate × a fixed
 * buffer duration. For high-bitrate FLAC (~1400 kbps) this hits the byte
 * cap long before [maxBufferMs] does, causing the loader to stop at
 * ~30-50% of the track. When [lenientBufferBytes] is true we return a
 * larger fixed cap so [maxBufferMs] becomes the effective limit.
 *
 * The cap is 256 MiB — enough for ~24 min of 1500 kbps audio across all
 * buffered periods in the player, but low enough to avoid OOM on
 * low-RAM devices (Java heap is typically 192-256 MB).
 *
 * Note: this applies to the shared allocator's total buffered bytes for
 * the player, not per track. Actual memory use is still gated by
 * [maxBufferMs] and the allocator's on-demand allocation.
 */
@UnstableApi
class SakiLoadControl(
    private val lenientBufferBytes: Boolean,
    minBufferMs: Int,
    maxBufferMs: Int,
    bufferForPlaybackMs: Int,
    bufferForPlaybackAfterRebufferMs: Int,
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
    DEFAULT_TARGET_BUFFER_BYTES,
    DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
    DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS_FOR_LOCAL_PLAYBACK,
    DEFAULT_BACK_BUFFER_DURATION_MS,
    DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
) {
    override fun calculateTargetBufferBytes(
        parameters: LoadControl.Parameters,
        trackSelectionArray: Array<out ExoTrackSelection?>,
    ): Int {
        return if (lenientBufferBytes) {
            LENIENT_TARGET_BUFFER_BYTES
        } else {
            super.calculateTargetBufferBytes(parameters, trackSelectionArray)
        }
    }

    companion object {
        // 256 MiB cap — high enough to not bottleneck realistic buffer
        // durations, low enough to avoid OOM on low-RAM devices.
        private const val LENIENT_TARGET_BUFFER_BYTES = 256 * 1024 * 1024
    }
}
