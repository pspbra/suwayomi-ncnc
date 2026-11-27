package suwayomi.tachidesk.manga.impl

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.types.AuthMode

/** Add the previous mode's library without replacing existing server resources or personal data. */
object ServerLibraryMode {
    fun reconcile(mode: AuthMode) {
        val multiUser = mode == AuthMode.UI_LOGIN
        transaction {
            val previous = exec("SELECT multi_user FROM serverlibrarymode WHERE id = 1 FOR UPDATE") { result ->
                check(result.next()) { "Server library mode has not been initialized" }
                result.getBoolean(1)
            } ?: error("Server library mode has not been initialized")
            if (previous == multiUser) return@transaction

            val owner = if (previous) {
                "(SELECT MIN(user_id) FROM userroles WHERE UPPER(role) = 'ADMIN' HAVING COUNT(DISTINCT user_id) = 1)"
            } else {
                "1"
            }
            exec(
                """
                UPDATE manga SET in_server_library = TRUE,
                    server_library_added_at = COALESCE(
                        (SELECT MIN(u.in_library_at) FROM mangauser u
                         WHERE u.manga = manga.id AND u.in_library = TRUE AND u.user_id = $owner), 0)
                WHERE in_server_library = FALSE
                    AND id IN (SELECT manga FROM mangauser WHERE in_library = TRUE AND user_id = $owner)
                """.trimIndent(),
            )
            exec("UPDATE serverlibrarymode SET multi_user = $multiUser WHERE id = 1")
        }
    }
}
