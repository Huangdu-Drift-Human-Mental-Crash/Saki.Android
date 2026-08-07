package org.hdhmc.saki.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

internal fun isResourceEof(requestLength: Long, bytesRead: Long): Boolean =
    requestLength == C.LENGTH_UNSET.toLong() || bytesRead < requestLength

internal class StreamCacheEofTrackingDataSource(
    private val upstream: DataSource,
    private val onEof: (DataSpec, Long) -> Unit,
) : DataSource {
    private var openedDataSpec: DataSpec? = null
    private var bytesRead = 0L
    private var reportedEof = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        openedDataSpec = dataSpec
        bytesRead = 0L
        reportedEof = false
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = upstream.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) {
            val dataSpec = openedDataSpec
            if (!reportedEof && dataSpec != null && isResourceEof(dataSpec.length, bytesRead)) {
                reportedEof = true
                onEof(dataSpec, dataSpec.position + bytesRead)
            }
        } else if (read > 0) {
            bytesRead += read
        }
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            upstream.close()
        } finally {
            openedDataSpec = null
        }
    }
}
