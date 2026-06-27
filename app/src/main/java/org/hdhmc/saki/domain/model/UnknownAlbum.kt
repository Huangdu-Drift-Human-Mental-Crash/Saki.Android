package org.hdhmc.saki.domain.model

internal fun AlbumSummary.isUnknownAlbumPlaceholder(): Boolean {
    return name.isUnknownAlbumPlaceholderName()
}

internal fun Album.isUnknownAlbumPlaceholder(): Boolean {
    return name.isUnknownAlbumPlaceholderName()
}

internal fun String.isUnknownAlbumPlaceholderName(): Boolean {
    val normalized = trim()
        .removePrefix("[")
        .removeSuffix("]")
        .trim()
    return normalized.equals("Unknown Album", ignoreCase = true)
}

internal fun List<AlbumSummary>.withoutUnknownAlbumPlaceholders(): List<AlbumSummary> {
    return filterNot(AlbumSummary::isUnknownAlbumPlaceholder)
}
