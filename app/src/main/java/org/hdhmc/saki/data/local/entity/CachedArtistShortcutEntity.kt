package org.hdhmc.saki.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_artist_shortcuts",
    primaryKeys = ["serverId", "artistId"],
    indices = [
        Index(value = ["serverId", "sortOrder"]),
    ],
)
data class CachedArtistShortcutEntity(
    val serverId: Long,
    val artistId: String,
    val name: String,
    val albumCount: Int?,
    val coverArtId: String?,
    val artistImageUrl: String?,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long,
)
