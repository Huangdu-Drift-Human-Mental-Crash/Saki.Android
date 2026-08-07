package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.model.StreamQuality
import kotlin.math.roundToLong

internal fun estimateSongStreamBytes(
    song: Song,
    quality: StreamQuality,
): Long? {
    val durationSeconds = song.durationSeconds?.takeIf { it > 0 }
    val sourceBitRate = song.bitRate?.takeIf { it > 0 }
    val sourceSize = song.sizeBytes?.takeIf { it > 0L }
    val requestedBitRate = quality.maxBitRate?.takeIf { it > 0 }

    if (requestedBitRate == null || sourceBitRate != null && sourceBitRate <= requestedBitRate) {
        return sourceSize ?: estimateFromBitRate(durationSeconds, sourceBitRate)
    }

    val effectiveBitRate = sourceBitRate?.coerceAtMost(requestedBitRate) ?: requestedBitRate
    return estimateFromBitRate(durationSeconds, effectiveBitRate)
        ?.let { bytes -> (bytes * TRANSCODE_ESTIMATE_OVERHEAD).roundToLong() }
}

private fun estimateFromBitRate(
    durationSeconds: Int?,
    bitRateKbps: Int?,
): Long? {
    if (durationSeconds == null || bitRateKbps == null) return null
    return durationSeconds.toLong() * bitRateKbps.toLong() * 1_000L / 8L
}

private const val TRANSCODE_ESTIMATE_OVERHEAD = 1.03
