package org.hdhmc.saki.domain.model

data class PlaylistDownloadEstimate(
    val serverId: Long,
    val quality: StreamQuality,
    val totalSongCount: Int,
    val alreadyDownloadedSongCount: Int,
    val unknownSizeSongCount: Int,
    val estimatedAdditionalBytes: Long,
    val availableBytes: Long,
) {
    val pendingSongCount: Int
        get() = (totalSongCount - alreadyDownloadedSongCount).coerceAtLeast(0)

    val hasEnoughSpace: Boolean
        get() = estimatedAdditionalBytes <= availableBytes
}

enum class PlaylistDownloadTaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class PlaylistDownloadTask(
    val workId: String,
    val createdAtMillis: Long,
    val serverId: Long,
    val sourceKey: String,
    val title: String,
    val quality: StreamQuality,
    val totalSongCount: Int,
    val processedSongCount: Int = 0,
    val downloadedSongCount: Int = 0,
    val skippedSongCount: Int = 0,
    val failedSongCount: Int = 0,
    val activeSongIds: Set<String> = emptySet(),
    val downloadedBytes: Long = 0L,
    val estimatedAdditionalBytes: Long = 0L,
    val status: PlaylistDownloadTaskStatus = PlaylistDownloadTaskStatus.QUEUED,
) {
    val progress: Float
        get() = if (totalSongCount <= 0) 1f else {
            processedSongCount.toFloat().div(totalSongCount).coerceIn(0f, 1f)
        }

    val isActive: Boolean
        get() = status == PlaylistDownloadTaskStatus.QUEUED ||
            status == PlaylistDownloadTaskStatus.RUNNING
}
