package org.hdhmc.saki.playback

import java.util.Locale
import org.hdhmc.saki.domain.model.StreamQuality

private const val STREAM_CACHE_KEY_PREFIX = "saki.stream.v1"
private const val ENCODED_STREAM_CACHE_KEY_PREFIX = "saki.stream.v2"
private const val VARIANT_STREAM_CACHE_KEY_PREFIX = "saki.stream.v3"
private const val FIELD_DELIMITER = '|'
private const val ENCODED_ESCAPE = '%'
internal const val STREAM_CACHE_EOF_LENGTH_METADATA_KEY = "custom_saki_stream_eof_length"

data class StreamCacheResourceKey(
    val serverId: Long,
    val songId: String,
    val qualityKey: String,
    val variantKey: String? = null,
)

fun buildStreamCacheKey(
    serverId: Long,
    songId: String,
    quality: StreamQuality,
): String {
    if (songId.indexOf(FIELD_DELIMITER) < 0) {
        return listOf(
            STREAM_CACHE_KEY_PREFIX,
            serverId.toString(),
            songId,
            quality.storageKey,
        ).joinToString(FIELD_DELIMITER.toString())
    }

    return listOf(
        ENCODED_STREAM_CACHE_KEY_PREFIX,
        serverId.toString(),
        encodeCacheKeyField(songId),
        quality.storageKey,
    ).joinToString(FIELD_DELIMITER.toString())
}

fun buildForcedTranscodeStreamCacheKey(
    serverId: Long,
    songId: String,
    quality: StreamQuality,
    format: String,
): String = listOf(
    VARIANT_STREAM_CACHE_KEY_PREFIX,
    serverId.toString(),
    encodeCacheKeyField(songId),
    quality.storageKey,
    encodeCacheKeyField("forced-${format.lowercase(Locale.ROOT)}"),
).joinToString(FIELD_DELIMITER.toString())

fun parseStreamCacheKey(key: String): StreamCacheResourceKey? {
    val parts = key.split(FIELD_DELIMITER)
    if (parts.size !in 4..5) {
        return null
    }

    val songId = when (parts.first()) {
        STREAM_CACHE_KEY_PREFIX -> parts[2]
        ENCODED_STREAM_CACHE_KEY_PREFIX -> decodeCacheKeyField(parts[2]) ?: return null
        VARIANT_STREAM_CACHE_KEY_PREFIX -> decodeCacheKeyField(parts[2]) ?: return null
        else -> return null
    }.ifBlank { return null }
    if (parts.first() == VARIANT_STREAM_CACHE_KEY_PREFIX && parts.size != 5) return null
    if (parts.first() != VARIANT_STREAM_CACHE_KEY_PREFIX && parts.size != 4) return null

    return StreamCacheResourceKey(
        serverId = parts[1].toLongOrNull() ?: return null,
        songId = songId,
        qualityKey = parts[3].ifBlank { return null },
        variantKey = parts.getOrNull(4)
            ?.let(::decodeCacheKeyField)
            ?.ifBlank { return null },
    )
}

private fun encodeCacheKeyField(value: String): String {
    return buildString(value.length) {
        value.forEach { char ->
            when (char) {
                FIELD_DELIMITER -> append("%7C")
                ENCODED_ESCAPE -> append("%25")
                else -> append(char)
            }
        }
    }
}

private fun decodeCacheKeyField(value: String): String? {
    return buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != ENCODED_ESCAPE) {
                append(char)
                index++
                continue
            }
            if (index + 2 >= value.length) return null
            when (value.substring(index + 1, index + 3).uppercase()) {
                "7C" -> append(FIELD_DELIMITER)
                "25" -> append(ENCODED_ESCAPE)
                else -> return null
            }
            index += 3
        }
    }
}
