package org.hdhmc.saki.playback

import java.nio.ByteBuffer
import java.security.MessageDigest

internal data class ShuffleQueueItemIdentity(
    val serverId: Long,
    val songId: String,
)

internal data class ShuffleQueueSnapshot(
    val itemCount: Int,
    val identity: String,
    val generation: Long?,
)

internal data class ShuffleQueueTarget(
    val itemCount: Int,
    val identity: String,
    val generation: Long?,
) {
    fun matches(snapshot: ShuffleQueueSnapshot): Boolean {
        return itemCount == snapshot.itemCount &&
            identity == snapshot.identity &&
            generation == snapshot.generation
    }
}

/** A compact, order-sensitive identity for the logical songs in a playback queue. */
internal fun shuffleQueueIdentity(items: List<ShuffleQueueItemIdentity>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(items.size.toBytes())
    items.forEach { item ->
        digest.update(item.serverId.toBytes())
        val songId = item.songId.toByteArray(Charsets.UTF_8)
        digest.update(songId.size.toBytes())
        digest.update(songId)
    }
    return digest.digest().joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

private fun Int.toBytes(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

private fun Long.toBytes(): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array()
