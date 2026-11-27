package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.serverConfig

/** Persist the last mode so changes made while the server is stopped are handled too. */
@Suppress("ClassName", "unused")
class M0068_ServerLibraryMode : SQLMigration() {
    override val sql: String =
        """
        CREATE TABLE serverlibrarymode (id INT PRIMARY KEY, multi_user BOOLEAN NOT NULL);
        INSERT INTO serverlibrarymode (id, multi_user) VALUES (1, ${serverConfig.authMode.value == AuthMode.UI_LOGIN});
        """.trimIndent()
}
