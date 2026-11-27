package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.model.table.toDataClass
import uy.kohesive.injekt.injectLazy
import suwayomi.tachidesk.manga.impl.ServerLibrary
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.UserPermission
import java.util.concurrent.CompletableFuture

class ServerLibraryMutation {
    private val updater: IUpdater by injectLazy()
    data class AddServerLibraryMangaInput(val mangaId: Int, val clientMutationId: String? = null)
    data class RemoveServerLibraryMangaInput(
        val mangaId: Int,
        val deleteDownloads: Boolean = false,
        val clientMutationId: String? = null,
    )
    data class ServerLibraryMangaPayload(val manga: MangaType, val clientMutationId: String?)
    data class ServerLibraryDownloadsInput(val chapterIds: List<Int>, val clientMutationId: String? = null)
    data class ServerLibraryDownloadsPayload(val chapterIds: List<Int>, val clientMutationId: String?)

    private fun manga(userId: Int, mangaId: Int): MangaType = transaction {
        MangaType(MangaTable.getWithUserData(userId).selectAll().where { MangaTable.id eq mangaId }.first())
    }

    @RequireAuth
    fun addServerLibraryManga(
        @GraphQLIgnore userId: Int,
        input: AddServerLibraryMangaInput,
    ): CompletableFuture<ServerLibraryMangaPayload> = future {
        ServerLibrary.add(input.mangaId)
        val entry = transaction { MangaTable.selectAll().where { MangaTable.id eq input.mangaId }.first() }
        if (!entry[MangaTable.initialized] || entry[MangaTable.chaptersLastFetchedAt] == 0L) {
            updater.addMangasToQueue(listOf(MangaTable.toDataClass(entry)))
        }
        ServerLibraryMangaPayload(manga(userId, input.mangaId), input.clientMutationId)
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SERVER_LIBRARY)
    fun removeServerLibraryManga(
        @GraphQLIgnore userId: Int,
        input: RemoveServerLibraryMangaInput,
    ): CompletableFuture<ServerLibraryMangaPayload> = future {
        ServerLibrary.remove(userId, input.mangaId, input.deleteDownloads)
        ServerLibraryMangaPayload(manga(userId, input.mangaId), input.clientMutationId)
    }

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun enqueueServerLibraryDownloads(
        @GraphQLIgnore userId: Int,
        input: ServerLibraryDownloadsInput,
    ): CompletableFuture<ServerLibraryDownloadsPayload> = future {
        val pending = ServerLibrary.enqueue(userId, input.chapterIds)
        ServerLibraryDownloadsPayload(pending, input.clientMutationId)
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SERVER_LIBRARY)
    fun deleteServerLibraryDownloads(
        @GraphQLIgnore userId: Int,
        input: ServerLibraryDownloadsInput,
    ): CompletableFuture<ServerLibraryDownloadsPayload> = future {
        ServerLibrary.deleteDownloads(userId, input.chapterIds)
        ServerLibraryDownloadsPayload(input.chapterIds.distinct(), input.clientMutationId)
    }
    @RequireAuth
    fun updateServerLibrary(@GraphQLIgnore userId: Int): Boolean {
        ServerLibrary.requireAdmin(userId)
        val mangas = transaction {
            MangaTable.selectAll().where { MangaTable.inServerLibrary eq true }.map { MangaTable.toDataClass(it) }
        }
        updater.addMangasToQueue(mangas)
        return true
    }

}
