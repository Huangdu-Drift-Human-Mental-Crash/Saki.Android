package org.hdhmc.saki.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_song_artists",
    primaryKeys = ["serverId", "songId", "artistId"],
    indices = [
        Index(value = ["serverId", "artistId", "sortOrder"]),
        Index(value = ["serverId", "songId"]),
        Index(value = ["serverId", "name"]),
    ],
)
data class CachedSongArtistEntity(
    val serverId: Long,
    val songId: String,
    val artistId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val sortOrder: Int,
)

data class CachedSongArtistSummary(
    val artistId: String,
    val name: String,
    val albumCount: Int,
    val coverArtId: String?,
)

/** A (artist, album) association derived from the per-song artist relationships. */
data class ArtistAlbumRef(
    val artistId: String,
    val albumId: String,
)
