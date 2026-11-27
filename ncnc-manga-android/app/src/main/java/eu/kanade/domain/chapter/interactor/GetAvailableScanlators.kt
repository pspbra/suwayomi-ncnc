package eu.kanade.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.chapter.repository.ChapterRepository

@Inject
class GetAvailableScanlators(
    private val repository: ChapterRepository,
) {
    private fun List<String>.cleanupAvailableScanlators(): Set<String> = mapNotNull { it.ifBlank { null } }.toSet()

    suspend fun await(mangaId: Long): Set<String> =
        repository
            .getScanlatorsByMangaId(mangaId)
            .cleanupAvailableScanlators()

    fun subscribe(mangaId: Long): Flow<Set<String>> =
        repository
            .getScanlatorsByMangaIdAsFlow(mangaId)
            .map { it.cleanupAvailableScanlators() }
}
