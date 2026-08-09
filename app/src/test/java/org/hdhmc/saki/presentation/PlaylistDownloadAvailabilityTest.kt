package org.hdhmc.saki.presentation

import org.hdhmc.saki.domain.model.PlaylistDownloadTask
import org.hdhmc.saki.domain.model.PlaylistDownloadTaskStatus
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDownloadAvailabilityTest {
    @Test
    fun `active song ids are exposed only on the task server`() {
        val task = task(status = PlaylistDownloadTaskStatus.RUNNING)

        assertEquals(setOf("song-1", "song-2"), task.activeSongIdsForServer(7L))
        assertTrue(task.activeSongIdsForServer(8L).isEmpty())
        assertTrue(task.activeSongIdsForServer(null).isEmpty())
    }

    @Test
    fun `terminal tasks do not mark songs as downloading`() {
        val task = task(status = PlaylistDownloadTaskStatus.FAILED)

        assertTrue(task.activeSongIdsForServer(7L).isEmpty())
    }
}

private fun task(status: PlaylistDownloadTaskStatus) = PlaylistDownloadTask(
    workId = "work",
    createdAtMillis = 1L,
    serverId = 7L,
    sourceKey = "server:7:playlist:p",
    title = "Playlist",
    quality = StreamQuality.ORIGINAL,
    totalSongCount = 2,
    activeSongIds = setOf("song-1", "song-2"),
    status = status,
)
