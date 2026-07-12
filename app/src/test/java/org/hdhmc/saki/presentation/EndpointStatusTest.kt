package org.hdhmc.saki.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointStatusTest {
    @Test
    fun `offline degraded remains active while a background recovery probe runs`() {
        val status = EndpointStatus(
            isProbing = true,
            isProbeComplete = true,
            probeResults = listOf(
                EndpointProbeInfo(
                    id = 1L,
                    label = "Primary",
                    baseUrl = "https://music.example.test",
                    latencyMs = null,
                    reachable = false,
                ),
            ),
        )

        assertTrue(status.isOfflineDegraded)
    }

    @Test
    fun `reachable endpoint clears offline degraded during recovery`() {
        val status = EndpointStatus(
            activeEndpointId = 1L,
            isProbing = true,
            isProbeComplete = true,
            probeResults = listOf(
                EndpointProbeInfo(
                    id = 1L,
                    label = "Primary",
                    baseUrl = "https://music.example.test",
                    latencyMs = 12L,
                    reachable = true,
                ),
            ),
        )

        assertFalse(status.isOfflineDegraded)
    }
}
