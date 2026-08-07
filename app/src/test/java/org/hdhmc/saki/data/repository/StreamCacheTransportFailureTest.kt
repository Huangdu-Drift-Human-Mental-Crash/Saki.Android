package org.hdhmc.saki.data.repository

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
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
    fun droppedSocketIsRetryable() {
        val failure = IOException("Cache data source failed", SocketException("connection reset"))

        assertTrue(failure.hasRetryableTransportCause())
    }

    @Test
    fun unexpectedEndOfStreamIsRetryable() {
        val failure = IOException("Cache data source failed", EOFException("unexpected end of stream"))

        assertTrue(failure.hasRetryableTransportCause())
    }

    @Test
    fun http2StreamResetIsRetryable() {
        val errorCodeClass = Class.forName("okhttp3.internal.http2.ErrorCode")
        val cancelErrorCode = requireNotNull(errorCodeClass.enumConstants)
            .filterIsInstance<Enum<*>>()
            .first { errorCode -> errorCode.name == "CANCEL" }
        val streamResetClass = Class.forName("okhttp3.internal.http2.StreamResetException")
        val failure = streamResetClass
            .getConstructor(errorCodeClass)
            .newInstance(cancelErrorCode) as IOException

        assertTrue(failure.hasRetryableTransportCause())
    }

    @Test
    fun ordinaryHttpFailureIsNotRetryable() {
        val failure = IOException("HTTP 401")

        assertFalse(failure.hasRetryableTransportCause())
    }
}
