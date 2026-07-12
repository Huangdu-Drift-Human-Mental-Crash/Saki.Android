package org.hdhmc.saki.data.remote

import android.content.ContextWrapper
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hdhmc.saki.domain.model.ServerConfig
import org.hdhmc.saki.domain.model.ServerEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSelectorTest {
    @Test
    fun `concurrent probes replace active probe without stranding callers`() = runBlocking {
        val mockServer = MockWebServer()
        mockServer.start()
        val endpoint = ServerEndpoint(
            id = 7L,
            label = "primary",
            baseUrl = mockServer.url("/").toString().trimEnd('/'),
        )
        val server = ServerConfig(
            id = 3L,
            name = "test",
            username = "user",
            password = "password",
            endpoints = listOf(endpoint),
        )
        val selector = EndpointSelector(
            context = ContextWrapper(null),
            okHttpClient = OkHttpClient(),
            ioDispatcher = Dispatchers.IO,
        )
        val probeCount = 32
        repeat(probeCount) {
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeadersDelay(200L, TimeUnit.MILLISECONDS),
            )
        }

        try {
            val startBarrier = CyclicBarrier(probeCount)
            val probes = List(probeCount) {
                async(Dispatchers.IO) {
                    startBarrier.await(5L, TimeUnit.SECONDS)
                    selector.probe(server.id, server)
                }
            }

            val results = withTimeout(10_000L) { probes.awaitAll() }

            assertEquals(probeCount, results.size)
            assertTrue(results.any { result -> result?.id == endpoint.id })
        } finally {
            selector.unregisterServer(server.id)
            mockServer.shutdown()
        }
    }

    @Test
    fun `newer invalidation wins over success and older probe completion`() {
        val probeStartVersion = 0L
        val success = EndpointReachabilityEvent(version = 1L, reachable = true)
        val invalidation = EndpointReachabilityEvent(version = 2L, reachable = false)

        assertEquals(
            true,
            resolveEndpointReachability(
                hasProbeLatency = false,
                latestEvent = success,
                probeStartEventVersion = probeStartVersion,
                probeCompleted = false,
                previousReachable = false,
            ),
        )
        assertEquals(
            false,
            resolveEndpointReachability(
                hasProbeLatency = true,
                latestEvent = invalidation,
                probeStartEventVersion = probeStartVersion,
                probeCompleted = true,
                previousReachable = true,
            ),
        )
    }

    @Test
    fun `invalidation after final publication clears the selected endpoint`() {
        val endpoint = ServerEndpoint(
            id = 7L,
            label = "primary",
            baseUrl = "https://example.test",
        )
        val publishedReachable = listOf(
            EndpointSelector.EndpointProbeResult(endpoint, latencyMs = 10L, reachable = true),
        )
        val invalidated = listOf(
            EndpointSelector.EndpointProbeResult(endpoint, latencyMs = null, reachable = false),
        )

        assertEquals(7L, bestReachableEndpointId(publishedReachable))
        assertNull(bestReachableEndpointId(invalidated))
    }

    @Test
    fun `probe ignores endpoint events older than its start`() {
        assertEquals(
            false,
            resolveEndpointReachability(
                hasProbeLatency = false,
                latestEvent = EndpointReachabilityEvent(version = 4L, reachable = true),
                probeStartEventVersion = 4L,
                probeCompleted = true,
                previousReachable = true,
            ),
        )
    }

    @Test
    fun `offline recovery delay backs off and remains capped`() {
        assertEquals(5_000L, endpointOfflineRecoveryDelayMs(0))
        assertEquals(15_000L, endpointOfflineRecoveryDelayMs(1))
        assertEquals(30_000L, endpointOfflineRecoveryDelayMs(2))
        assertEquals(60_000L, endpointOfflineRecoveryDelayMs(3))
        assertEquals(60_000L, endpointOfflineRecoveryDelayMs(100))
    }
}
