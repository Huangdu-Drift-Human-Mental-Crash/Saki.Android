package org.hdhmc.saki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownAlbumTest {
    @Test
    fun unknownAlbumPlaceholderMatchesCommonServerAndFallbackNames() {
        assertTrue("Unknown Album".isUnknownAlbumPlaceholderName())
        assertTrue("[Unknown Album]".isUnknownAlbumPlaceholderName())
        assertTrue(" [unknown album] ".isUnknownAlbumPlaceholderName())
    }

    @Test
    fun unknownAlbumPlaceholderDoesNotMatchRealAlbumNamesContainingWords() {
        assertFalse("Unknown Album Sessions".isUnknownAlbumPlaceholderName())
        assertFalse("The Unknown Album".isUnknownAlbumPlaceholderName())
        assertFalse("Unknown".isUnknownAlbumPlaceholderName())
    }

    @Test
    fun filtersUnknownAlbumPlaceholdersWithoutRemovingNormalAlbums() {
        val albums = listOf(
            album(id = "unknown-1", name = "[Unknown Album]"),
            album(id = "album-1", name = "Real Album"),
            album(id = "album-2", name = "Unknown Album Sessions"),
            album(id = "unknown-2", name = "Unknown Album"),
        )

        assertEquals(
            listOf("album-1", "album-2"),
            albums.withoutUnknownAlbumPlaceholders().map(AlbumSummary::id),
        )
    }

    private fun album(id: String, name: String): AlbumSummary = AlbumSummary(
        id = id,
        name = name,
        artist = null,
        artistId = null,
        coverArtId = null,
        songCount = null,
        durationSeconds = null,
        year = null,
        genre = null,
        created = null,
    )
}
