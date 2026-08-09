package org.hdhmc.saki.data.download

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.net.URLDecoder
import java.net.URLEncoder
import org.hdhmc.saki.domain.model.PlaylistDownloadTask
import org.hdhmc.saki.domain.model.PlaylistDownloadTaskStatus
import org.hdhmc.saki.domain.model.StreamQuality

internal const val PLAYLIST_DOWNLOAD_WORK_NAME = "playlist-offline-download"
internal const val PLAYLIST_DOWNLOAD_BASE_TAG = "saki-playlist-download"

internal const val INPUT_SERVER_ID = "server_id"
internal const val INPUT_PLAN_ID = "plan_id"
internal const val INPUT_PLAYLIST_ID = "playlist_id"
internal const val INPUT_TITLE = "title"
internal const val INPUT_QUALITY = "quality"
internal const val INPUT_TOTAL = "total"
internal const val INPUT_ESTIMATED_BYTES = "estimated_bytes"
internal const val INPUT_CREATED_AT = "created_at"

private const val PROGRESS_PROCESSED = "processed"
private const val PROGRESS_DOWNLOADED = "downloaded"
private const val PROGRESS_SKIPPED = "skipped"
private const val PROGRESS_FAILED = "failed"
private const val PROGRESS_ACTIVE_IDS = "active_ids"
private const val PROGRESS_DOWNLOADED_BYTES = "downloaded_bytes"

private const val TAG_SERVER = "saki-download-server:"
private const val TAG_PLAN = "saki-download-plan:"
private const val TAG_PLAYLIST = "saki-download-playlist:"
private const val TAG_TITLE = "saki-download-title:"
private const val TAG_QUALITY = "saki-download-quality:"
private const val TAG_TOTAL = "saki-download-total:"
private const val TAG_ESTIMATED_BYTES = "saki-download-estimated:"
private const val TAG_CREATED_AT = "saki-download-created:"

data class PlaylistDownloadWorkInput(
    val serverId: Long,
    val planId: String,
    val playlistId: String,
    val title: String,
    val quality: StreamQuality,
    val totalSongCount: Int,
    val estimatedAdditionalBytes: Long,
    val createdAtMillis: Long,
) {
    val sourceKey: String
        get() = "server:$serverId:playlist:$playlistId"

    fun toData(): Data = workDataOf(
        INPUT_SERVER_ID to serverId,
        INPUT_PLAN_ID to planId,
        INPUT_PLAYLIST_ID to playlistId,
        INPUT_TITLE to title,
        INPUT_QUALITY to quality.storageKey,
        INPUT_TOTAL to totalSongCount,
        INPUT_ESTIMATED_BYTES to estimatedAdditionalBytes,
        INPUT_CREATED_AT to createdAtMillis,
    )

    fun tags(): Set<String> = setOf(
        PLAYLIST_DOWNLOAD_BASE_TAG,
        "$TAG_SERVER$serverId",
        "$TAG_PLAN$planId",
        "$TAG_PLAYLIST${playlistId.encodeTagValue()}",
        "$TAG_TITLE${title.take(MAX_TAG_TITLE_LENGTH).encodeTagValue()}",
        "$TAG_QUALITY${quality.storageKey}",
        "$TAG_TOTAL$totalSongCount",
        "$TAG_ESTIMATED_BYTES$estimatedAdditionalBytes",
        "$TAG_CREATED_AT$createdAtMillis",
    )
}

data class PlaylistDownloadProgress(
    val totalSongCount: Int,
    val processedSongCount: Int = 0,
    val downloadedSongCount: Int = 0,
    val skippedSongCount: Int = 0,
    val failedSongCount: Int = 0,
    val activeSongIds: Set<String> = emptySet(),
    val downloadedBytes: Long = 0L,
) {
    fun toData(): Data = workDataOf(
        INPUT_TOTAL to totalSongCount,
        PROGRESS_PROCESSED to processedSongCount,
        PROGRESS_DOWNLOADED to downloadedSongCount,
        PROGRESS_SKIPPED to skippedSongCount,
        PROGRESS_FAILED to failedSongCount,
        PROGRESS_ACTIVE_IDS to activeSongIds.toTypedArray(),
        PROGRESS_DOWNLOADED_BYTES to downloadedBytes,
    )
}

