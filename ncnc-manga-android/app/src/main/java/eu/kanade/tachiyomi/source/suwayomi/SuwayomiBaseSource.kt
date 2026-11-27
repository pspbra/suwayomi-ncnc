package eu.kanade.tachiyomi.source.suwayomi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request

/**
 * Behaviour shared by every source backed by the Suwayomi server.
 *
 * [SuwayomiSource] exposes the server library, [SuwayomiRemoteSource] exposes one of the sources
 * installed on the server. Both read details, chapters and pages through the same GraphQL queries,
 * so the chapter url convention (`<mangaId>/<chapterId>`) is defined here exactly once.
 */
abstract class SuwayomiBaseSource(
    protected val preferences: SuwayomiServerPreferences,
    networkHelper: NetworkHelper,
    /** Reports the shared download counters of the browse pages to the cards. */
    protected val cache: SuwayomiSourceCache? = null,
) : HttpSource() {
    protected val api = SuwayomiServerApi(preferences, networkHelper.client)

    override val baseUrl: String
        get() = preferences.serverUrl

    override val client get() = api.client

    override fun getHomeUrl(): String = preferences.serverUrl

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val account =
            manga.url
                .split('/')
                .getOrNull(1)
                .orEmpty()
        val id = preferences.mangaId(manga.url)
        val details = if (fetchDetails) api.manga(id, account).toSManga() else manga
        val chapterList = if (fetchChapters) api.chapters(id, account).map(::toSChapter) else chapters
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        preferences.mangaId(chapter.url.substringBeforeLast('/'))
        val chapterId = chapter.url.substringAfterLast('/').toInt()
        return api.pages(chapterId, chapter.url.split('/')[1]).mapIndexed { index, path ->
            Page(index = index, url = chapter.url, imageUrl = api.absoluteUrl(path))
        }
    }

    override fun getMangaUrl(manga: SManga): String = "${preferences.serverUrl}/manga/${preferences.mangaId(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String =
        "${preferences.serverUrl}/manga/${preferences.mangaId(
            chapter.url.substringBeforeLast('/'),
        )}/chapter/${chapter.url.substringAfterLast('/')}"

    override fun imageRequest(page: Page): Request =
        GET(page.imageUrl!!, headersBuilder().build())
            .newBuilder()
            .tag(
                SuwayomiAccount::class.java,
                SuwayomiAccount(
                    page.url
                        .split('/')
                        .getOrNull(1)
                        .orEmpty(),
                ),
            ).build()

    fun toSChapter(value: ServerChapter) =
        SChapter.create().apply {
            url = value.localUrl
            name = value.name
            date_upload = value.uploadDate
            scanlator = value.scanlator
            chapter_number = value.chapterNumber
        }
}
