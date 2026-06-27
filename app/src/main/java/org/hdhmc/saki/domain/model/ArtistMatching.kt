package org.hdhmc.saki.domain.model

internal fun Song.belongsToArtist(artist: Artist): Boolean {
    return belongsToArtist(artist.id, artist.name)
}

internal fun Song.belongsToArtistInAlbum(artist: Artist, album: Album): Boolean {
    if (hasArtistIdentity()) return belongsToArtist(artist)
    return album.isVisibleInArtistDetail(artist) && (!album.hasArtistIdentity() || album.belongsToArtist(artist))
}

internal fun Song.belongsToArtistInAlbum(artist: Artist, album: AlbumSummary): Boolean {
    if (hasArtistIdentity()) return belongsToArtist(artist)
    return album.isVisibleInArtistDetail(artist) && (!album.hasArtistIdentity() || album.belongsToArtist(artist))
}

internal fun Artist.withVisibleDetailAlbums(): Artist {
    val visibleAlbums = visibleDetailAlbums()
    return copy(
        albums = visibleAlbums,
        albumCount = when {
            albumCount != null -> visibleAlbums.size
            visibleAlbums.isNotEmpty() -> visibleAlbums.size
            else -> null
        },
    )
}

internal fun Artist.visibleDetailAlbums(): List<AlbumSummary> {
    return albums.filter { album -> album.isVisibleInArtistDetail(this) }
}

internal fun AlbumSummary.isVisibleInArtistDetail(artist: Artist): Boolean {
    return !hasUnknownAlbumName() || !hasPrimaryArtistMismatch(artist)
}

private fun Album.isVisibleInArtistDetail(artist: Artist): Boolean {
    return !hasUnknownAlbumName() || !hasPrimaryArtistMismatch(artist)
}

private fun Song.belongsToArtist(targetId: String, targetName: String?): Boolean {
    val cleanTargetId = targetId.takeIf(String::isNotBlank)
    if (cleanTargetId != null) {
        if (artistId == cleanTargetId) return true
        if (artists.any { it.id == cleanTargetId }) return true
    }

    val targetNames = targetName.nameParts()
    if (targetNames.isEmpty()) return false
    return artist.nameParts().matchesAny(targetNames) || artists.any { it.name.nameParts().matchesAny(targetNames) }
}

private fun Album.belongsToArtist(artist: Artist): Boolean {
    return belongsToArtist(artist.id, artist.name)
}

private fun AlbumSummary.belongsToArtist(artist: Artist): Boolean {
    return belongsToArtist(artist.id, artist.name)
}

private fun Album.belongsToArtist(targetId: String, targetName: String?): Boolean {
    val cleanTargetId = targetId.takeIf(String::isNotBlank)
    if (cleanTargetId != null) {
        if (artistId == cleanTargetId) return true
        if (artists.any { it.id == cleanTargetId }) return true
    }

    val targetNames = targetName.nameParts()
    if (targetNames.isEmpty()) return false
    return artist.nameParts().matchesAny(targetNames) || artists.any { it.name.nameParts().matchesAny(targetNames) }
}

private fun AlbumSummary.belongsToArtist(targetId: String, targetName: String?): Boolean {
    val cleanTargetId = targetId.takeIf(String::isNotBlank)
    if (cleanTargetId != null) {
        if (artistId == cleanTargetId) return true
        if (artists.any { it.id == cleanTargetId }) return true
    }

    val targetNames = targetName.nameParts()
    if (targetNames.isEmpty()) return false
    return artist.nameParts().matchesAny(targetNames) || artists.any { it.name.nameParts().matchesAny(targetNames) }
}

private fun Album.hasPrimaryArtistMismatch(artist: Artist): Boolean {
    val targetId = artist.id.takeIf(String::isNotBlank)
    if (targetId != null && !artistId.isNullOrBlank()) return artistId != targetId

    val targetNames = artist.name.nameParts()
    if (targetNames.isNotEmpty() && !this.artist.isNullOrBlank()) {
        return !this.artist.nameParts().matchesAny(targetNames)
    }
    return false
}

private fun AlbumSummary.hasPrimaryArtistMismatch(artist: Artist): Boolean {
    val targetId = artist.id.takeIf(String::isNotBlank)
    if (targetId != null && !artistId.isNullOrBlank()) return artistId != targetId

    val targetNames = artist.name.nameParts()
    if (targetNames.isNotEmpty() && !this.artist.isNullOrBlank()) {
        return !this.artist.nameParts().matchesAny(targetNames)
    }
    return false
}

private fun Album.hasUnknownAlbumName(): Boolean {
    return isUnknownAlbumPlaceholder()
}

private fun AlbumSummary.hasUnknownAlbumName(): Boolean {
    return isUnknownAlbumPlaceholder()
}

private fun Song.hasArtistIdentity(): Boolean {
    return !artistId.isNullOrBlank() || !artist.isNullOrBlank() || artists.any { it.id.isNotBlank() || it.name.isNotBlank() }
}

private fun Album.hasArtistIdentity(): Boolean {
    return !artistId.isNullOrBlank() || !artist.isNullOrBlank() || artists.any { it.id.isNotBlank() || it.name.isNotBlank() }
}

private fun AlbumSummary.hasArtistIdentity(): Boolean {
    return !artistId.isNullOrBlank() || !artist.isNullOrBlank() || artists.any { it.id.isNotBlank() || it.name.isNotBlank() }
}

private fun String?.nameParts(): List<String> {
    val normalized = this?.trim()?.takeIf(String::isNotBlank)
    return normalized?.let(::listOf).orEmpty()
}

private fun List<String>.matchesAny(targetNames: List<String>): Boolean {
    return any { candidate -> targetNames.any { target -> candidate.equals(target, ignoreCase = true) } }
}
