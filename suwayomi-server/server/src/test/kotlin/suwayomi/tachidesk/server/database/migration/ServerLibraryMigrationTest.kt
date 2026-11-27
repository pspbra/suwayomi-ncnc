package suwayomi.tachidesk.server.database.migration

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.manga.impl.ServerLibraryMode
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerLibraryMigrationTest : ApplicationTest() {
    private fun withLegacyDatabase(mode: AuthMode, extraAdmin: Boolean = false, action: (Database) -> Unit) {
        val previousDatabase = TransactionManager.primaryDatabase
        val previousMode = serverConfig.authMode.value
        val database = Database.connect("jdbc:h2:mem:server-library-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver")
        try {
            serverConfig.authMode.value = mode
            transaction(database) {
                exec("CREATE TABLE manga (id INT PRIMARY KEY)")
                exec("CREATE TABLE mangauser (manga INT, user_id INT, in_library BOOLEAN, in_library_at BIGINT)")
                exec("CREATE TABLE chapter (id INT PRIMARY KEY, manga INT, is_downloaded BOOLEAN)")
                exec("CREATE TABLE chapteruser (chapter INT, is_downloaded BOOLEAN, is_download_requested BOOLEAN)")
                exec("CREATE TABLE userroles (user_id INT, role VARCHAR)")
                exec("INSERT INTO manga VALUES (1), (2), (3), (4)")
                exec("INSERT INTO userroles VALUES (7, 'ADMIN'), (1, 'USER')")
                if (extraAdmin) exec("INSERT INTO userroles VALUES (8, 'ADMIN')")
                exec("INSERT INTO mangauser VALUES (1, 1, TRUE, 100), (2, 7, TRUE, 200), (3, 8, TRUE, 300)")
                exec("INSERT INTO chapter VALUES (4, 4, TRUE)")
                exec("INSERT INTO chapteruser VALUES (4, TRUE, TRUE)")
                M0067_ServerLibrary().run()
                M0068_ServerLibraryMode().run()
            }
            action(database)
        } finally {
            serverConfig.authMode.value = previousMode
            TransactionManager.closeAndUnregister(database)
            if (previousDatabase != null) TransactionManager.defaultDatabase = previousDatabase
        }
    }

    private fun library(database: Database): List<Int> = transaction(database) {
        exec("SELECT id FROM manga WHERE in_server_library = TRUE ORDER BY id") { result ->
            buildList { while (result.next()) add(result.getInt(1)) }
        }!!
    }

    @Test
    fun `multi user upgrade seeds only the unique administrator and preserves downloads`() {
        withLegacyDatabase(AuthMode.UI_LOGIN) { database ->
            assertEquals(listOf(2), library(database))
            transaction(database) {
                assertEquals(200L, exec("SELECT server_library_added_at FROM manga WHERE id = 2") { it.next(); it.getLong(1) })
                assertEquals(true, exec("SELECT is_downloaded FROM chapter WHERE id = 4") { it.next(); it.getBoolean(1) })
                assertEquals(3, exec("SELECT COUNT(*) FROM mangauser WHERE in_library = TRUE") { it.next(); it.getInt(1) })
            }
        }
    }

    @Test
    fun `ambiguous administrators do not merge other users libraries`() {
        withLegacyDatabase(AuthMode.UI_LOGIN, extraAdmin = true) { database ->
            assertEquals(emptyList(), library(database))
            ServerLibraryMode.reconcile(AuthMode.NONE)
            assertEquals(emptyList(), library(database))
        }
    }

    @Test
    fun `single user upgrade uses the original bookshelf`() {
        withLegacyDatabase(AuthMode.NONE) { database ->
            assertEquals(listOf(1), library(database))
        }
    }

    @Test
    fun `mode changes merge the previous library and repeated startup does not restore removed resources`() {
        withLegacyDatabase(AuthMode.NONE) { database ->
            transaction(database) {
                exec("UPDATE manga SET in_server_library = FALSE WHERE id = 1")
            }
            ServerLibraryMode.reconcile(AuthMode.NONE)
            assertEquals(emptyList(), library(database))
            ServerLibraryMode.reconcile(AuthMode.UI_LOGIN)
            assertEquals(listOf(1), library(database))
            ServerLibraryMode.reconcile(AuthMode.NONE)
            assertEquals(listOf(1, 2), library(database))
            transaction(database) {
                exec("UPDATE manga SET in_server_library = FALSE WHERE id = 2")
            }
            ServerLibraryMode.reconcile(AuthMode.NONE)
            assertEquals(listOf(1), library(database))
        }
    }
}
