package eu.kanade.tachiyomi.source.suwayomi

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga

/**
 * The server's library, exposed as a Mihon source. Backs the "浏览" tab.
 */
class SuwayomiSource(
    preferences: SuwayomiServerPreferences,
    networkHelper: NetworkHelper,
    cache: SuwayomiSourceCache? = null,
) : SuwayomiBaseSource(preferences, networkHelper, cache) {
    override val id = ID
    override val name = "NCNC漫画"
    override val lang = "all"
    override val supportsLatest = true

    override suspend fun getPopularManga(page: Int): MangasPage = getLibraryPage(page, "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getLibraryPage(page, "")

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = getLibraryPage(page, query)

    private suspend fun getLibraryPage(
        page: Int,
        query: String,
    ): MangasPage {
        val result = api.serverLibrary(page, query)
        cache?.rememberLibrary(result.items.map { it.manga })
        cache?.rememberLibraryCounts(result.items)
        return MangasPage(result.items.map { it.manga.toSManga() }, result.hasNextPage)
    }

    companion object {
        const val ID = 3100117499901280806L
    }
}

/**
 * Maps a server manga to Mihon's [SManga].
 *
 * Shared by [SuwayomiSource] and [SuwayomiRemoteSource]. The paging source upserts what this
 * produces into the local database; that is what gives each manga a local id to open.
 */
internal fun ServerManga.toSManga(): SManga {
    // Aliased so the properties below don't resolve to SManga's own fields inside apply.
    val value = this
    return SManga.create().apply {
        url = value.localUrl
        title = value.title
        thumbnail_url = value.thumbnailUrl
        artist = value.artist
        author = value.author
        description = value.description
        genre = value.genres.joinToString(", ")
        status =
            when (value.status) {
                "ONGOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "LICENSED" -> SManga.LICENSED
                "PUBLISHING_FINISHED" -> SManga.PUBLISHING_FINISHED
                "CANCELLED" -> SManga.CANCELLED
                "ON_HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        initialized = true
    }
}
