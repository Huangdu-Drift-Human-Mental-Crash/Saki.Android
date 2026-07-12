package org.hdhmc.saki.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointSelectorTest {
    @Test
    fun `successful traffic wins over a concurrent failed probe`() {
        assertEquals(
            true,
            resolveEndpointReachability(
                hasProbeLatency = false,
                succeededDuringProbe = true,
                probeCompleted = true,
                previousReachable = true,
            ),
        )
        assertEquals(
            false,
            resolveEndpointReachability(
                hasProbeLatency = false,
                succeededDuringProbe = false,
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
