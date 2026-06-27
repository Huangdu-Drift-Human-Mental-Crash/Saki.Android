package org.hdhmc.saki.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMatchingTest {
    private val yanhe = Artist(
        id = "yanhe",
        name = "言和",
        coverArtId = null,
        artistImageUrl = null,
        albumCount = null,
        albums = emptyList(),
    )

    private val luotianyi = ArtistRef(id = "luotianyi", name = "洛天依")
    private val yanheRef = ArtistRef(id = "yanhe", name = "言和")

    @Test
    fun `explicit song artist wins over shared unknown album artist list`() {
        val sharedUnknownAlbum = album(
            artistId = "luotianyi",
            artist = "洛天依",
            artists = listOf(luotianyi, yanheRef),
        )
        val song = song(
            artistId = "luotianyi",
            artist = "洛天依",
            artists = listOf(luotianyi),
        )

        assertFalse(song.belongsToArtistInAlbum(yanhe, sharedUnknownAlbum))
    }

    @Test
    fun `multi artist song is included when target artist is in song artists`() {
        val sharedUnknownAlbum = album(
            artistId = "luotianyi",
            artist = "洛天依",
            artists = listOf(luotianyi, yanheRef),
        )
        val song = song(
            artistId = "luotianyi",
            artist = "洛天依 • 言和",
            artists = listOf(luotianyi, yanheRef),
        )

        assertTrue(song.belongsToArtistInAlbum(yanhe, sharedUnknownAlbum))
    }

    @Test
    fun `song without artist metadata falls back to album artist`() {
        val yanheAlbum = album(
            artistId = "yanhe",
            artist = "言和",
            artists = listOf(yanheRef),
        )
        val song = song(
            artistId = null,
            artist = null,
            artists = emptyList(),
        )

        assertTrue(song.belongsToArtistInAlbum(yanhe, yanheAlbum))
    }

    @Test
    fun `name fallback does not split slash ampersand or comma inside artist names`() {
        val acdc = yanhe.copy(id = "acdc", name = "AC/DC")
        val earthWindAndFire = yanhe.copy(id = "ewf", name = "Earth, Wind & Fire")
        val nightcord = yanhe.copy(id = "nightcord", name = "25時、ナイトコードで。")

        assertTrue(song(artistId = null, artist = "AC/DC", artists = emptyList()).belongsToArtist(acdc))
        assertTrue(song(artistId = null, artist = "Earth, Wind & Fire", artists = emptyList()).belongsToArtist(earthWindAndFire))
        assertTrue(song(artistId = null, artist = "25時、ナイトコードで。", artists = emptyList()).belongsToArtist(nightcord))
        assertFalse(song(artistId = null, artist = "AC", artists = emptyList()).belongsToArtist(acdc))
        assertFalse(song(artistId = null, artist = "Earth", artists = emptyList()).belongsToArtist(earthWindAndFire))
        assertFalse(song(artistId = null, artist = "25時", artists = emptyList()).belongsToArtist(nightcord))
    }

    @Test
    fun `name fallback does not split collaboration strings in the client`() {
        val song = song(
            artistId = null,
            artist = "洛天依 • 言和",
            artists = emptyList(),
        )

        assertFalse(song.belongsToArtist(yanhe))
    }

    @Test
    fun `artist detail hides unknown album bucket owned by another primary artist`() {
        val luotianyiUnknownAlbum = albumSummary(
            id = "unknown-luotianyi",
            name = "[Unknown Album]",
            artistId = "luotianyi",
            artist = "洛天依",
            artists = listOf(luotianyi, yanheRef),
        )
        val yanheUnknownAlbum = albumSummary(
            id = "unknown-yanhe",
            name = "[Unknown Album]",
            artistId = "yanhe",
            artist = "言和",
            artists = listOf(yanheRef),
        )
        val collaborationAlbum = albumSummary(
            id = "collaboration",
            name = "人·間",
            artistId = "xinhua",
            artist = "心华",
            artists = listOf(ArtistRef(id = "xinhua", name = "心华"), yanheRef),
        )
        val artist = yanhe.copy(
            albumCount = 3,
            albums = listOf(luotianyiUnknownAlbum, yanheUnknownAlbum, collaborationAlbum),
        )

        val normalizedArtist = artist.withVisibleDetailAlbums()

        assertTrue(normalizedArtist.albums.any { it.id == "unknown-yanhe" })
        assertTrue(normalizedArtist.albums.any { it.id == "collaboration" })
        assertFalse(normalizedArtist.albums.any { it.id == "unknown-luotianyi" })
        assertTrue(normalizedArtist.albumCount == 2)
    }

    private fun album(
        artistId: String?,
        artist: String?,
        artists: List<ArtistRef>,
    ) = Album(
        id = "unknown-album",
        name = "[Unknown Album]",
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = null,
        songCount = null,
        durationSeconds = null,
        year = null,
        genre = null,
        created = null,
        songs = emptyList(),
    )

    private fun albumSummary(
        id: String,
        name: String,
        artistId: String?,
        artist: String?,
        artists: List<ArtistRef>,
    ) = AlbumSummary(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = null,
        songCount = null,
        durationSeconds = null,
        year = null,
        genre = null,
        created = null,
    )

    private fun song(
        artistId: String?,
        artist: String?,
        artists: List<ArtistRef>,
    ) = Song(
        id = "song",
        parentId = null,
        title = "Song",
        album = "[Unknown Album]",
        albumId = "unknown-album",
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = null,
        durationSeconds = null,
        track = null,
        discNumber = null,
        year = null,
        genre = null,
        bitRate = null,
        sampleRate = null,
        suffix = null,
        contentType = null,
        sizeBytes = null,
        path = null,
        created = null,
    )
}
