package org.hdhmc.saki.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "cached_artists", primaryKeys = ["serverId", "artistId"])
data class CachedArtistEntity(
    val serverId: Long,
    val artistId: String,
    val name: String,
    val sectionName: String,
    val albumCount: Int?,
    val coverArtId: String?,
    val artistImageUrl: String?,
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long,
)
