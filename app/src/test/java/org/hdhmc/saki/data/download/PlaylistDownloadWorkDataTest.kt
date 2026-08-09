package org.hdhmc.saki.data.download

import org.hdhmc.saki.domain.model.PlaylistDownloadTask
import org.hdhmc.saki.domain.model.PlaylistDownloadTaskStatus
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDownloadWorkDataTest {
    @Test
    fun `input data captures quality and identity`() {
        val input = PlaylistDownloadWorkInput(
            serverId = 7L,
            planId = "00000000-0000-0000-0000-000000000007",
            playlistId = "mix:2026/夏",
            title = "Drive: 100% / 夏",
            quality = StreamQuality.KBPS_192,
            totalSongCount = 42,
            estimatedAdditionalBytes = 123_456L,
            createdAtMillis = 999L,
        )

        assertEquals(input, input.toData().toPlaylistDownloadWorkInput())
        val metadata = input.tags().toPlaylistDownloadTagMetadata()
        assertEquals(input.serverId, metadata?.serverId)
        assertEquals(input.planId, metadata?.planId)
        assertEquals(input.playlistId, metadata?.playlistId)
        assertEquals(input.title, metadata?.title)
        assertEquals(input.quality, metadata?.quality)
        assertEquals(input.createdAtMillis, metadata?.createdAtMillis)
    }

    @Test
    fun `latest task selection is independent of WorkManager list order`() {
        val old = task(workId = "z", createdAtMillis = 100L)
        val latest = task(workId = "a", createdAtMillis = 200L)

        assertEquals(latest, selectLatestPlaylistDownloadTask(listOf(latest, old)))
        assertEquals(latest, selectLatestPlaylistDownloadTask(listOf(old, latest)))
    }

    @Test
    fun `work id deterministically breaks equal timestamp ties`() {
        val first = task(workId = "a", createdAtMillis = 100L)
        val second = task(workId = "b", createdAtMillis = 100L)

        assertEquals(second, selectLatestPlaylistDownloadTask(listOf(second, first)))
    }

    @Test
    fun `task state keeps each playlist terminal result and exposes the single active task`() {
        val oldA = task(workId = "a-old", createdAtMillis = 100L, sourceKey = "server:1:playlist:a")
        val latestA = task(workId = "a-new", createdAtMillis = 200L, sourceKey = "server:1:playlist:a")
        val activeB = task(
            workId = "b",
            createdAtMillis = 300L,
            sourceKey = "server:1:playlist:b",
            status = PlaylistDownloadTaskStatus.RUNNING,
        )

        val state = buildPlaylistDownloadTaskState(listOf(activeB, latestA, oldA))

        assertEquals(latestA, state.tasksBySourceKey[latestA.sourceKey])
        assertEquals(activeB, state.tasksBySourceKey[activeB.sourceKey])
        assertEquals(activeB, state.activeTask)
    }
}

private fun task(
    workId: String,
    createdAtMillis: Long,
    sourceKey: String = "server:1:playlist:p",
    status: PlaylistDownloadTaskStatus = PlaylistDownloadTaskStatus.COMPLETED,
) = PlaylistDownloadTask(
    workId = workId,
    createdAtMillis = createdAtMillis,
    serverId = 1L,
    sourceKey = sourceKey,
    title = "Playlist",
    quality = StreamQuality.ORIGINAL,
    totalSongCount = 1,
    status = status,
)
