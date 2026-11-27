package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.manga.impl.ServerLibrary
import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater: IUpdater by injectLazy()

    @GraphQLDeprecated("Replaced with libraryUpdateStatus", ReplaceWith("libraryUpdateStatus"))
    @RequireAuth
    fun updateStatus(@GraphQLIgnore userId: Int): CompletableFuture<UpdateStatus> =
        future {
            ServerLibrary.requireAdmin(userId)
            UpdateStatus(updater.status.first())
        }

    @RequireAuth
    fun libraryUpdateStatus(@GraphQLIgnore userId: Int): CompletableFuture<LibraryUpdateStatus> =
        future {
            ServerLibrary.requireAdmin(userId)
            LibraryUpdateStatus(updater.getStatus())
        }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    @RequireAuth
    fun lastUpdateTimestamp(): LastUpdateTimestampPayload = LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
}
