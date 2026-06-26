package org.hdhmc.saki.presentation.library

/**
 * Browse-internal navigation entry. The route stack is the source of truth for
 * which Browse detail page is shown, so each detail page can be rendered as an
 * opaque page over its real previous page.
 */
sealed interface BrowseNavRoute {
    data object Root : BrowseNavRoute
    data class AlbumDetail(val albumId: String) : BrowseNavRoute
    data class ArtistDetail(val artistId: String) : BrowseNavRoute
    data class PlaylistDetail(val playlistId: String) : BrowseNavRoute
}
