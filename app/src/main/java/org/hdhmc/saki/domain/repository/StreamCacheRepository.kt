package org.hdhmc.saki.domain.repository

import org.hdhmc.saki.domain.model.StreamCacheProgress
import org.hdhmc.saki.domain.model.StreamCacheSummary
import org.hdhmc.saki.domain.model.StreamQuality
import kotlinx.coroutines.flow.Flow

interface StreamCacheRepository {
    fun observeCacheVersion(): Flow<Long>

    fun buildCacheKey(
        serverId: Long,
        songId: String,
        quality: StreamQuality,
    ): String

    suspend fun getStreamCacheSummary(
        serverId: Long? = null,
        quality: StreamQuality? = null,
    ): StreamCacheSummary

    /**
     * Find the best cached quality for a song. Returns the requested quality if cached,
     * or a higher quality if available, or null if not cached at all.
     */
    fun findCachedQualityKey(serverId: Long, songId: String, preferredQuality: StreamQuality): String?

    fun getStreamCacheProgress(serverId: Long, songId: String, quality: StreamQuality): StreamCacheProgress?

    suspend fun clearStreamCache(serverId: Long? = null): Int
}
