package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.manga.impl.ServerLibrary
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class DownloadQuery {
    @RequireAuth
    fun downloadStatus(@GraphQLIgnore userId: Int): CompletableFuture<DownloadStatus> =
        future {
            ServerLibrary.requireAdmin(userId)
            DownloadStatus(DownloadManager.getStatus())
        }
}
