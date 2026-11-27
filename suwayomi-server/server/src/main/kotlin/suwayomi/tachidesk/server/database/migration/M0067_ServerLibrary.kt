package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.serverConfig

/** Preserve existing libraries and downloads while moving ownership to the server. */
@Suppress("ClassName", "unused")
class M0067_ServerLibrary : SQLMigration() {
    private val owner =
        if (serverConfig.authMode.value == AuthMode.UI_LOGIN) {
            "(SELECT MIN(user_id) FROM userroles WHERE UPPER(role) = 'ADMIN' HAVING COUNT(DISTINCT user_id) = 1)"
        } else {
            "1"
        }

    override val sql: String =
        """
        ALTER TABLE manga ADD COLUMN in_server_library BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE manga ADD COLUMN server_library_added_at BIGINT NOT NULL DEFAULT 0;
        ALTER TABLE chapter ADD COLUMN pages_fetched_at BIGINT NOT NULL DEFAULT 0;
        UPDATE manga SET in_server_library = TRUE
        WHERE id IN (SELECT manga FROM mangauser WHERE in_library = TRUE AND user_id = $owner);
        UPDATE manga SET server_library_added_at = COALESCE(
            (SELECT MIN(u.in_library_at) FROM mangauser u WHERE u.manga = manga.id AND u.in_library = TRUE AND u.user_id = $owner), 0)
        WHERE in_server_library = TRUE;
        UPDATE chapteruser SET is_download_requested = FALSE, is_downloaded = FALSE;
        CREATE INDEX manga_server_library_idx ON manga(in_server_library, server_library_added_at);
        """.trimIndent()
}
