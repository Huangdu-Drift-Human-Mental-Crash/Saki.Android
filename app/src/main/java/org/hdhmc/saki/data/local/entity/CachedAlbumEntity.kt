package org.hdhmc.saki.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "cached_albums", primaryKeys = ["serverId", "albumId", "listType"])
data class CachedAlbumEntity(
    val serverId: Long,
    val albumId: String,
    val listType: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val artistsJson: String?,
    val coverArtId: String?,
    val songCount: Int?,
    val durationSeconds: Int?,
    val year: Int?,
    val genre: String?,
    val created: String?,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long,
)
