package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.hasPermission
import java.io.IOException
import java.time.Instant

/** Server ownership is independent of every user's library and reading state. */
object ServerLibrary {
    // Fixed stripes cannot expire while a download or removal is using its lock.
    private val locks = Array(256) { Mutex() }

    suspend fun <T> withMangaLock(mangaId: Int, action: suspend () -> T): T =
        locks[Math.floorMod(mangaId, locks.size)].withLock { action() }

    private suspend fun <T> withMangaLocks(mangaIds: List<Int>, action: suspend () -> T): T {
        val selected = mangaIds.map { Math.floorMod(it, locks.size) }.distinct().sorted().map { locks[it] }
        val acquired = mutableListOf<Mutex>()
        try {
            selected.forEach {
                it.lock()
                acquired.add(it)
            }
            return action()
        } finally {
            acquired.asReversed().forEach { it.unlock() }
        }
    }

    fun requireAdmin(userId: Int) {
        val admin = transaction {
            UserRolesTable.selectAll().where { UserRolesTable.user eq userId }
                .any { it[UserRolesTable.role].equals(UserRole.ADMIN.name, ignoreCase = true) }
        }
        if (!admin) throw ForbiddenException()
    }

    fun requireManagement(userId: Int) {
        if (!hasPermission(userId, UserPermission.MANAGE_SERVER_LIBRARY)) throw ForbiddenException()
    }

    fun requireMembership(mangaIds: List<Int>) {
        val ids = mangaIds.distinct()
        val count = transaction {
            MangaTable.selectAll().where { (MangaTable.id inList ids) and (MangaTable.inServerLibrary eq true) }.count()
        }
        require(count == ids.size.toLong()) { "Manga must be added to the server library first" }
    }

    suspend fun add(mangaId: Int) = withMangaLock(mangaId) {
        transaction {
            require(MangaTable.selectAll().where { MangaTable.id eq mangaId }.any()) { "Manga not found" }
            MangaTable.update({ (MangaTable.id eq mangaId) and (MangaTable.inServerLibrary eq false) }) {
                it[inServerLibrary] = true
                it[serverLibraryAddedAt] = Instant.now().epochSecond
            }
        }
        Library.handleMangaThumbnail(mangaId)
    }

    suspend fun remove(userId: Int, mangaId: Int, deleteDownloads: Boolean) {
        requireManagement(userId)
        withMangaLock(mangaId) {
            val chapters = transaction {
                require(MangaTable.selectAll().where { MangaTable.id eq mangaId }.any()) { "Manga not found" }
                ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.map { it[ChapterTable.id].value }
            }
            // The same lock covers the complete physical write in Downloader.
            DownloadManager.dequeue(chapters)
            if (deleteDownloads) deleteFiles(mangaId, chapters)
            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[inServerLibrary] = false
                    it[serverLibraryAddedAt] = 0
                }
            }
            // Keep manga/chapter rows and personal reading history, even after removal.
        }
    }

    suspend fun enqueue(userId: Int, chapterIds: List<Int>): List<Int> {
        if (!hasPermission(userId, UserPermission.DOWNLOAD_CHAPTERS)) throw ForbiddenException()
        val ids = chapterIds.distinct()
        val chapters = transaction {
            ChapterTable.selectAll().where { ChapterTable.id inList ids }.map {
                it[ChapterTable.id].value to it[ChapterTable.manga].value
            }
        }
        require(chapters.size == ids.size) { "Chapter not found" }
        return withMangaLocks(chapters.map { it.second }) {
            requireMembership(chapters.map { it.second })
            val pending = transaction {
                ChapterTable.selectAll().where { (ChapterTable.id inList ids) and (ChapterTable.isDownloaded eq false) }
                    .map { it[ChapterTable.id].value }
            }
            DownloadManager.enqueue(pending)
            pending
        }
    }

    suspend fun deleteDownloads(userId: Int, chapterIds: List<Int>) {
        requireManagement(userId)
        val ids = chapterIds.distinct()
        val chapters = transaction {
            ChapterTable.selectAll().where { ChapterTable.id inList ids }.map {
                it[ChapterTable.id].value to it[ChapterTable.manga].value
            }
        }
        require(chapters.size == ids.size) { "Chapter not found" }
        withMangaLocks(chapters.map { it.second }) {
            DownloadManager.dequeue(ids)
            // Also permits cleaning retained files of a removed server-library entry.
            chapters.groupBy({ it.second }, { it.first }).forEach { (mangaId, chapterIds) ->
                deleteFiles(mangaId, chapterIds)
            }
        }
    }

    private suspend fun deleteFiles(mangaId: Int, chapterIds: List<Int>) {
        chapterIds.forEach { chapterId ->
            if (!ChapterDownloadHelper.delete(mangaId, chapterId)) {
                throw IOException("Could not delete downloaded chapter $chapterId")
            }
            // Commit each successful deletion so partial failures do not claim removed files exist.
            transaction {
                ChapterTable.update({ ChapterTable.id eq chapterId }) { it[isDownloaded] = false }
                ChapterUserTable.update({ ChapterUserTable.chapter eq chapterId }) {
                    it[isDownloaded] = false
                    it[isDownloadRequested] = false
                }
            }
        }
    }
}
