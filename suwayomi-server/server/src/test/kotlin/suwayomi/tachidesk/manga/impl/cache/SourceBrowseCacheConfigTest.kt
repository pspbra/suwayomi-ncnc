package suwayomi.tachidesk.manga.impl.cache

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.io.TempDir
import xyz.nulldev.ts.config.CONFIG_PREFIX
import xyz.nulldev.ts.config.ConfigManager
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SourceBrowseCacheConfigTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `startup adds missing ttl removes obsolete settings and preserves custom ttl`() {
        val property = "$CONFIG_PREFIX.server.rootDir"
        val original = System.getProperty(property)
        try {
            System.setProperty(property, directory.toString())
            val file = directory.resolve("server.conf").toFile()
            file.writeText(
                """
                server.sourceBrowseCachePopularTtl = "1h"
                server.sourceBrowseCacheLatestTtl = "2h"
                server.sourceBrowseCacheSearchTtl = "3h"
                server.port = 9876
                """.trimIndent(),
            )
            ConfigManager().updateUserConfig { this }
            val updated = ConfigFactory.parseFile(file)
            assertEquals(java.time.Duration.ofHours(12), updated.getDuration("server.sourceBrowseCacheTtl"))
            assertEquals(9876, updated.getInt("server.port"))
            listOf("Popular", "Latest", "Search").forEach {
                assertFalse(updated.hasPath("server.sourceBrowseCache${it}Ttl"))
            }

            file.writeText("server.sourceBrowseCacheTtl = \"6h\"\nserver.port = 9876")
            ConfigManager().updateUserConfig { this }
            assertEquals(
                java.time.Duration.ofHours(6),
                ConfigFactory.parseFile(file).getDuration("server.sourceBrowseCacheTtl"),
            )
        } finally {
            if (original == null) System.clearProperty(property) else System.setProperty(property, original)
        }
    }
}
