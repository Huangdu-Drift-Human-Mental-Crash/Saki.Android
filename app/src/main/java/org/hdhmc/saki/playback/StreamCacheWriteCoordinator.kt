package org.hdhmc.saki.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class StreamCacheWriteCoordinator @Inject constructor() {
    private val writerMutex = Mutex()

    suspend fun <T> withWriter(block: suspend () -> T): T =
        writerMutex.withLock { block() }
}
