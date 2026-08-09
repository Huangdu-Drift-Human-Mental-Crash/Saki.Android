package org.hdhmc.saki.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.hdhmc.saki.domain.model.StreamQuality
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDownloadWorkRestorationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(TEST_WORK_NAME).result.get(5L, TimeUnit.SECONDS)
    }

    @Test
    fun queuedTaskMetadataCanBeReadAfterWorkManagerIsReacquired() {
        val input = PlaylistDownloadWorkInput(
            serverId = 12L,
            planId = "00000000-0000-0000-0000-000000000012",
            playlistId = "mix:夏",
            title = "Trip: 100% / 夏",
            quality = StreamQuality.KBPS_256,
            totalSongCount = 8,
            estimatedAdditionalBytes = 80_000L,
            createdAtMillis = 123_456L,
        )
        val builder = OneTimeWorkRequestBuilder<PlaylistDownloadWorker>()
            .setInputData(input.toData())
            .setInitialDelay(1L, TimeUnit.DAYS)
        input.tags().forEach(builder::addTag)
        workManager.enqueueUniqueWork(
            TEST_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            builder.build(),
        ).result.get(5L, TimeUnit.SECONDS)

        val reacquired = WorkManager.getInstance(context)
        val info = reacquired.getWorkInfosForUniqueWork(TEST_WORK_NAME)
            .get(5L, TimeUnit.SECONDS)
            .single()
        val task = info.toPlaylistDownloadTask()

        assertNotNull(task)
        assertEquals("server:12:playlist:mix:夏", task?.sourceKey)
        assertEquals("Trip: 100% / 夏", task?.title)
        assertEquals(StreamQuality.KBPS_256, task?.quality)
        assertEquals(8, task?.totalSongCount)
    }
}

private const val TEST_WORK_NAME = "playlist-download-restoration-test"
