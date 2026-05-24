package org.hdhmc.saki.data.repository

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.hdhmc.saki.data.local.dao.LibraryCacheDao
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailAlbumEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedArtistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedArtistEntity
import org.hdhmc.saki.data.local.entity.CachedLibrarySongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistDetailSongEntity
import org.hdhmc.saki.data.local.entity.CachedPlaylistEntity
import org.hdhmc.saki.data.local.entity.CachedSongMetadataEntity
import org.hdhmc.saki.data.local.entity.CachedSongArtistEntity
import org.hdhmc.saki.data.local.entity.CachedSongArtistSummary
import org.hdhmc.saki.di.IoDispatcher
import org.hdhmc.saki.domain.model.Album
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.AlbumSummary
import org.hdhmc.saki.domain.model.Artist
import org.hdhmc.saki.domain.model.ArtistRef
import org.hdhmc.saki.domain.model.ArtistSection
import org.hdhmc.saki.domain.model.ArtistSummary
import org.hdhmc.saki.domain.model.CachedArtistDetail
import org.hdhmc.saki.domain.model.LibraryIndexes
import org.hdhmc.saki.domain.model.Playlist
import org.hdhmc.saki.domain.model.PlaylistSummary
import org.hdhmc.saki.domain.model.SearchResults
import org.hdhmc.saki.domain.model.Song
import org.hdhmc.saki.domain.repository.LibraryCacheRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryCacheRepository @Inject constructor(
    private val dao: LibraryCacheDao,
    moshi: Moshi,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LibraryCacheRepository {
    private val artistRefsAdapter: JsonAdapter<List<CachedArtistRefJsonDto>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, CachedArtistRefJsonDto::class.java))

    override suspend fun getArtists(serverId: Long): LibraryIndexes? = withContext(ioDispatcher) {
        val entities = dao.getArtists(serverId)
        val songArtistSummaries = dao.getSongArtistSummaries(serverId)
        if (entities.isEmpty() && songArtistSummaries.isEmpty()) return@withContext null
        val artists = mergeArtistSummaries(
            serverArtists = entities.map { it.toDomain() },
            songArtists = songArtistSummaries.map { it.toDomain() },
        )
        LibraryIndexes(
            lastModified = null,
            ignoredArticles = null,
            shortcuts = emptyList(),
            sections = listOf(ArtistSection(name = "#", artists = artists)),
        )
    }

    override suspend fun saveArtists(serverId: Long, indexes: LibraryIndexes) = withContext(ioDispatcher) {
        val entities = indexes.sections.flatMap { section ->
            section.artists.map { artist ->
                CachedArtistEntity(
                    serverId = serverId,
                    artistId = artist.id,
                    name = artist.name,
                    sectionName = section.name,
                    albumCount = artist.albumCount,
                    coverArtId = artist.coverArtId,
                    artistImageUrl = artist.artistImageUrl,
                )
            }
        }
        dao.replaceArtists(serverId, entities)
    }

    override suspend fun getAlbums(serverId: Long, type: AlbumListType): List<AlbumSummary> = withContext(ioDispatcher) {
        dao.getAlbums(serverId, type.apiValue).map { it.toDomain() }
    }

    override suspend fun saveAlbums(serverId: Long, type: AlbumListType, albums: List<AlbumSummary>) = withContext(ioDispatcher) {
        val entities = albums.mapIndexed { index, album ->
            CachedAlbumEntity(
                serverId = serverId,
                albumId = album.id,
                listType = type.apiValue,
                name = album.name,
                artist = album.artist,
                artistId = album.artistId,
                artistsJson = album.artists.toArtistRefsJson(),
                coverArtId = album.coverArtId,
                songCount = album.songCount,
                durationSeconds = album.durationSeconds,
                year = album.year,
                genre = album.genre,
                created = album.created,
                sortOrder = index,
            )
        }
        dao.replaceAlbums(serverId, type.apiValue, entities)
    }

    override suspend fun getPlaylists(serverId: Long): List<PlaylistSummary> = withContext(ioDispatcher) {
        dao.getPlaylists(serverId).map { it.toDomain() }
    }

    override suspend fun savePlaylists(serverId: Long, playlists: List<PlaylistSummary>) = withContext(ioDispatcher) {
        val entities = playlists.map { playlist ->
            CachedPlaylistEntity(
                serverId = serverId,
                playlistId = playlist.id,
                name = playlist.name,
                owner = playlist.owner,
                isPublic = playlist.isPublic,
                songCount = playlist.songCount,
                durationSeconds = playlist.durationSeconds,
                coverArtId = playlist.coverArtId,
                created = playlist.created,
                changed = playlist.changed,
            )
        }
        dao.replacePlaylists(serverId, entities)
    }

    override suspend fun getCachedPlaylistDetailIds(
        serverId: Long,
        playlistIds: List<String>,
    ): Set<String> = withContext(ioDispatcher) {
        if (playlistIds.isEmpty()) return@withContext emptySet()
        playlistIds.distinct()
            .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> dao.getCachedPlaylistDetailIds(serverId, chunk) }
            .toSet()
    }

    override suspend fun getSongs(serverId: Long): List<Song> = withContext(ioDispatcher) {
        dao.getSongs(serverId).map { it.toDomain() }
    }

    override suspend fun getSongsPage(
        serverId: Long,
        limit: Int,
        offset: Int,
    ): List<Song> = withContext(ioDispatcher) {
        val safeLimit = limit.coerceAtLeast(0)
        val safeOffset = offset.coerceAtLeast(0)
        if (safeLimit == 0) return@withContext emptyList()
        val safeEnd = safeOffset.toLong()
            .plus(safeLimit)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val orderedPage = dao.getSongMetadataPageByLibraryOrder(
            serverId = serverId,
            startOrder = safeOffset,
            endOrder = safeEnd,
        )
        if (orderedPage.isNotEmpty() || dao.hasOrderedSongMetadata(serverId, LIBRARY_ORDER_UNSET)) {
            return@withContext orderedPage.map { it.toDomain() }
        }

        dao.getSongMetadataPage(
            serverId = serverId,
            limit = safeLimit,
            offset = safeOffset,
        ).map { it.toDomain() }
    }

    override suspend fun saveSongs(serverId: Long, songs: List<Song>) = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        replaceLibrarySongs(serverId, songs)
        dao.pruneUnreferencedSongMetadata(serverId)
        dao.pruneOrphanedSongArtists(serverId)
        saveSongMetadataPageInternal(serverId, songs, cachedAt, startOrder = 0)
    }

    override suspend fun saveSongsWindow(
        serverId: Long,
        songs: List<Song>,
        cachedAt: Long,
        startOrder: Int?,
    ): Unit = withContext(ioDispatcher) {
        replaceLibrarySongs(serverId, songs)
        saveSongMetadataPageInternal(serverId, songs, cachedAt, startOrder)
    }

    override suspend fun saveSongMetadataPage(
        serverId: Long,
        songs: List<Song>,
        cachedAt: Long,
        startOrder: Int?,
    ): Unit = withContext(ioDispatcher) {
        saveSongMetadataPageInternal(serverId, songs, cachedAt, startOrder)
    }

    override suspend fun pruneSongMetadataBefore(
        serverId: Long,
        cachedAt: Long,
    ): Unit = withContext(ioDispatcher) {
        dao.pruneSongMetadataBefore(serverId, cachedAt)
        dao.pruneOrphanedSongArtists(serverId)
    }

    override suspend fun searchCached(
        serverId: Long,
        query: String,
        artistCount: Int,
        albumCount: Int,
        songCount: Int,
    ): SearchResults = withContext(ioDispatcher) {
        val queryPattern = "%${query.trim()}%"
        SearchResults(
            artists = if (artistCount > 0) {
                dao.searchArtists(serverId, queryPattern, artistCount).map { it.toDomain() }
            } else {
                emptyList()
            },
            albums = if (albumCount > 0) {
                dao.searchAlbums(serverId, queryPattern, albumCount * SEARCH_DUPLICATE_BUFFER_MULTIPLIER)
                    .map { it.toDomain() }
                    .distinctBy(AlbumSummary::id)
                    .take(albumCount)
            } else {
                emptyList()
            },
            songs = if (songCount > 0) {
                dao.searchSongMetadata(serverId, queryPattern, songCount).map { it.toDomain() }
            } else {
                emptyList()
            },
        )
    }

    override suspend fun getArtistDetail(
        serverId: Long,
        artistId: String,
    ): CachedArtistDetail? = withContext(ioDispatcher) {
        val detail = dao.getArtistDetail(serverId, artistId)
        if (detail != null) {
            val albums = dao.getArtistDetailAlbums(serverId, artistId).map { it.toDomain() }
            val topSongRefs = dao.getArtistDetailSongs(serverId, artistId)
            val topSongs = resolveSongs(serverId, topSongRefs.map { it.songId })
            val relationshipSongs = resolveSongsByArtistId(serverId, artistId)
            val songArtistSummary = dao.getSongArtistSummaries(serverId)
                .firstOrNull { it.artistId == artistId }
            val artist = detail.toDomain(albums).let { cachedArtist ->
                songArtistSummary?.let { summary ->
                    cachedArtist.copy(
                        name = summary.name,
                        coverArtId = cachedArtist.coverArtId ?: summary.coverArtId,
                        albumCount = cachedArtist.albumCount ?: summary.albumCount.takeIf { it > 0 },
                    )
                } ?: cachedArtist
            }
            return@withContext CachedArtistDetail(
                artist = artist,
                songs = mergeSongs(topSongs, relationshipSongs),
                songsAreTopSongs = relationshipSongs.isEmpty(),
            )
        }
        getInferredArtistDetail(serverId, artistId)
    }

    override suspend fun saveArtistDetail(
        serverId: Long,
        detail: CachedArtistDetail,
    ): Unit = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        dao.replaceArtistDetail(
            detail = detail.artist.toEntity(serverId, cachedAt),
            albums = detail.artist.albums.mapIndexed { index, album ->
                album.toArtistDetailAlbumEntity(serverId, detail.artist.id, index)
            },
            topSongs = detail.songs.mapIndexed { index, song ->
                CachedArtistDetailSongEntity(
                    serverId = serverId,
                    artistId = detail.artist.id,
                    songId = song.id,
                    sortOrder = index,
                )
            },
            songMetadata = detail.songs.map { song -> song.toMetadataEntity(serverId, cachedAt) },
        )
        replaceSongArtists(serverId, detail.songs)
    }

    override suspend fun getAlbumDetail(
        serverId: Long,
        albumId: String,
    ): Album? = withContext(ioDispatcher) {
        val detail = dao.getAlbumDetail(serverId, albumId)
        if (detail != null) {
            val songRefs = dao.getAlbumDetailSongs(serverId, albumId)
            return@withContext detail.toDomain(resolveSongs(serverId, songRefs.map { it.songId }))
        }
        getInferredAlbumDetail(serverId, albumId)
    }

    override suspend fun saveAlbumDetail(
        serverId: Long,
        album: Album,
    ): Unit = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        dao.replaceAlbumDetail(
            detail = album.toEntity(serverId, cachedAt),
            songs = album.songs.mapIndexed { index, song ->
                CachedAlbumDetailSongEntity(
                    serverId = serverId,
                    albumId = album.id,
                    songId = song.id,
                    sortOrder = index,
                )
            },
            songMetadata = album.songs.map { song -> song.toMetadataEntity(serverId, cachedAt) },
        )
        replaceSongArtists(serverId, album.songs)
    }

    override suspend fun getPlaylistDetail(
        serverId: Long,
        playlistId: String,
    ): Playlist? = withContext(ioDispatcher) {
        val detail = dao.getPlaylistDetail(serverId, playlistId) ?: return@withContext null
        val songRefs = dao.getPlaylistDetailSongs(serverId, playlistId)
        detail.toDomain(resolveSongs(serverId, songRefs.map { it.songId }))
    }

    override suspend fun savePlaylistDetail(
        serverId: Long,
        playlist: Playlist,
    ): Unit = withContext(ioDispatcher) {
        val cachedAt = System.currentTimeMillis()
        dao.replacePlaylistDetail(
            detail = playlist.toEntity(serverId, cachedAt),
            songs = playlist.songs.mapIndexed { index, song ->
                CachedPlaylistDetailSongEntity(
                    serverId = serverId,
                    playlistId = playlist.id,
                    songId = song.id,
                    sortOrder = index,
                )
            },
            songMetadata = playlist.songs.map { song -> song.toMetadataEntity(serverId, cachedAt) },
        )
        replaceSongArtists(serverId, playlist.songs)
    }

    private fun CachedArtistEntity.toDomain() = ArtistSummary(
        id = artistId,
        name = name,
        albumCount = albumCount,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
    )

    private fun CachedSongArtistSummary.toDomain() = ArtistSummary(
        id = artistId,
        name = name,
        albumCount = albumCount,
        coverArtId = coverArtId,
        artistImageUrl = null,
    )

    private fun mergeArtistSummaries(
        serverArtists: List<ArtistSummary>,
        songArtists: List<ArtistSummary>,
    ): List<ArtistSummary> {
        return (songArtists + serverArtists)
            .distinctBy(ArtistSummary::id)
            .sortedBy { it.name.lowercase() }
    }

    private fun CachedAlbumEntity.toDomain() = AlbumSummary(
        id = albumId,
        name = name,
        artist = artist,
        artistId = artistId,
        artists = artistsJson.toArtistRefs(),
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
    )

    private fun CachedPlaylistEntity.toDomain() = PlaylistSummary(
        id = playlistId,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        created = created,
        changed = changed,
    )

    private fun CachedLibrarySongEntity.toDomain() = Song(
        id = songId,
        parentId = parentId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        artists = artistsJson.toArtistRefs(),
        coverArtId = coverArtId,
        durationSeconds = durationSeconds,
        track = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
    )

    private suspend fun replaceLibrarySongs(serverId: Long, songs: List<Song>) {
        dao.replaceSongs(serverId, songs.map { song -> song.toLibraryEntity(serverId) })
    }

    private suspend fun saveSongMetadataPageInternal(
        serverId: Long,
        songs: List<Song>,
        cachedAt: Long,
        startOrder: Int?,
    ) {
        val existingOrders = if (startOrder == null && songs.isNotEmpty()) {
            songs.map(Song::id)
                .distinct()
                .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
                .flatMap { chunk -> dao.getSongMetadataOrders(serverId, chunk) }
                .associate { it.songId to it.libraryOrder }
        } else {
            emptyMap()
        }
        songs.asSequence()
            .mapIndexed { index, song ->
                song.toMetadataEntity(
                    serverId = serverId,
                    cachedAt = cachedAt,
                    libraryOrder = startOrder?.plus(index) ?: existingOrders[song.id] ?: LIBRARY_ORDER_UNSET,
                )
            }
            .chunked(SONG_METADATA_WRITE_CHUNK_SIZE)
            .forEach { chunk -> dao.insertSongMetadata(chunk) }
        replaceSongArtists(serverId, songs)
    }

    private suspend fun replaceSongArtists(serverId: Long, songs: List<Song>) {
        val songIds = songs.map(Song::id).distinct()
        if (songIds.isEmpty()) return
        dao.replaceSongArtists(
            serverId = serverId,
            songIds = songIds,
            artists = songs.flatMap { song -> song.toSongArtistEntities(serverId) },
        )
    }

    private fun Song.toLibraryEntity(serverId: Long) = CachedLibrarySongEntity(
        serverId = serverId,
        songId = id,
        parentId = parentId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        artistsJson = artists.toArtistRefsJson(),
        coverArtId = coverArtId,
        durationSeconds = durationSeconds,
        track = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
    )

    private fun Song.toSongArtistEntities(serverId: Long): List<CachedSongArtistEntity> {
        return artists.mapIndexedNotNull { index, artist ->
            val artistId = artist.id.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            val name = artist.name.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            CachedSongArtistEntity(
                serverId = serverId,
                songId = id,
                artistId = artistId,
                name = name,
                sortOrder = index,
            )
        }
    }

    private suspend fun resolveSongs(serverId: Long, songIds: List<String>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        val distinctSongIds = songIds.distinct()
        val metadataSongs = distinctSongIds
            .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> dao.getSongMetadata(serverId, chunk).map { it.toDomain() } }
        val librarySongs = distinctSongIds
            .chunked(IN_CLAUSE_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> dao.getLibrarySongs(serverId, chunk).map { it.toDomain() } }
        val songsById = (librarySongs + metadataSongs).associateBy(Song::id)
        return songIds.mapNotNull { songId -> songsById[songId] }
    }

    private suspend fun getInferredArtistDetail(
        serverId: Long,
        artistId: String,
    ): CachedArtistDetail? {
        val songs = resolveSongsByArtistId(serverId, artistId)
        val cachedAlbums = dao.getAlbumSummariesByArtistId(serverId, artistId)
            .map { it.toDomain() }
            .distinctBy(AlbumSummary::id)
        val albums = (cachedAlbums + inferAlbumSummariesFromSongs(songs)).distinctBy(AlbumSummary::id)
        if (albums.isEmpty() && songs.isEmpty()) return null

        val summary = dao.getArtistSummary(serverId, artistId)
        val songArtistSummary = dao.getSongArtistSummaries(serverId)
            .firstOrNull { it.artistId == artistId }
        val artist = summary?.toDomain(albums) ?: Artist(
            id = artistId,
            name = songArtistSummary?.name ?: songs.firstOrNull()?.artist ?: albums.firstOrNull()?.artist ?: artistId,
            coverArtId = songArtistSummary?.coverArtId
                ?: songs.asSequence().mapNotNull(Song::coverArtId).firstOrNull()
                ?: albums.asSequence().mapNotNull(AlbumSummary::coverArtId).firstOrNull(),
            artistImageUrl = null,
            albumCount = songArtistSummary?.albumCount?.takeIf { it > 0 } ?: albums.size.takeIf { it > 0 },
            albums = albums,
        )
        return CachedArtistDetail(
            artist = artist,
            songs = songs,
            songsAreTopSongs = false,
        )
    }

    private suspend fun getInferredAlbumDetail(
        serverId: Long,
        albumId: String,
    ): Album? {
        val songs = resolveSongsByAlbumId(serverId, albumId)
        if (songs.isEmpty()) return null
        val summary = dao.getAlbumSummary(serverId, albumId)?.toDomain()
        return summary?.toDomain(songs) ?: songs.first().toInferredAlbum(albumId, songs)
    }

    private suspend fun resolveSongsByAlbumId(serverId: Long, albumId: String): List<Song> {
        val librarySongs = dao.getLibrarySongsByAlbumId(serverId, albumId).map { it.toDomain() }
        val metadataSongs = dao.getSongMetadataByAlbumId(serverId, albumId).map { it.toDomain() }
        return mergeSongs(librarySongs, metadataSongs)
    }

    private suspend fun resolveSongsByArtistId(serverId: Long, artistId: String): List<Song> {
        val librarySongs = dao.getLibrarySongsByArtistId(serverId, artistId).map { it.toDomain() }
        val metadataSongs = dao.getSongMetadataByArtistId(serverId, artistId).map { it.toDomain() }
        return mergeSongs(librarySongs, metadataSongs)
    }

    private fun mergeSongs(primary: List<Song>, secondary: List<Song>): List<Song> {
        return (primary + secondary).distinctBy(Song::id)
    }

    private fun inferAlbumSummariesFromSongs(songs: List<Song>): List<AlbumSummary> {
        return songs.asSequence()
            .filter { song -> song.albumId != null }
            .groupBy { song -> song.albumId.orEmpty() }
            .map { (albumId, albumSongs) -> albumSongs.first().toInferredAlbumSummary(albumId, albumSongs) }
            .sortedWith(compareBy<AlbumSummary>({ it.year ?: Int.MAX_VALUE }, { it.name.lowercase() }))
    }

    private fun Song.toInferredAlbumSummary(albumId: String, songs: List<Song>) = AlbumSummary(
        id = albumId,
        name = album ?: albumId,
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = coverArtId,
        songCount = songs.size,
        durationSeconds = songs.totalDurationSeconds(),
        year = year,
        genre = genre,
        created = null,
    )

    private fun AlbumSummary.toDomain(songs: List<Song>) = Album(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = coverArtId,
        songCount = songCount ?: songs.size,
        durationSeconds = durationSeconds ?: songs.totalDurationSeconds(),
        year = year,
        genre = genre,
        created = created,
        songs = songs,
    )

    private fun Song.toInferredAlbum(albumId: String, songs: List<Song>) = Album(
        id = albumId,
        name = album ?: albumId,
        artist = artist,
        artistId = artistId,
        artists = artists,
        coverArtId = coverArtId,
        songCount = songs.size,
        durationSeconds = songs.totalDurationSeconds(),
        year = year,
        genre = genre,
        created = null,
        songs = songs,
    )

    private fun List<Song>.totalDurationSeconds(): Int? {
        val durations = mapNotNull(Song::durationSeconds)
        return durations.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun CachedArtistDetailEntity.toDomain(albums: List<AlbumSummary>) = Artist(
        id = artistId,
        name = name,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
        albumCount = albumCount,
        albums = albums,
    )

    private fun CachedArtistEntity.toDomain(albums: List<AlbumSummary>) = Artist(
        id = artistId,
        name = name,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
        albumCount = albumCount,
        albums = albums,
    )

    private fun Artist.toEntity(serverId: Long, cachedAt: Long) = CachedArtistDetailEntity(
        serverId = serverId,
        artistId = id,
        name = name,
        coverArtId = coverArtId,
        artistImageUrl = artistImageUrl,
        albumCount = albumCount,
        cachedAt = cachedAt,
        isComplete = true,
    )

    private fun AlbumSummary.toArtistDetailAlbumEntity(
        serverId: Long,
        detailArtistId: String,
        sortOrder: Int,
    ) = CachedArtistDetailAlbumEntity(
        serverId = serverId,
        artistId = detailArtistId,
        albumId = id,
        name = name,
        artist = artist,
        albumArtistId = artistId,
        artistsJson = artists.toArtistRefsJson(),
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
        sortOrder = sortOrder,
    )

    private fun CachedArtistDetailAlbumEntity.toDomain() = AlbumSummary(
        id = albumId,
        name = name,
        artist = artist,
        artistId = albumArtistId,
        artists = artistsJson.toArtistRefs(),
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
    )

    private fun Album.toEntity(serverId: Long, cachedAt: Long) = CachedAlbumDetailEntity(
        serverId = serverId,
        albumId = id,
        name = name,
        artist = artist,
        artistId = artistId,
        artistsJson = artists.toArtistRefsJson(),
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
        cachedAt = cachedAt,
        isComplete = true,
    )

    private fun CachedAlbumDetailEntity.toDomain(songs: List<Song>) = Album(
        id = albumId,
        name = name,
        artist = artist,
        artistId = artistId,
        artists = artistsJson.toArtistRefs(),
        coverArtId = coverArtId,
        songCount = songCount,
        durationSeconds = durationSeconds,
        year = year,
        genre = genre,
        created = created,
        songs = songs,
    )

    private fun Playlist.toEntity(serverId: Long, cachedAt: Long) = CachedPlaylistDetailEntity(
        serverId = serverId,
        playlistId = id,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        created = created,
        changed = changed,
        cachedAt = cachedAt,
        isComplete = true,
    )

    private fun CachedPlaylistDetailEntity.toDomain(songs: List<Song>) = Playlist(
        id = playlistId,
        name = name,
        owner = owner,
        isPublic = isPublic,
        songCount = songCount,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        created = created,
        changed = changed,
        songs = songs,
    )

    private fun Song.toMetadataEntity(
        serverId: Long,
        cachedAt: Long,
        libraryOrder: Int = LIBRARY_ORDER_UNSET,
    ) = CachedSongMetadataEntity(
        serverId = serverId,
        songId = id,
        parentId = parentId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        artistsJson = artists.toArtistRefsJson(),
        coverArtId = coverArtId,
        durationSeconds = durationSeconds,
        track = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
        cachedAt = cachedAt,
        libraryOrder = libraryOrder,
    )

    private fun CachedSongMetadataEntity.toDomain() = Song(
        id = songId,
        parentId = parentId,
        title = title,
        album = album,
        albumId = albumId,
        artist = artist,
        artistId = artistId,
        artists = artistsJson.toArtistRefs(),
        coverArtId = coverArtId,
        durationSeconds = durationSeconds,
        track = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        sampleRate = sampleRate,
        suffix = suffix,
        contentType = contentType,
        sizeBytes = sizeBytes,
        path = path,
        created = created,
    )

    private fun List<ArtistRef>.toArtistRefsJson(): String? {
        if (isEmpty()) return null
        val payload = map { artist ->
            CachedArtistRefJsonDto(
                id = artist.id,
                name = artist.name,
            )
        }
        return artistRefsAdapter.toJson(payload)
    }

    private fun String?.toArtistRefs(): List<ArtistRef> {
        if (isNullOrBlank()) return emptyList()
        return runCatching {
            artistRefsAdapter.fromJson(this).orEmpty().mapNotNull { artist ->
                val id = artist.id.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val name = artist.name.takeIf(String::isNotBlank) ?: return@mapNotNull null
                ArtistRef(id = id, name = name)
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val IN_CLAUSE_QUERY_CHUNK_SIZE = 500
        const val SONG_METADATA_WRITE_CHUNK_SIZE = 500
        const val SEARCH_DUPLICATE_BUFFER_MULTIPLIER = 4
        const val LIBRARY_ORDER_UNSET = Int.MAX_VALUE
    }
}

@JsonClass(generateAdapter = true)
internal data class CachedArtistRefJsonDto(
    val id: String,
    val name: String,
)
