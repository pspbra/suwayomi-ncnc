@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.types.ChapterDownloadReorder
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.ServerLibrary
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.DEQUEUED
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.UserPermission
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class DownloadMutation {
    data class DeleteDownloadedChaptersInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    data class DeleteDownloadedChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>,
    )

    @RequireAuth
    fun deleteDownloadedChapters(
        @GraphQLIgnore
        userId: Int,
        input: DeleteDownloadedChaptersInput,
    ): CompletableFuture<DeleteDownloadedChaptersPayload?> {
        val (clientMutationId, chapters) = input

        return future {
            Chapter.deleteDownloadedChapters(userId, chapters)

            DeleteDownloadedChaptersPayload(
                clientMutationId = clientMutationId,
                chapters =
                    transaction {
                        ChapterTable
                            .getWithUserData(userId)
                            .selectAll()
                            .where { ChapterTable.id inList chapters }
                            .map { ChapterType(it) }
                    },
            )
        }
    }

    data class DeleteDownloadedChapterInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DeleteDownloadedChapterPayload(
        val clientMutationId: String?,
        val chapters: ChapterType,
    )

    @RequireAuth
    fun deleteDownloadedChapter(
        @GraphQLIgnore
        userId: Int,
        input: DeleteDownloadedChapterInput,
    ): CompletableFuture<DeleteDownloadedChapterPayload?> {
        val (clientMutationId, chapter) = input

        return future {
            Chapter.deleteDownloadedChapters(userId, listOf(chapter))

            DeleteDownloadedChapterPayload(
                clientMutationId = clientMutationId,
                chapters =
                    transaction {
                        ChapterType(
                            ChapterTable
                                .getWithUserData(userId)
                                .selectAll()
                                .where { ChapterTable.id eq chapter }
                                .first(),
                        )
                    },
            )
        }
    }

    data class EnqueueChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    data class EnqueueChapterDownloadsPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun enqueueChapterDownloads(
        @GraphQLIgnore userId: Int,
        input: EnqueueChapterDownloadsInput,
    ): CompletableFuture<EnqueueChapterDownloadsPayload?> = future {
        throw IllegalArgumentException("Use enqueueServerLibraryDownloads from the server library")
    }

    data class EnqueueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class EnqueueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun enqueueChapterDownload(
        @GraphQLIgnore userId: Int,
        input: EnqueueChapterDownloadInput,
    ): CompletableFuture<EnqueueChapterDownloadPayload?> = future {
        throw IllegalArgumentException("Use enqueueServerLibraryDownloads from the server library")
    }

    data class DequeueChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    data class DequeueChapterDownloadsPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun dequeueChapterDownloads(
        @GraphQLIgnore userId: Int,
        input: DequeueChapterDownloadsInput,
    ): CompletableFuture<DequeueChapterDownloadsPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.dequeue(input.ids)
        DequeueChapterDownloadsPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }

    data class DequeueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DequeueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun dequeueChapterDownload(
        @GraphQLIgnore userId: Int,
        input: DequeueChapterDownloadInput,
    ): CompletableFuture<DequeueChapterDownloadPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.dequeue(listOf(input.id))
        DequeueChapterDownloadPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }

    data class StartDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StartDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun startDownloader(
        @GraphQLIgnore userId: Int,
        input: StartDownloaderInput,
    ): CompletableFuture<StartDownloaderPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.start()
        StartDownloaderPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }

    data class StopDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StopDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun stopDownloader(
        @GraphQLIgnore userId: Int,
        input: StopDownloaderInput,
    ): CompletableFuture<StopDownloaderPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.stop()
        StopDownloaderPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }

    data class ClearDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class ClearDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun clearDownloader(
        @GraphQLIgnore userId: Int,
        input: ClearDownloaderInput,
    ): CompletableFuture<ClearDownloaderPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.clear()
        ClearDownloaderPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }

    data class ReorderChapterDownloadInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val to: Int,
    )

    data class ReorderChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun reorderChapterDownload(
        @GraphQLIgnore userId: Int,
        input: ReorderChapterDownloadInput,
    ): CompletableFuture<ReorderChapterDownloadPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.reorder(input.chapterId, input.to)
        ReorderChapterDownloadPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }

    data class ReorderChapterDownloadsInput(
        val clientMutationId: String? = null,
        val reorders: List<ChapterDownloadReorder>,
    )

    @RequireAuth
    @RequirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
    fun reorderChapterDownloads(
        @GraphQLIgnore userId: Int,
        input: ReorderChapterDownloadsInput,
    ): CompletableFuture<ReorderChapterDownloadPayload?> = future {
        ServerLibrary.requireAdmin(userId)
        DownloadManager.reorder(input.reorders)
        ReorderChapterDownloadPayload(input.clientMutationId, DownloadStatus(DownloadManager.getStatus()))
    }
}
