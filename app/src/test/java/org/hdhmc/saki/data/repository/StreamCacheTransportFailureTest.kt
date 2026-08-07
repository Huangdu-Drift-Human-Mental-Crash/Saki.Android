package org.hdhmc.saki.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCacheTransportFailureTest {
    @Test
    fun wrappedDnsFailureIsRetryable() {
        val failure = IOException(
            "Cache data source failed",
            IOException("HTTP data source failed", UnknownHostException("offline.example")),
        )

        assertTrue(failure.hasRetryableTransportCause())
    }

    @Test
    fun wrappedTimeoutIsRetryable() {
        val failure = IOException("HTTP data source failed", SocketTimeoutException("timed out"))

        assertTrue(failure.hasRetryableTransportCause())
    }

    @Test
    fun ordinaryHttpFailureIsNotRetryable() {
        val failure = IOException("HTTP 401")

        assertFalse(failure.hasRetryableTransportCause())
    }
}
