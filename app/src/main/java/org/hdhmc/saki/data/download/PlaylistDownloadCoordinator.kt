package org.hdhmc.saki.data.download

import android.content.Context
import android.os.StatFs
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistDownloadEstimate
import org.hdhmc.saki.domain.model.PlaylistDownloadTask
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.repository.CachedSongRepository
import org.hdhmc.saki.domain.repository.PlaybackPreferencesRepository

@Singleton
class PlaylistDownloadCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val cachedSongRepository: CachedSongRepository,
    private val planStore: PlaylistDownloadPlanStore,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val workManager = WorkManager.getInstance(appContext)
    private val operationMutex = Mutex()

    fun observeTasks(): Flow<PlaylistDownloadTaskState> {
        return workManager.getWorkInfosForUniqueWorkFlow(PLAYLIST_DOWNLOAD_WORK_NAME)
            .map { workInfos ->
                val referencedPlanIds = workInfos.mapNotNull { workInfo ->
                    workInfo.tags.toPlaylistDownloadTagMetadata()?.planId
                }.toSet()
                workInfos.asSequence()
                    .filter { workInfo -> workInfo.state.isFinished }
                    .mapNotNull { workInfo ->
                        workInfo.tags.toPlaylistDownloadTagMetadata()?.planId
                    }
                    .forEach { planId -> planStore.delete(planId) }
                planStore.deleteUnreferenced(referencedPlanIds)
                selectPlaylistDownloadTaskState(workInfos)
            }
    }

    suspend fun estimate(
        serverId: Long,
        songs: List<Song>,
    ): PlaylistDownloadEstimate {
        val quality = playbackPreferencesRepository.getPreferences().downloadQuality
        val uniqueSongIds = songs.distinctBy(Song::id).map(Song::id)
        val downloaded = cachedSongRepository.getPlayableCachedSongs(
            serverId = serverId,
            songIds = uniqueSongIds,
            preferredQuality = quality,
        )
        return calculatePlaylistDownloadEstimate(
            serverId = serverId,
            songs = songs,
            quality = quality,
            qualifyingDownloadedSongIds = downloaded.keys,
            availableBytes = StatFs(appContext.filesDir.absolutePath).availableBytes,
        )
    }

    suspend fun enqueue(
        serverId: Long,
        playlist: Playlist,
        estimate: PlaylistDownloadEstimate,
    ): Unit = operationMutex.withLock {
        check(currentActiveTask() == null) { "A playlist download is already active." }
        require(serverId == estimate.serverId) {
            "The download estimate belongs to a different server."
        }
        require(playlist.songs.distinctBy(Song::id).size == estimate.totalSongCount) {
            "The playlist changed after its download estimate was created."
        }
        val planId = planStore.createPlanId()
        val input = PlaylistDownloadWorkInput(
            serverId = serverId,
            planId = planId,
            playlistId = playlist.id,
            title = playlist.name,
            quality = estimate.quality,
            totalSongCount = estimate.totalSongCount,
            estimatedAdditionalBytes = estimate.estimatedAdditionalBytes,
            createdAtMillis = System.currentTimeMillis(),
        )
        val requestBuilder = OneTimeWorkRequestBuilder<PlaylistDownloadWorker>()
            .setInputData(input.toData())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MINIMUM_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .keepResultsForAtLeast(
                FINISHED_WORK_RETENTION_DAYS,
                TimeUnit.DAYS,
            )
        input.tags().forEach(requestBuilder::addTag)
        withContext(NonCancellable) {
            try {
                planStore.write(planId, serverId, playlist)
                withContext(ioDispatcher) {
                    workManager.enqueueUniqueWork(
                        PLAYLIST_DOWNLOAD_WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        requestBuilder.build(),
                    ).result.get()
                }
            } catch (exception: Exception) {
                planStore.delete(planId)
                throw exception
            }
        }
        Unit
    }

    suspend fun cancel() {
        cancelActiveForServer(serverId = null)
    }

    suspend fun cancelActiveForServer(serverId: Long?): Boolean = operationMutex.withLock {
        val activeTask = currentActiveTask()
        if (activeTask == null || (serverId != null && activeTask.serverId != serverId)) {
            return@withLock false
        }
        withContext(ioDispatcher) {
            workManager.cancelUniqueWork(PLAYLIST_DOWNLOAD_WORK_NAME).result.get()
        }
        planStore.deleteAll()
        true
    }

    private suspend fun currentActiveTask(): PlaylistDownloadTask? = withContext(ioDispatcher) {
        val workInfos = workManager.getWorkInfosForUniqueWork(PLAYLIST_DOWNLOAD_WORK_NAME).get()
        selectPlaylistDownloadTaskState(workInfos).activeTask
    }
}

private const val MINIMUM_BACKOFF_SECONDS = 30L
private const val FINISHED_WORK_RETENTION_DAYS = 7L

data class PlaylistDownloadTaskState(
    val tasksBySourceKey: Map<String, PlaylistDownloadTask> = emptyMap(),
    val activeTask: PlaylistDownloadTask? = null,
)

internal fun selectPlaylistDownloadTaskState(
    workInfos: List<androidx.work.WorkInfo>,
): PlaylistDownloadTaskState {
    return buildPlaylistDownloadTaskState(workInfos.mapNotNull { it.toPlaylistDownloadTask() })
}

internal fun buildPlaylistDownloadTaskState(
    tasks: List<PlaylistDownloadTask>,
): PlaylistDownloadTaskState {
    return PlaylistDownloadTaskState(
        tasksBySourceKey = tasks.groupBy(PlaylistDownloadTask::sourceKey)
            .mapValues { (_, sourceTasks) ->
                checkNotNull(selectLatestPlaylistDownloadTask(sourceTasks))
            },
        activeTask = selectLatestPlaylistDownloadTask(tasks.filter(PlaylistDownloadTask::isActive)),
    )
}

internal fun selectLatestPlaylistDownloadTask(
    tasks: List<PlaylistDownloadTask>,
): PlaylistDownloadTask? = tasks.maxWithOrNull(
    compareBy<PlaylistDownloadTask>(PlaylistDownloadTask::createdAtMillis)
        .thenBy(PlaylistDownloadTask::workId),
)
