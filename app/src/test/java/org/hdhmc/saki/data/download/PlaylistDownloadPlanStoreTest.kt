package org.hdhmc.saki.data.download

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.hdhmc.saki.domain.model.ArtistRef
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaylistDownloadPlanStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stored plan is an immutable deduplicated snapshot with complete song metadata`() = runTest {
        val directory = temporaryFolder.newFolder("plans")
        val store = PlaylistDownloadPlanStore(
            directory = directory,
            moshi = Moshi.Builder().build(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val original = song("song-夏")
        val mutableSongs = mutableListOf(original, original.copy(title = "duplicate"))
        val playlist = playlist(mutableSongs)

        val planId = store.createPlanId()
        store.write(planId = planId, serverId = 9L, playlist = playlist)
        mutableSongs.clear()
        val restored = store.read(planId)

        assertEquals(9L, restored?.serverId)
        assertEquals(playlist.id, restored?.playlistId)
        assertEquals(playlist.name, restored?.title)
        assertEquals(listOf(original), restored?.songs)

        store.delete(planId)
        assertNull(store.read(planId))
    }

    @Test
    fun `invalid plan ids cannot address files outside the plan directory`() = runTest {
        val store = PlaylistDownloadPlanStore(
            directory = temporaryFolder.newFolder("plans"),
            moshi = Moshi.Builder().build(),
            ioDispatcher = Dispatchers.Unconfined,
        )

        assertNull(store.read("../outside"))
        store.delete("../outside")
    }

    @Test
    fun `old unreferenced plans are removed without touching referenced work`() = runTest {
        val directory = temporaryFolder.newFolder("plans")
        val store = PlaylistDownloadPlanStore(
            directory = directory,
            moshi = Moshi.Builder().build(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val planId = store.createPlanId()
        store.write(
            planId = planId,
            serverId = 9L,
            playlist = playlist(listOf(song("song"))),
        )
        val afterGracePeriod = System.currentTimeMillis() + 2L * 24L * 60L * 60L * 1_000L

        store.deleteUnreferenced(setOf(planId), nowMillis = afterGracePeriod)
        assertEquals(1, store.read(planId)?.songs?.size)

        store.deleteUnreferenced(emptySet(), nowMillis = afterGracePeriod)
        assertNull(store.read(planId))
    }
}

private fun playlist(songs: List<Song>) = Playlist(
    id = "playlist-1",
    name = "夜间播放列表",
    owner = "owner",
    isPublic = false,
    songCount = songs.size,
    durationSeconds = 321,
    coverArtId = "playlist-cover",
    created = "2026-08-09T00:00:00Z",
    changed = "2026-08-09T01:00:00Z",
    songs = songs,
)

private fun song(id: String) = Song(
    id = id,
    parentId = "parent",
    title = "曲目 $id",
    album = "Album",
    albumId = "album-id",
    artist = "Artist",
    artistId = "artist-id",
    artists = listOf(ArtistRef(id = "artist-id", name = "Artist")),
    coverArtId = "cover-id",
    durationSeconds = 321,
    track = 4,
    discNumber = 2,
    year = 2026,
    genre = "Electronic",
    bitRate = 320,
    sampleRate = 48_000,
    suffix = "flac",
    contentType = "audio/flac",
    sizeBytes = 12_345_678L,
    path = "Music/Album/Track.flac",
    created = "2026-08-09T00:00:00Z",
)
