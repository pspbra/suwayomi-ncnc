package eu.kanade.tachiyomi.source.suwayomi

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Persists the sources installed on the Suwayomi server.
 *
 * The proxies in `AndroidSourceManager` are rebuilt from this list, so manga browsed from a server
 * source keep resolving after a restart. Without it those rows would point at a source id that no
 * longer exists, breaking their details screen and reader.
 */
@Inject
@SingleIn(AppScope::class)
class SuwayomiSourceCache(
    context: Context,
) {
    private val preferences =
        context.applicationContext
            .getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
    private val serverPreferences = SuwayomiServerPreferences(context)

    private val mutableSources = MutableStateFlow(read())

    val sources: StateFlow<List<ServerSource>> = mutableSources.asStateFlow()

    /**
     * The shared collection as the latest browse pages saw it, keyed by manga url.
     *
     * Downloads belong to the server, so a card has to show the numbers of the server instead of the
     * device. The paging sources already read them, caching them here avoids a second request per page.
     */
    private val mutableLibrary = MutableStateFlow<Map<String, ServerLibraryPresence>>(emptyMap())
    val library: StateFlow<Map<String, ServerLibraryPresence>> = mutableLibrary.asStateFlow()

    /** Records what a browse page learned about the shared collection. */
    fun rememberLibrary(mangas: List<ServerManga>) {
        if (mangas.isEmpty()) return
        val merged = mutableLibrary.value.toMutableMap()
        mangas.forEach { manga ->
            merged[manga.localUrl] = (merged[manga.localUrl] ?: ServerLibraryPresence.EMPTY).copy(
                inServerLibrary = manga.inServerLibrary,
            )
        }
        mutableLibrary.value = merged
    }

    /** Records the download counters of a server library page. */
    fun rememberLibraryCounts(items: List<ServerLibraryItem>) {
        if (items.isEmpty()) return
        val merged = mutableLibrary.value.toMutableMap()
        items.forEach { item ->
            merged[item.manga.localUrl] = ServerLibraryPresence(
                inServerLibrary = true,
                chapterCount = item.chapterCount,
                downloadedChapterCount = item.downloadedChapterCount,
                queuedChapterCount = item.queuedChapterCount,
            )
        }
        mutableLibrary.value = merged
    }

    /**
     * Updates the counters of one title, e.g. after a download was requested from its detail page,
     * so the cards of the browse page do not keep showing the numbers of the previous page load.
     */
    fun updateLibraryCounts(
        url: String,
        chapterCount: Int,
        downloadedChapterCount: Int,
        queuedChapterCount: Int,
    ) {
        val current = mutableLibrary.value[url]
        val updated = ServerLibraryPresence(
            inServerLibrary = current?.inServerLibrary ?: true,
            chapterCount = chapterCount,
            downloadedChapterCount = downloadedChapterCount,
            queuedChapterCount = queuedChapterCount,
        )
        if (current == updated) return
        mutableLibrary.value = mutableLibrary.value.toMutableMap().apply { put(url, updated) }
    }

    fun save(sources: List<ServerSource>) {
        serverPreferences.rememberSources(sources.map { it.mihonSourceId() })
        preferences
            .edit()
            .putString(KEY_SERVER_URL, serverPreferences.accountKey)
            .putString(KEY_SOURCES, encode(sources))
            .apply()
        mutableSources.value = sources
    }

    /** Drops the cached list, e.g. after the user points the app at a different server. */
    fun clear() {
        read() // Preserve source ownership before discarding a previous server/account cache.
        preferences.edit().clear().apply()
        mutableSources.value = emptyList()
    }

    private fun read(): List<ServerSource> {
        val raw = preferences.getString(KEY_SOURCES, null) ?: return emptyList()
        val sources = runCatching { decode(raw) }.getOrDefault(emptyList())
        serverPreferences.rememberSources(sources.map { it.mihonSourceId() })
        if (preferences.getString(KEY_SERVER_URL, null) != serverPreferences.accountKey) return emptyList()
        return sources
    }

    private fun encode(sources: List<ServerSource>): String =
        buildJsonArray {
            sources.forEach { source ->
                add(
                    buildJsonObject {
                        put("id", source.id)
                        put("name", source.name)
                        put("lang", source.lang)
                        put("iconUrl", source.iconUrl)
                        put("supportsLatest", source.supportsLatest)
                        put("extensionPackage", source.extensionPackage)
                    },
                )
            }
        }.toString()

    private fun decode(raw: String): List<ServerSource> {
        val array = Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val value = element as? JsonObject ?: return@mapNotNull null
            ServerSource(
                id = value.string("id"),
                name = value.string("name"),
                lang = value.string("lang"),
                iconUrl = value.string("iconUrl"),
                supportsLatest = value.string("supportsLatest").toBooleanStrictOrNull() ?: false,
                extensionPackage = value.string("extensionPackage"),
            ).takeIf { it.id.isNotEmpty() }
        }
    }

    private fun JsonObject.string(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

    private companion object {
        const val PREFERENCE_FILE = "suwayomi_sources"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SOURCES = "sources"
    }
}

/**
 * What the client knows about one title of the shared collection.
 *
 * A card of the server library shows the download counters, a card of a source page only needs to
 * say whether the title is already part of the shared collection.
 */
data class ServerLibraryPresence(
    val inServerLibrary: Boolean,
    val chapterCount: Int,
    val downloadedChapterCount: Int,
    val queuedChapterCount: Int,
) {
    val hasCounts: Boolean get() = chapterCount > 0 || downloadedChapterCount > 0 || queuedChapterCount > 0

    companion object {
        val EMPTY = ServerLibraryPresence(false, 0, 0, 0)
    }
}
