package org.hdhmc.saki.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointSelectorTest {
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
