package eu.kanade.tachiyomi.source.suwayomi

import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Values of the GraphQL `FetchSourceMangaType` enum. */
internal const val BROWSE_TYPE_POPULAR = "POPULAR"
internal const val BROWSE_TYPE_LATEST = "LATEST"
internal const val BROWSE_TYPE_SEARCH = "SEARCH"

class SuwayomiServerApi(
    private val preferences: SuwayomiServerPreferences,
    baseClient: OkHttpClient,
) {
    val client: OkHttpClient =
        baseClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(SuwayomiAuthInterceptor(preferences))
            .build()

    fun mangaId(url: String): Int = preferences.mangaId(url)

    suspend fun validate(): Int =
        execute("query ValidateServer { mangas(condition: { inLibrary: true }, first: 1) { totalCount } }")
            .objectAt("mangas")
            .int("totalCount")

    /** Personal favorites are resolved by the server from the authenticated session. */
    suspend fun libraryManga(): List<ServerManga> {
        val account = preferences.accountKey
        val result = mutableListOf<ServerManga>()
        do {
            val page = execute(
                """query LibraryManga(${D}offset: Int!) {
                    mangas(condition: { inLibrary: true }, first: 200, offset: ${D}offset, order: { by: ID }) {
                        nodes { id title artist author description genre status thumbnailUrl inLibrary inLibraryAt inServerLibrary source { displayName } }
                    }
                }""",
                buildJsonObject { put("offset", result.size) },
                account,
            ).objectAt("mangas").array("nodes")
            result += page.map { it.jsonObject.toServerManga(account) }
        } while (page.size == 200)
        return result
    }

    /**
     * One page of the server's shared collection.
     *
     * Unlike the personal library this carries the shared download counters: the number of chapters,
     * how many of them are already downloaded on the server and how many are waiting in its queue.
     * The counters are what the browse page shows, because downloads belong to the server.
     */
    suspend fun serverLibrary(page: Int, search: String): ServerLibraryPage {
        val account = preferences.accountKey
        val data = execute(
            """query ServerLibrary(${D}offset: Int!, ${D}search: String!) {
                serverLibrary(offset: ${D}offset, limit: 40, search: ${D}search) {
                    hasNextPage
                    items {
                        manga { id title artist author description genre status thumbnailUrl inLibrary inLibraryAt inServerLibrary source { displayName } }
                        chapterCount downloadedChapterCount queuedChapterCount
                    }
                }
            }""",
            buildJsonObject {
                put("offset", (page.coerceAtLeast(1) - 1) * 40)
                put("search", search)
            },
            account,
        ).objectAt("serverLibrary")
        return ServerLibraryPage(
            data.array("items").map { item ->
                val value = item.jsonObject
                ServerLibraryItem(
                    manga = value.objectAt("manga").toServerManga(account),
                    chapterCount = value.int("chapterCount"),
                    downloadedChapterCount = value.int("downloadedChapterCount"),
                    queuedChapterCount = value.int("queuedChapterCount"),
                )
            },
            data.boolean("hasNextPage"),
        )
    }

    suspend fun addServerLibraryManga(id: Int) {
        execute(
            """mutation AddServerManga(${D}id: Int!) {
                addServerLibraryManga(input: { mangaId: ${D}id }) { manga { id inServerLibrary } }
            }""",
            buildJsonObject { put("id", id) },
        )
    }

    suspend fun manga(
        id: Int,
        account: String = preferences.accountKey,
    ): ServerManga {
        val data =
            execute(
                """
                query Manga(${D}id: Int!) {
                    manga(id: ${D}id) { id title artist author description genre status thumbnailUrl inLibrary inLibraryAt inServerLibrary source { displayName } }
                }
                """.trimIndent(),
                buildJsonObject { put("id", id) },
                account = account,
            )
        return data.objectAt("manga").toServerManga(account)
    }

    suspend fun chapters(
        mangaId: Int,
        account: String = preferences.accountKey,
    ): List<ServerChapter> {
        val data =
            execute(
                """
                query Chapters(${D}id: Int!) {
                    chapters(condition: { mangaId: ${D}id }, order: { by: SOURCE_ORDER, byType: DESC }) {
                        nodes { id mangaId sourceOrder name chapterNumber uploadDate scanlator isRead lastPageRead lastReadAt isDownloaded }
                    }
                }
                """.trimIndent(),
                buildJsonObject { put("id", mangaId) },
                account = account,
            )
        return data.objectAt("chapters").array("nodes").map { node ->
            val value = node.jsonObject
            ServerChapter(
                id = value.int("id"),
                localUrl = "account/$account/${value.int("mangaId")}/${value.int("id")}",
                mangaId = value.int("mangaId"),
                sourceOrder = value.int("sourceOrder"),
                name = value.string("name"),
                chapterNumber = value.string("chapterNumber").toFloatOrNull() ?: -1f,
                uploadDate = value.long("uploadDate"),
                scanlator = value.nullableString("scanlator"),
                isRead = value.boolean("isRead"),
                lastPageRead = value.int("lastPageRead"),
                lastReadAt = value.long("lastReadAt"),
                isDownloaded = value.boolean("isDownloaded"),
            )
        }
    }

    suspend fun chapterProgress(id: Int): ServerReadingHistory {
        val value =
            execute(
                "query Progress(${D}id: Int!) { chapter(id: ${D}id) { id mangaId isRead lastPageRead lastReadAt } }",
                buildJsonObject { put("id", id) },
            ).objectAt("chapter")
        return ServerReadingHistory(
            value.int("id"),
            value.int("mangaId"),
            value.boolean("isRead"),
            value.int("lastPageRead"),
            value.long("lastReadAt"),
        )
    }

    suspend fun updateProgress(
        account: String,
        chapterId: Int,
        page: Int,
        read: Boolean,
    ): ServerReadingHistory {
        val value =
            execute(
                """mutation Progress(${D}id: Int!, ${D}page: Int!, ${D}read: Boolean!) {
                updateChapter(input: { id: ${D}id, patch: { lastPageRead: ${D}page, isRead: ${D}read } }) {
                    chapter { id mangaId isRead lastPageRead lastReadAt }
                }
            }""",
                buildJsonObject {
                    put("id", chapterId)
                    put("page", page)
                    put("read", read)
                },
                account,
            ).objectAt("updateChapter").objectAt("chapter")
        return ServerReadingHistory(
            value.int("id"),
            value.int("mangaId"),
            value.boolean("isRead"),
            value.int("lastPageRead"),
            value.long("lastReadAt"),
        )
    }

    suspend fun readingHistory(offset: Int): List<ServerReadingHistory> {
        val data =
            execute(
                """query History(${D}offset: Int!) {
                chapters(first: 100, offset: ${D}offset, filter: { lastReadAt: { greaterThan: "0" } },
                    order: { by: LAST_READ_AT, byType: DESC }) {
                    nodes { id mangaId isRead lastPageRead lastReadAt }
                }
            }""",
                buildJsonObject { put("offset", offset) },
            )
        return data.objectAt("chapters").array("nodes").map {
            val value = it.jsonObject
            ServerReadingHistory(
                value.int("id"),
                value.int("mangaId"),
                value.boolean("isRead"),
                value.int("lastPageRead"),
                value.long("lastReadAt"),
            )
        }
    }

    suspend fun pages(
        chapterId: Int,
        account: String = preferences.accountKey,
    ): List<String> {
        val data =
            execute(
                """mutation Pages(${D}id: Int!) { fetchChapterPages(input: { chapterId: ${D}id }) { pages } }""",
                buildJsonObject { put("id", chapterId) },
                account = account,
            )
        return data.objectAt("fetchChapterPages").array("pages").map { it.jsonPrimitive.content }
    }

    suspend fun sources(): List<ServerSource> {
        val data =
            execute(
                """query Sources { sources(first: 1000, order: { by: NAME }) {
                nodes { id name displayName lang iconUrl supportsLatest extension { pkgName } }
            } }""",
            )
        return data
            .objectAt("sources")
            .array("nodes")
            .map { node ->
                val value = node.jsonObject
                ServerSource(
                    id = value.string("id"),
                    name = value.string("displayName").ifBlank { value.string("name") },
                    lang = value.string("lang"),
                    iconUrl = absoluteUrl(value.string("iconUrl")),
                    supportsLatest = value.boolean("supportsLatest"),
                    extensionPackage = value.objectAt("extension").string("pkgName"),
                )
            }.filterNot { it.lang == "localsourcelang" }
    }

    suspend fun extensions(
        offset: Int = 0,
        limit: Int = 100,
    ): List<ServerExtension> {
        val data =
            execute(
                """
                query Extensions(${D}offset: Int!, ${D}limit: Int!) {
                    extensions(first: ${D}limit, offset: ${D}offset, order: { by: NAME }) {
                        nodes { pkgName name lang iconUrl versionName isInstalled hasUpdate isObsolete }
                    }
                }
                """.trimIndent(),
                buildJsonObject {
                    put("offset", offset)
                    put("limit", limit)
                },
            )
        return data.objectAt("extensions").array("nodes").map { node ->
            val value = node.jsonObject
            ServerExtension(
                packageName = value.string("pkgName"),
                name = value.string("name"),
                lang = value.string("lang"),
                iconUrl = absoluteUrl(value.string("iconUrl")),
                versionName = value.string("versionName"),
                isInstalled = value.boolean("isInstalled"),
                hasUpdate = value.boolean("hasUpdate"),
                isObsolete = value.boolean("isObsolete"),
            )
        }
    }

    suspend fun refreshExtensions() {
        execute("mutation RefreshExtensions { fetchExtensions(input: {}) { clientMutationId } }")
    }

    suspend fun setExtensionInstalled(
        packageName: String,
        install: Boolean,
        update: Boolean = false,
    ) {
        execute(
            """
            mutation UpdateExtension(${D}id: String!, ${D}install: Boolean!, ${D}update: Boolean!, ${D}uninstall: Boolean!) {
                updateExtension(input: { id: ${D}id, patch: { install: ${D}install, update: ${D}update, uninstall: ${D}uninstall } }) {
                    extension { pkgName isInstalled hasUpdate }
                }
            }
            """.trimIndent(),
            buildJsonObject {
                put("id", packageName)
                put("install", install)
                put("update", update)
                put("uninstall", !install && !update)
            },
        )
    }

    // Downloads belong to the server: they run on it, are shared by every account and are always
    // requested for chapters of the server library. Only the personal shelf downloads to the device.

    suspend fun currentUser(): ServerUser {
        val value =
            execute(
                "query CurrentUser { user { id username roles permissions } }",
            ).objectAt("user")
        return ServerUser(
            id = value.int("id"),
            username = value.string("username"),
            roles = value.array("roles").map { it.jsonPrimitive.content },
            permissions = value.array("permissions").map { it.jsonPrimitive.content },
        )
    }

    /**
     * The shared download queue.
     *
     * The server only exposes it to administrators, so a rejected request is a normal answer for a
     * regular account and is reported as `null` instead of an error. Callers fall back to the
     * per-chapter `isDownloaded` flag in that case.
     */
    suspend fun downloadStatus(): ServerDownloadStatus? =
        try {
            val value =
                execute(
                    """query DownloadStatus {
                        downloadStatus {
                            state
                            queue { state progress tries position chapter { id mangaId } }
                        }
                    }""",
                ).objectAt("downloadStatus")
            ServerDownloadStatus(
                started = value.string("state") == "STARTED",
                queue =
                    value.array("queue").map { item ->
                        val entry = item.jsonObject
                        val chapter = entry.objectAt("chapter")
                        ServerDownloadItem(
                            chapterId = chapter.int("id"),
                            mangaId = chapter.int("mangaId"),
                            state = entry.string("state"),
                            progress = entry.string("progress").toFloatOrNull() ?: 0f,
                            tries = entry.int("tries"),
                            position = entry.int("position"),
                        )
                    },
            )
        } catch (_: SuwayomiForbiddenException) {
            null
        }

    /** Returns the chapter ids the server accepted; already downloaded chapters are filtered out. */
    suspend fun enqueueServerLibraryDownloads(chapterIds: List<Int>): List<Int> {
        if (chapterIds.isEmpty()) return emptyList()
        val data =
            execute(
                """mutation EnqueueServerDownloads(${D}ids: [Int!]!) {
                    enqueueServerLibraryDownloads(input: { chapterIds: ${D}ids }) { chapterIds }
                }""",
                buildJsonObject { put("chapterIds", chapterIds.toJsonArray()) },
            )
        return data.objectAt("enqueueServerLibraryDownloads").array("chapterIds").map { it.jsonPrimitive.int }
    }

    /** Cancels queued downloads. The server only allows this for administrators. */
    suspend fun dequeueChapterDownloads(chapterIds: List<Int>) {
        if (chapterIds.isEmpty()) return
        execute(
            """mutation DequeueDownloads(${D}ids: [Int!]!) {
                dequeueChapterDownloads(input: { ids: ${D}ids }) { clientMutationId }
            }""",
            buildJsonObject { put("ids", chapterIds.toJsonArray()) },
        )
    }

    /** Deletes the downloaded files for every account. Requires the manage-server-library permission. */
    suspend fun deleteServerLibraryDownloads(chapterIds: List<Int>) {
        if (chapterIds.isEmpty()) return
        execute(
            """mutation DeleteServerDownloads(${D}ids: [Int!]!) {
                deleteServerLibraryDownloads(input: { chapterIds: ${D}ids }) { chapterIds }
            }""",
            buildJsonObject { put("chapterIds", chapterIds.toJsonArray()) },
        )
    }

    private fun List<Int>.toJsonArray(): JsonArray = JsonArray(map { JsonPrimitive(it) })

    suspend fun browseSource(        sourceId: String,
        page: Int = 1,
        query: String = "",
        type: String = if (query.isBlank()) BROWSE_TYPE_POPULAR else BROWSE_TYPE_SEARCH,
    ): ServerMangaPage {
        val account = preferences.accountKey
        val data =
            execute(
                """
                mutation BrowseSource(${D}source: LongString!, ${D}page: Int!, ${D}query: String!, ${D}type: FetchSourceMangaType!) {
                    fetchSourceManga(input: { source: ${D}source, page: ${D}page, query: ${D}query, type: ${D}type }) {
                        hasNextPage
                        mangas { id title artist author description genre status thumbnailUrl inLibrary inLibraryAt inServerLibrary source { displayName } }
                    }
                }
                """.trimIndent(),
                buildJsonObject {
                    put("source", sourceId)
                    put("page", page)
                    put("query", query)
                    put("type", type)
                },
                account = account,
            ).objectAt("fetchSourceManga")
        return ServerMangaPage(
            mangas = data.array("mangas").map { it.jsonObject.toServerManga(account) },
            hasNextPage = data.boolean("hasNextPage"),
        )
    }

    suspend fun addMangaToLibrary(id: Int, account: String = preferences.accountKey) {
        execute(
            """mutation AddManga(${D}id: Int!) { updateManga(input: { id: ${D}id, patch: { inLibrary: true } }) { manga { id } } }""",
            buildJsonObject { put("id", id) },
            account,
        )
    }

    suspend fun removeMangaFromLibrary(id: Int, account: String = preferences.accountKey) {
        execute(
            """mutation RemoveManga(${D}id: Int!) { updateManga(input: { id: ${D}id, patch: { inLibrary: false } }) { manga { id inLibrary } } }""",
            buildJsonObject { put("id", id) },
            account,
        )
    }

    /** Force Suwayomi to fetch fresh manga metadata and chapters from the source extension. */
    suspend fun refreshManga(id: Int) {
        execute(
            """
            mutation RefreshManga(${D}id: Int!, ${D}fetchManga: Boolean!, ${D}fetchChapters: Boolean!) {
                fetchMangaAndChapters(
                    input: { id: ${D}id, fetchManga: ${D}fetchManga, fetchChapters: ${D}fetchChapters }
                ) {
                    manga @include(if: ${D}fetchManga) { id }
                    chapters @include(if: ${D}fetchChapters) { id }
                }
            }
            """.trimIndent(),
            buildJsonObject {
                put("id", id)
                put("fetchManga", true)
                put("fetchChapters", true)
            },
        )
    }

    fun absoluteUrl(path: String): String =
        when {
            path.isBlank() -> ""
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> preferences.serverUrl + "/" + path.trimStart('/')
        }

    private suspend fun execute(
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
        account: String = preferences.accountKey,
    ): JsonObject {
        check(preferences.isConfigured) { "请先配置 Suwayomi 服务器" }
        val payload =
            buildJsonObject {
                put("query", query)
                put("variables", variables)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request =
            Request
                .Builder()
                .url("${preferences.serverUrl}/api/graphql")
                .tag(SuwayomiAccount::class.java, SuwayomiAccount(account))
                .post(payload)
                .build()
        val root =
            client.newCall(request).awaitSuccess().use { response ->
                Json.parseToJsonElement(response.body.string()).jsonObject
            }
        check(account == preferences.accountKey) { "账号已切换，请重新打开页面" }
        val errors = root["errors"] as? JsonArray
        if (!errors.isNullOrEmpty()) {
            val message = errors.first().jsonObject.errorMessage()
            if (message.equals("Forbidden", ignoreCase = true)) throw SuwayomiForbiddenException()
            throw IllegalStateException(message)
        }
        return root["data"]?.jsonObject ?: error("Suwayomi 返回了空数据")
    }

    private fun JsonObject.toServerManga(account: String) =
        ServerManga(
            id = int("id"),
            localUrl = "account/$account/${int("id")}",
            title = string("title"),
            artist = nullableString("artist"),
            author = nullableString("author"),
            description = nullableString("description"),
            genres = (get("genre") as? JsonArray).orEmpty().map { it.jsonPrimitive.content },
            status = string("status"),
            thumbnailUrl = absoluteUrl(nullableString("thumbnailUrl").orEmpty()),
            inLibrary = boolean("inLibrary"),
            inServerLibrary = boolean("inServerLibrary"),
            inLibraryAt = long("inLibraryAt"),
            sourceName = (get("source") as? JsonObject)?.nullableString("displayName"),
        )

    /**
     * When a source fails on the server, Suwayomi wraps the JVM exception in a graphql-java message
     * that carries the whole stack trace. Keep only the reason, otherwise the UI renders thousands
     * of frames:
     *
     * `Exception while fetching data (/fetchSourceManga) : Connection reset\r\n\r\njava.net.Sock...`
     */
    private fun JsonObject.errorMessage(): String {
        val firstLine =
            get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
                .lineSequence()
                .map(String::trim)
                .firstOrNull(String::isNotEmpty)
                .orEmpty()
        // Drop the "Exception while fetching data (<path>) : " wrapper when present.
        val reason = firstLine.substringAfter(") : ", firstLine)
        return reason.take(MAX_ERROR_LENGTH).ifBlank { "Suwayomi 请求失败" }
    }

    private fun JsonObject.objectAt(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.nullableString(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.intOrNull ?: getValue(key).jsonPrimitive.int

    private fun JsonObject.long(key: String): Long = get(key)?.jsonPrimitive?.longOrNull ?: 0L

    private fun JsonObject.boolean(key: String): Boolean = get(key)?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

    private companion object {
        const val D = "$"
        const val MAX_ERROR_LENGTH = 200
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class ServerManga(
    val id: Int,
    val localUrl: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val status: String,
    val thumbnailUrl: String,
    val inLibrary: Boolean,
    val inServerLibrary: Boolean,
    val inLibraryAt: Long,
    val sourceName: String?,
)

data class ServerChapter(
    val id: Int,
    val localUrl: String,
    val mangaId: Int,
    val sourceOrder: Int,
    val name: String,
    val chapterNumber: Float,
    val uploadDate: Long,
    val scanlator: String?,
    val isRead: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val isDownloaded: Boolean,
)

/**
 * The account the client is signed in with.
 *
 * Downloading needs `DOWNLOAD_CHAPTERS`, the shared queue is administration only and deleting the
 * shared files needs `MANAGE_SERVER_LIBRARY`, so the UI has to know which of them apply.
 */
data class ServerUser(
    val id: Int,
    val username: String,
    val roles: List<String>,
    val permissions: List<String>,
) {
    val isAdmin: Boolean get() = roles.any { it.equals("ADMIN", ignoreCase = true) }

    val canDownload: Boolean get() = isAdmin || permissions.has("DOWNLOAD_CHAPTERS")

    val canManageDownloads: Boolean get() = isAdmin || permissions.has("MANAGE_SERVER_LIBRARY")

    /** The queue of the server is only readable by administrators. */
    val canSeeQueue: Boolean get() = isAdmin

    private fun List<String>.has(value: String) = any { it.equals(value, ignoreCase = true) }
}

data class ServerDownloadStatus(
    val started: Boolean,
    val queue: List<ServerDownloadItem>,
)

data class ServerDownloadItem(
    val chapterId: Int,
    val mangaId: Int,
    /** `QUEUED`, `DOWNLOADING`, `FINISHED` or `ERROR`. */
    val state: String,
    val progress: Float,
    val tries: Int,
    val position: Int,
)

/** Download state of a chapter as the server knows it. */
enum class ServerChapterState {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
    ;

    companion object {
        fun ofQueueState(value: String): ServerChapterState =
            when (value) {
                "QUEUED" -> QUEUED
                "DOWNLOADING" -> DOWNLOADING
                "FINISHED" -> DOWNLOADED
                "ERROR" -> ERROR
                else -> QUEUED
            }
    }
}

data class ServerLibraryItem(
    val manga: ServerManga,
    val chapterCount: Int,
    val downloadedChapterCount: Int,
    val queuedChapterCount: Int,
)

data class ServerLibraryPage(
    val items: List<ServerLibraryItem>,
    val hasNextPage: Boolean,
)

/** Raised when the signed-in account lacks the permission a request needs. */
class SuwayomiForbiddenException : IllegalStateException("当前账号没有该操作权限")


data class ServerSource(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val extensionPackage: String,
)

data class ServerExtension(
    val packageName: String,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val versionName: String,
    val isInstalled: Boolean,
    val hasUpdate: Boolean,
    val isObsolete: Boolean,
)

data class ServerMangaPage(
    val mangas: List<ServerManga>,
    val hasNextPage: Boolean,
)

data class ServerReadingHistory(
    val id: Int,
    val mangaId: Int,
    val isRead: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
)
