package org.hdhmc.saki.data.download

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hdhmc.saki.domain.model.AuthenticatedUrlCandidate
import org.hdhmc.saki.domain.model.ServerEndpoint
import org.hdhmc.saki.data.repository.hasRetryableTransportCause
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CancellableBinaryDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: CancellableBinaryDownloader

    @Before
    fun setUp() {
        server = MockWebServer().also(MockWebServer::start)
        downloader = CancellableBinaryDownloader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful replacement removes backup`() = runBlocking {
        val directory = temporaryFolder.newFolder("success")
        val target = File(directory, "track.bin").apply { writeText("old") }
        server.enqueue(MockResponse().setBody("new"))

        val result = downloader.download(
            candidates = listOf(candidate()),
            destinationDirectory = directory,
            destinationBaseName = "track",
            preferredSuffix = "bin",
        )

        assertEquals(target.absolutePath, result.file.absolutePath)
        assertEquals("new", target.readText())
        assertFalse(File(directory, "track.previous").exists())
        assertFalse(File(directory, "track.tmp").exists())
    }

    @Test
    fun `empty response falls back to the next endpoint`() = runBlocking {
        val directory = temporaryFolder.newFolder("empty-response")
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody("audio"))

        val result = downloader.download(
            candidates = listOf(candidate(), candidate()),
            destinationDirectory = directory,
            destinationBaseName = "track",
            preferredSuffix = "bin",
        )

        assertEquals("audio", result.file.readText())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cancellation restores previous target and removes temporary files`() = runBlocking {
        val directory = temporaryFolder.newFolder("cancel")
        val target = File(directory, "track.bin").apply { writeText("old") }
        server.enqueue(
            MockResponse()
                .setBody("x".repeat(512 * 1024))
                .throttleBody(1_024L, 100L, TimeUnit.MILLISECONDS),
        )

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            downloader.download(
                candidates = listOf(candidate()),
                destinationDirectory = directory,
                destinationBaseName = "track",
                preferredSuffix = "bin",
            )
        }
        assertNotNull(server.takeRequest(2L, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertEquals("old", target.readText())
        assertFalse(File(directory, "track.previous").exists())
        assertFalse(File(directory, "track.tmp").exists())
    }

    @Test
    fun `rollback restores an existing target after a completed swap`() {
        val directory = temporaryFolder.newFolder("swap-existing")
        val temp = File(directory, "track.tmp").apply { writeText("new") }
        val target = File(directory, "track.bin").apply { writeText("old") }
        val backup = File(directory, "track.previous")
        val swap = DownloadFileSwap(temp, target, backup)

        swap.replace()
        swap.rollback()

        assertEquals("old", target.readText())
        assertFalse(temp.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun `rollback removes a newly created target when no previous file existed`() {
        val directory = temporaryFolder.newFolder("swap-new")
        val temp = File(directory, "track.tmp").apply { writeText("new") }
        val target = File(directory, "track.bin")
        val backup = File(directory, "track.previous")
        val swap = DownloadFileSwap(temp, target, backup)

        swap.replace()
        swap.rollback()

        assertFalse(target.exists())
        assertFalse(temp.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun `rollback recovers a backup left by an interrupted replacement`() {
        val directory = temporaryFolder.newFolder("swap-interrupted")
        val temp = File(directory, "track.tmp").apply { writeText("new") }
        val target = File(directory, "track.bin")
        val backup = File(directory, "track.previous").apply { writeText("old") }
        val swap = DownloadFileSwap(temp, target, backup)

        swap.replace()
        swap.rollback()

        assertEquals("old", target.readText())
        assertFalse(temp.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun `retryable HTTP response falls back to the next endpoint`() = runBlocking {
        val directory = temporaryFolder.newFolder("http-fallback")
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("healthy"))

        val result = downloader.download(
            candidates = listOf(candidate("/primary"), candidate("/secondary")),
            destinationDirectory = directory,
            destinationBaseName = "track",
            preferredSuffix = "bin",
        )

        assertEquals("healthy", result.file.readText())
        assertEquals("/primary", server.takeRequest().path)
        assertEquals("/secondary", server.takeRequest().path)
    }

    @Test
    fun `exhausted retryable HTTP responses remain retryable for WorkManager`() = runBlocking {
        val directory = temporaryFolder.newFolder("http-retry")
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(503))

        val failure = runCatching {
            downloader.download(
                candidates = listOf(candidate("/primary"), candidate("/secondary")),
                destinationDirectory = directory,
                destinationBaseName = "track",
                preferredSuffix = "bin",
            )
        }.exceptionOrNull()

        assertTrue(failure is HttpDownloadException)
        assertTrue(failure?.hasRetryableTransportCause() == true)
    }

    @Test
    fun `retryable HTTP status classification is bounded to standard status codes`() {
        assertTrue(408.isRetryableDownloadHttpStatus())
        assertTrue(429.isRetryableDownloadHttpStatus())
        assertTrue(599.isRetryableDownloadHttpStatus())
        assertFalse(600.isRetryableDownloadHttpStatus())
    }

    private fun candidate(path: String = "/file") = AuthenticatedUrlCandidate(
        endpoint = ServerEndpoint(label = "test", baseUrl = server.url("/").toString()),
        url = server.url(path).toString(),
    )
}
