package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.ServerLibrary
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class ServerLibraryEntry(
    val manga: MangaType,
    val chapterCount: Int,
    val downloadedChapterCount: Int,
    val queuedChapterCount: Int,
)

data class ServerLibraryPage(
    val items: List<ServerLibraryEntry>,
    val totalCount: Int,
    val hasNextPage: Boolean,
)

data class ServerLibraryStorage(
    val downloadBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long,
    val measuredAt: Long,
)

class ServerLibraryQuery {
    private val dirs: ApplicationDirs by injectLazy()

    @RequireAuth
    fun serverLibrary(
        @GraphQLIgnore userId: Int,
        offset: Int = 0,
        limit: Int = 50,
        search: String = "",
        sourceId: Long? = null,
    ): ServerLibraryPage {
        require(offset >= 0 && limit in 1..200) { "offset must be non-negative; limit must be 1..200" }
        val queue = DownloadManager.getStatus().queue.groupingBy { it.mangaId }.eachCount()
        return transaction {
            var condition = MangaTable.inServerLibrary eq true
            if (search.isNotBlank()) condition = condition and (MangaTable.title like "%${search.trim()}%")
            if (sourceId != null) condition = condition and (MangaTable.sourceReference eq sourceId)
            val query = MangaTable.getWithUserData(userId).selectAll().where { condition }
            val total = query.count().toInt()
            val rows = query.orderBy(MangaTable.serverLibraryAddedAt to SortOrder.DESC, MangaTable.id to SortOrder.DESC)
                .limit(limit).offset(offset.toLong()).toList()
            val chapters = ChapterTable.selectAll().where { ChapterTable.manga inList rows.map { it[MangaTable.id].value } }
                .groupBy { it[ChapterTable.manga].value }
            ServerLibraryPage(
                rows.map { row ->
                    val id = row[MangaTable.id].value
                    val mangaChapters = chapters[id].orEmpty()
                    ServerLibraryEntry(
                        MangaType(row), mangaChapters.size,
                        mangaChapters.count { it[ChapterTable.isDownloaded] }, queue[id] ?: 0,
                    )
                },
                total,
                offset.toLong() + rows.size < total,
            )
        }
    }

    @RequireAuth
    fun serverLibraryStorage(@GraphQLIgnore userId: Int): CompletableFuture<ServerLibraryStorage> = future {
        ServerLibrary.requireAdmin(userId)
        val root = Path.of(dirs.downloadsRoot).toAbsolutePath()
        val bytes = if (Files.exists(root)) {
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.mapToLong { Files.size(it) }.sum()
            }
        } else {
            0L
        }
        var existing = root
        while (!Files.exists(existing)) existing = existing.parent ?: error("Download volume not found")
        val store = Files.getFileStore(existing)
        ServerLibraryStorage(bytes, store.usableSpace, store.totalSpace, Instant.now().epochSecond)
    }
}
