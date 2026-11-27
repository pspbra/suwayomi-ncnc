package eu.kanade.tachiyomi.source.suwayomi

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage

/**
 * One of the sources installed on the Suwayomi server, exposed as a Mihon source so that the native
 * browse screen can be reused to list its manga.
 *
 * The manga are not stored on the device — listing goes through the `fetchSourceManga` mutation,
 * which asks the server to run its extension. The results are written to the server's database
 * (without joining its library), which is why details and chapters can be read back by id.
 *
 * Dynamic filters of the server extension are not mapped, so [getFilterList] stays empty.
 */
class SuwayomiRemoteSource(
    preferences: SuwayomiServerPreferences,
    networkHelper: NetworkHelper,
    private val serverSource: ServerSource,
    cache: SuwayomiSourceCache? = null,
) : SuwayomiBaseSource(preferences, networkHelper, cache) {
    override val id = serverSource.mihonSourceId()

    override val name = serverSource.name
    override val lang = serverSource.lang
    override val supportsLatest = serverSource.supportsLatest

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, query = "", type = BROWSE_TYPE_POPULAR)

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, query = "", type = BROWSE_TYPE_LATEST)

    // An empty query means "browse everything"; the server's SEARCH would look for nothing.
    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = browse(page, query, type = if (query.isBlank()) BROWSE_TYPE_POPULAR else BROWSE_TYPE_SEARCH)

    private suspend fun browse(
        page: Int,
        query: String,
        type: String,
    ): MangasPage {
        val result = api.browseSource(serverSource.id, page = page, query = query, type = type)
        cache?.rememberLibrary(result.mangas)
        return MangasPage(result.mangas.map { it.toSManga() }, result.hasNextPage)
    }
}

/**
 * Id a server source is registered under.
 *
 * Suwayomi exposes the source's Tachiyomi id as a string, so this is normally just that number and
 * stays stable across restarts. The hash only covers the day that stops being true — callers open
 * the source by this id, so they must derive it the same way.
 */
internal fun ServerSource.mihonSourceId(): Long = id.toLongOrNull() ?: id.hashCode().toLong()
