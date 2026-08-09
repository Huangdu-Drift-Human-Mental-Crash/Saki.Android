package org.hdhmc.saki.data.download

import java.io.File
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLConnection
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.hdhmc.saki.domain.model.AuthenticatedUrlCandidate

@Singleton
class CancellableBinaryDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun download(
        candidates: List<AuthenticatedUrlCandidate>,
        destinationDirectory: File,
        destinationBaseName: String,
        preferredSuffix: String?,
    ): DownloadedBinary {
        var lastTransportFailure: IOException? = null

        for (candidate in candidates) {
            try {
                return downloadCandidate(
                    candidate = candidate,
                    destinationDirectory = destinationDirectory,
                    destinationBaseName = destinationBaseName,
                    preferredSuffix = preferredSuffix,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                if (!exception.shouldRetryNextEndpoint()) throw exception
                lastTransportFailure = exception
            }
        }

        throw lastTransportFailure ?: IOException("No endpoint could provide the requested file.")
    }

    private suspend fun downloadCandidate(
        candidate: AuthenticatedUrlCandidate,
        destinationDirectory: File,
        destinationBaseName: String,
        preferredSuffix: String?,
    ): DownloadedBinary {
        val pending = suspendCancellableCoroutine<PendingDownloadedBinary> { continuation ->
            val request = Request.Builder().url(candidate.url).get().build()
            val call = okHttpClient.newCall(request)
            val tempFile = File(destinationDirectory, "$destinationBaseName.tmp")
            val fileSwap = AtomicReference<DownloadFileSwap?>(null)
            tempFile.delete()

            continuation.invokeOnCancellation {
                call.cancel()
                tempFile.delete()
                fileSwap.get()?.rollback()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    tempFile.delete()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                        if (!it.isSuccessful) {
                            throw HttpDownloadException(
                                statusCode = it.code,
                                endpoint = candidate.endpoint.baseUrl,
                            )
                        }
                        val body = it.body ?: throw IOException(
                            "Empty response from ${candidate.endpoint.baseUrl}",
                        )
                        val contentType = body.contentType()?.toString()
                        val suffix = (
                            preferredSuffix
                                ?: body.contentType()?.subtype
                                ?: URLConnection.guessContentTypeFromName(candidate.url)?.substringAfter('/')
                                ?: "bin"
                            ).normalizeSuffix()
                        val targetFile = File(destinationDirectory, "$destinationBaseName.$suffix")
                        val backupFile = File(destinationDirectory, "$destinationBaseName.previous")

                        body.byteStream().use { input ->
                            tempFile.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    if (!continuation.isActive) throw CancellationException()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        if (tempFile.length() <= 0L) {
                            throw EOFException("Empty response from ${candidate.endpoint.baseUrl}")
                        }
                        if (!continuation.isActive) throw CancellationException()
                        val swap = DownloadFileSwap(
                            tempFile = tempFile,
                            targetFile = targetFile,
                            backupFile = backupFile,
                        )
                        fileSwap.set(swap)
                        swap.replace()

                        val result = PendingDownloadedBinary(
                            binary = DownloadedBinary(
                                file = targetFile,
                                contentType = contentType,
                                suffix = suffix,
                            ),
                            fileSwap = swap,
                        )
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(result))
                        } else {
                            swap.rollback()
                        }
                        }
                    } catch (exception: CancellationException) {
                        tempFile.delete()
                        call.cancel()
                        fileSwap.get()?.rollback()
                    } catch (throwable: Exception) {
                        tempFile.delete()
                        fileSwap.get()?.rollback()
                        val exception = throwable as? IOException
                            ?: IOException("Could not download ${candidate.url}.", throwable)
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(exception))
                        }
                    }
                }
            })
        }
        pending.fileSwap.finalizeReplacement()
        return pending.binary
    }
}

private data class PendingDownloadedBinary(
    val binary: DownloadedBinary,
    val fileSwap: DownloadFileSwap,
)

internal class DownloadFileSwap(
    private val tempFile: File,
    private val targetFile: File,
    private val backupFile: File,
) {
    private val lock = Any()
    private var swapCompleted = false
    private var hadPreviousTarget = false

    fun replace() {
        synchronized(lock) {
            recoverInterruptedReplacement()
            hadPreviousTarget = targetFile.exists()
            if (hadPreviousTarget && !targetFile.renameTo(backupFile)) {
                throw IOException("Could not prepare ${targetFile.absolutePath} for replacement.")
            }
            if (!tempFile.renameTo(targetFile)) {
                if (hadPreviousTarget && !backupFile.renameTo(targetFile)) {
                    throw IOException(
                        "Could not finalize ${targetFile.absolutePath}; " +
                            "the previous file remains at ${backupFile.absolutePath}.",
                    )
                }
                throw IOException("Could not finalize ${targetFile.absolutePath}.")
            }
            swapCompleted = true
        }
    }

    fun finalizeReplacement() {
        synchronized(lock) {
            if (!swapCompleted) return
            backupFile.delete()
            swapCompleted = false
        }
    }

    fun rollback() {
        synchronized(lock) {
            if (!swapCompleted) return
            if (targetFile.exists() && !targetFile.delete()) return
            if (hadPreviousTarget && !backupFile.renameTo(targetFile)) return
            swapCompleted = false
        }
    }

    private fun recoverInterruptedReplacement() {
        if (!backupFile.exists()) return
        if (!targetFile.exists()) {
            if (!backupFile.renameTo(targetFile)) {
                throw IOException("Could not restore ${targetFile.absolutePath} from a previous replacement.")
            }
        } else if (!backupFile.delete()) {
            throw IOException("Could not remove stale backup ${backupFile.absolutePath}.")
        }
    }
}

data class DownloadedBinary(
    val file: File,
    val contentType: String?,
    val suffix: String?,
)

internal class HttpDownloadException(
    val statusCode: Int,
    endpoint: String,
) : IOException("HTTP $statusCode from $endpoint")

private fun String.normalizeSuffix(): String {
    return trim()
        .trimStart('.')
        .lowercase()
        .ifBlank { "bin" }
}

private fun IOException.shouldRetryNextEndpoint(): Boolean {
    return this is UnknownHostException ||
        this is ConnectException ||
        this is SocketTimeoutException ||
        this is NoRouteToHostException ||
        this is SocketException ||
        this is EOFException ||
        javaClass.name == OKHTTP_STREAM_RESET_EXCEPTION_CLASS ||
        (this is HttpDownloadException && statusCode.isRetryableDownloadHttpStatus())
}

internal fun Int.isRetryableDownloadHttpStatus(): Boolean {
    return this == 408 || this == 429 || this in 500..599
}

private const val OKHTTP_STREAM_RESET_EXCEPTION_CLASS =
    "okhttp3.internal.http2.StreamResetException"
