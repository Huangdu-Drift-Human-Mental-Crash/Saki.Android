package org.hdhmc.saki.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hdhmc.saki.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SakiDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = SakiDatabase::class.java,
    )

    @Test
    fun migrate14To15AddsCachedAtToLibraryListCaches() {
        val databaseName = "saki-migration-14-15"
        helper.createDatabase(databaseName, 14).apply {
            insertLibraryListCacheRows()
            close()
        }

        helper.runMigrationsAndValidate(
            name = databaseName,
            version = 15,
            validateDroppedTables = true,
            migrations = DatabaseModule.allMigrations().filter { it.startVersion >= 14 }.toTypedArray(),
        ).apply {
            assertCachedAtDefault("cached_artists")
            assertCachedAtDefault("cached_albums")
            assertCachedAtDefault("cached_playlists")
            assertCachedAtDefault("cached_library_songs")
            close()
        }
    }

    @Test
    fun migrate15To16AddsArtistShortcutsCache() {
        val databaseName = "saki-migration-15-16"
        helper.createDatabase(databaseName, 15).close()

        helper.runMigrationsAndValidate(
            name = databaseName,
            version = 16,
            validateDroppedTables = true,
            migrations = DatabaseModule.allMigrations().filter { it.startVersion >= 15 }.toTypedArray(),
        ).apply {
            assertTableExists("cached_artist_shortcuts")
            close()
        }
    }

    private fun SupportSQLiteDatabase.insertLibraryListCacheRows() {
        execSQL(
            """
            INSERT INTO cached_artists (
                serverId, artistId, name, sectionName, albumCount, coverArtId, artistImageUrl
            ) VALUES (1, 'artist-1', 'Artist', 'A', 1, 'artist-cover', 'https://example.invalid/artist.jpg')
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO cached_albums (
                serverId, albumId, listType, name, artist, artistId, artistsJson, coverArtId,
                songCount, durationSeconds, year, genre, created, sortOrder
            ) VALUES (
                1, 'album-1', 'newest', 'Album', 'Artist', 'artist-1', NULL, 'album-cover',
                10, 600, 2026, 'Genre', '2026-06-28T00:00:00Z', 0
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO cached_playlists (
                serverId, playlistId, name, owner, isPublic, songCount, durationSeconds, coverArtId,
                created, changed
            ) VALUES (
                1, 'playlist-1', 'Playlist', 'owner', 1, 3, 180, 'playlist-cover',
                '2026-06-28T00:00:00Z', '2026-06-28T00:01:00Z'
            )
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO cached_library_songs (
                serverId, songId, parentId, title, album, albumId, artist, artistId, artistsJson,
                coverArtId, durationSeconds, track, discNumber, year, genre, bitRate, sampleRate,
                suffix, contentType, sizeBytes, path, created
            ) VALUES (
                1, 'song-1', 'album-1', 'Song', 'Album', 'album-1', 'Artist', 'artist-1', NULL,
                'song-cover', 180, 1, 1, 2026, 'Genre', 320, 44100,
                'mp3', 'audio/mpeg', 123456, 'Artist/Album/Song.mp3', '2026-06-28T00:00:00Z'
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.assertCachedAtDefault(tableName: String) {
        query("SELECT COUNT(*), MIN(cachedAt), MAX(cachedAt) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            assertEquals(tableName, 1, cursor.getInt(0))
            assertEquals(tableName, 0L, cursor.getLong(1))
            assertEquals(tableName, 0L, cursor.getLong(2))
        }
    }

    private fun SupportSQLiteDatabase.assertTableExists(tableName: String) {
        query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(tableName, 1, cursor.getInt(0))
        }
    }
}
