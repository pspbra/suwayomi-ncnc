package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.source.suwayomi.SuwayomiReadingSync
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.time.Clock

@Inject
class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
    private val serverSync: Lazy<SuwayomiReadingSync>,
) {
    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        if (mangaUpdate.isSet(MangaUpdate::favoriteAt)) {
            val manga = mangaRepository.getMangaById(mangaUpdate.id)
            if (manga.url.startsWith("account/")) {
                if (!serverSync.value.setFavorite(manga, mangaUpdate.favoriteAt != null)) return false
            }
        }
        return mangaRepository.update(mangaUpdate)
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        var success = true
        for (update in mangaUpdates) if (!await(update)) success = false
        return success
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        dateTime: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime.date, timeZone),
    ): Boolean =
        mangaRepository.update(
            fetchInterval.withFetchInterval(manga, dateTime, timeZone, window),
        )

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean =
        mangaRepository.update(
            MangaUpdate(mangaId) {
                lastUpdate = Clock.System.now().toEpochMilliseconds()
            },
        )

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean =
        mangaRepository.update(
            MangaUpdate(mangaId) {
                coverLastModified = Clock.System.now().toEpochMilliseconds()
            },
        )

    suspend fun awaitUpdateFavorite(
        mangaId: Long,
        favorite: Boolean,
    ): Boolean {
        val update =
            when (favorite) {
                true -> MangaUpdate(mangaId) { favoriteAt = Clock.System.now().toEpochMilliseconds() }
                false -> MangaUpdate(mangaId) { favoriteAt = null }
            }
        return await(update)
    }
}