internal fun Data.toPlaylistDownloadWorkInput(): PlaylistDownloadWorkInput? {
    val serverId = getLong(INPUT_SERVER_ID, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        ?: return null
    val planId = getString(INPUT_PLAN_ID)?.takeIf(String::isNotBlank) ?: return null
    val playlistId = getString(INPUT_PLAYLIST_ID)?.takeIf(String::isNotBlank) ?: return null
    val title = getString(INPUT_TITLE)?.takeIf(String::isNotBlank) ?: return null
    val qualityKey = getString(INPUT_QUALITY) ?: return null
    val quality = StreamQuality.entries.firstOrNull { it.storageKey == qualityKey } ?: return null
    return PlaylistDownloadWorkInput(
        serverId = serverId,
        planId = planId,
        playlistId = playlistId,
        title = title,
        quality = quality,
        totalSongCount = getInt(INPUT_TOTAL, 0).coerceAtLeast(0),
        estimatedAdditionalBytes = getLong(INPUT_ESTIMATED_BYTES, 0L).coerceAtLeast(0L),
        createdAtMillis = getLong(INPUT_CREATED_AT, 0L).coerceAtLeast(0L),
    )
}

internal fun WorkInfo.toPlaylistDownloadTask(): PlaylistDownloadTask? {
    val metadata = tags.toPlaylistDownloadTagMetadata() ?: return null
    val stateData = if (
        state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED
    ) {
        outputData
    } else {
        progress
    }

    return PlaylistDownloadTask(
        workId = id.toString(),
        createdAtMillis = metadata.createdAtMillis,
        serverId = metadata.serverId,
        sourceKey = "server:${metadata.serverId}:playlist:${metadata.playlistId}",
        title = metadata.title,
        quality = metadata.quality,
        totalSongCount = stateData.getInt(INPUT_TOTAL, metadata.totalSongCount).coerceAtLeast(0),
        processedSongCount = stateData.getInt(PROGRESS_PROCESSED, 0).coerceAtLeast(0),
        downloadedSongCount = stateData.getInt(PROGRESS_DOWNLOADED, 0).coerceAtLeast(0),
        skippedSongCount = stateData.getInt(PROGRESS_SKIPPED, 0).coerceAtLeast(0),
        failedSongCount = stateData.getInt(PROGRESS_FAILED, 0).coerceAtLeast(0),
        activeSongIds = stateData.getStringArray(PROGRESS_ACTIVE_IDS).orEmpty().toSet(),
        downloadedBytes = stateData.getLong(PROGRESS_DOWNLOADED_BYTES, 0L).coerceAtLeast(0L),
        estimatedAdditionalBytes = metadata.estimatedAdditionalBytes,
        status = when (state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            -> PlaylistDownloadTaskStatus.QUEUED
            WorkInfo.State.RUNNING -> PlaylistDownloadTaskStatus.RUNNING
            WorkInfo.State.SUCCEEDED -> PlaylistDownloadTaskStatus.COMPLETED
            WorkInfo.State.CANCELLED -> PlaylistDownloadTaskStatus.CANCELLED
            WorkInfo.State.FAILED -> PlaylistDownloadTaskStatus.FAILED
        },
    )
}

internal data class PlaylistDownloadTagMetadata(
    val serverId: Long,
    val planId: String,
    val playlistId: String,
    val title: String,
    val quality: StreamQuality,
    val totalSongCount: Int,
    val estimatedAdditionalBytes: Long,
    val createdAtMillis: Long,
)

internal fun Set<String>.toPlaylistDownloadTagMetadata(): PlaylistDownloadTagMetadata? {
    val quality = valueAfter(TAG_QUALITY)?.let { key ->
        StreamQuality.entries.firstOrNull { it.storageKey == key }
    } ?: return null
    return PlaylistDownloadTagMetadata(
        serverId = valueAfter(TAG_SERVER)?.toLongOrNull() ?: return null,
        planId = valueAfter(TAG_PLAN)?.takeIf(String::isNotBlank) ?: return null,
        playlistId = valueAfter(TAG_PLAYLIST)?.decodeTagValue() ?: return null,
        title = valueAfter(TAG_TITLE)?.decodeTagValue() ?: return null,
        quality = quality,
        totalSongCount = valueAfter(TAG_TOTAL)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        estimatedAdditionalBytes = valueAfter(TAG_ESTIMATED_BYTES)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L,
        createdAtMillis = valueAfter(TAG_CREATED_AT)
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L,
    )
}

private fun Set<String>.valueAfter(prefix: String): String? {
    return firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
}

private fun String.encodeTagValue(): String = URLEncoder.encode(this, "UTF-8")

private fun String.decodeTagValue(): String = URLDecoder.decode(this, "UTF-8")

private const val MAX_TAG_TITLE_LENGTH = 160
