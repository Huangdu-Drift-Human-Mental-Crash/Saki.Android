package org.hdhmc.saki.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import org.hdhmc.saki.MainActivity
import org.hdhmc.saki.R

class PlaylistDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val input = inputData.toPlaylistDownloadWorkInput() ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            PlaylistDownloadWorkerEntryPoint::class.java,
        )
        val runner = dependencies.runner()
        val planStore = dependencies.planStore()
        val initialProgress = PlaylistDownloadProgress(totalSongCount = input.totalSongCount)

        setForeground(createForegroundInfo(input, initialProgress))
        return try {
            val plan = planStore.read(input.planId)
            if (
                plan == null ||
                plan.serverId != input.serverId ||
                plan.playlistId != input.playlistId ||
                plan.title != input.title ||
                plan.songs.size != input.totalSongCount
            ) {
                val unavailable = PlaylistDownloadProgress(
                    totalSongCount = input.totalSongCount,
                    processedSongCount = input.totalSongCount,
                    failedSongCount = input.totalSongCount,
                )
                planStore.delete(input.planId)
                return Result.failure(unavailable.toData())
            }

            val outcome = runner.run(input, plan.songs) { progress ->
                setProgress(progress.toData())
                setForeground(createForegroundInfo(input, progress))
            }
            val output = outcome.progress.toData()
            when {
                outcome.retryableFailureCount > 0 && runAttemptCount < MAX_RETRY_ATTEMPTS -> {
                    Result.retry()
                }
                outcome.progress.failedSongCount == 0 -> {
                    planStore.delete(input.planId)
                    Result.success(output)
                }
                else -> {
                    planStore.delete(input.planId)
                    Result.failure(output)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        }
    }

    private fun createForegroundInfo(
        input: PlaylistDownloadWorkInput,
        progress: PlaylistDownloadProgress,
    ): ForegroundInfo {
        ensureNotificationChannel()
        val cancelIntent = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(applicationContext.getString(R.string.playlist_download_notification_title, input.title))
            .setContentText(
                applicationContext.getString(
                    R.string.playlist_download_notification_progress,
                    progress.processedSongCount,
                    progress.totalSongCount,
                ),
            )
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(
                progress.totalSongCount.coerceAtLeast(1),
                progress.processedSongCount.coerceAtMost(progress.totalSongCount),
                progress.totalSongCount <= 0,
            )
            .addAction(
                R.drawable.ic_notification_download,
                applicationContext.getString(R.string.common_cancel),
                cancelIntent,
            )
            .build()
        val notificationId = NOTIFICATION_ID_BASE + Math.floorMod(id.hashCode(), NOTIFICATION_ID_RANGE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.playlist_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaylistDownloadWorkerEntryPoint {
    fun runner(): PlaylistDownloadRunner
    fun planStore(): PlaylistDownloadPlanStore
}

private const val NOTIFICATION_CHANNEL_ID = "playlist_downloads"
private const val NOTIFICATION_ID_BASE = 42_000
private const val NOTIFICATION_ID_RANGE = 1_000
private const val MAX_RETRY_ATTEMPTS = 2
