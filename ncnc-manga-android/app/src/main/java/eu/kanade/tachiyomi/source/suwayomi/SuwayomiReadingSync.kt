package eu.kanade.tachiyomi.source.suwayomi

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.copyFrom
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.util.Date

/** Durable, account-scoped outbox. Only actual reader events are uploaded, never legacy local history. */
@Inject
@SingleIn(AppScope::class)
class SuwayomiReadingSync(
    context: Context,
    network: NetworkHelper,
    private val mangas: MangaRepository,
    private val chapters: ChapterRepository,
    private val histories: HistoryRepository,
    private val syncChapters: SyncChaptersWithSource,
) {
    private val preferences = SuwayomiServerPreferences(context)
    private val outbox = context.getSharedPreferences("suwayomi_reading_outbox", Context.MODE_PRIVATE)
    private val api = SuwayomiServerApi(preferences, network.client)
    private val source = SuwayomiSource(preferences, network)
    private val mutex = Mutex()
    private val storageLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val mutableStatus = MutableStateFlow("")
    val status = mutableStatus.asStateFlow()

    init {
        scope.launch {
            for (signal in wakeUp) {
                delay(2000)
                attempt { mutex.withLock { flush(preferences.accountKey) } }
            }
        }
    }

    fun owns(url: String): Boolean =
        preferences.accountKey.isNotEmpty() &&
            url.startsWith("account/${preferences.accountKey}/")

    fun visible(manga: Manga): Boolean =
        when {
            manga.url.startsWith("account/") -> owns(manga.url)
            manga.source == SuwayomiSource.ID -> false
            manga.source in preferences.knownSourceIds && manga.url.toIntOrNull() != null -> false
            else -> true
        }

    suspend fun hideHistory(
        mangaId: Long,
        chapterId: Long? = null,
    ) {
        val manga = mangas.getMangaById(mangaId)
        if (!owns(manga.url)) return
        val url = if (chapterId == null) manga.url else chapters.getChapterById(chapterId)?.url ?: return
        outbox.edit().putLong("hidden:$url", System.currentTimeMillis() / 1000).commit()
    }

    private fun isHidden(
        mangaUrl: String,
        chapterUrl: String,
        readAt: Long,
    ): Boolean = readAt <= maxOf(outbox.getLong("hidden:$mangaUrl", -1), outbox.getLong("hidden:$chapterUrl", -1))

    suspend fun historyVisible(
        manga: Manga,
        chapterId: Long,
        readAt: Long,
    ): Boolean {
        val chapter = chapters.getChapterById(chapterId) ?: return false
        return !isHidden(manga.url, chapter.url, readAt)
    }

    /** Captures ownership from the chapter URL, so even an old reader cannot write as a new user. */
    suspend fun record(
        url: String,
        page: Int,
        read: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (!owns(url)) return@withContext
        val account = url.split('/')[1]
        val id = url.substringAfterLast('/').toInt()
        val value =
            buildJsonObject {
                put("page", page)
                put("read", read)
                put("time", System.currentTimeMillis() / 1000)
            }.toString()
        synchronized(storageLock) {
            outbox
                .edit()
                .putString("$account:$id", value)
                .remove("hidden:$url")
                .remove("hidden:${url.substringBeforeLast('/')}")
                .commit()
        }
        mutableStatus.value = "阅读记录等待同步"
        wakeUp.trySend(Unit)
    }

    /** Called on startup/resume and while the home screen is visible; retries offline work. */
    suspend fun synchronize() =
        withContext(Dispatchers.IO) {
            if (!preferences.isConfigured) return@withContext
            attempt {
                mutex.withLock {
                    val account = preferences.accountKey
                    flush(account)
                    pullLibrary(account)
                    var offset = 0
                    do {
                        val page = api.readingHistory(offset)
                        requireAccount(account)
                        for ((mangaId, records) in page.groupBy { it.mangaId }) {
                            val url = "account/$account/$mangaId"
                            val manga =
                                mangas.getMangaByUrlAndSourceId(url, SuwayomiSource.ID) ?: run {
                                    val remoteManga = api.manga(mangaId)
                                    requireAccount(account)
                                    mangas
                                        .insertNetworkManga(
                                            listOf(
                                                Manga.create().copyFrom(remoteManga.toSManga()).copy(
                                                    url = url,
                                                    source = SuwayomiSource.ID,
                                                    title = remoteManga.title,
                                                ),
                                            ),
                                        ).single()
                                }
                            requireAccount(account)
                            val knownIds =
                                chapters
                                    .getChapterByMangaId(manga.id)
                                    .map { it.url.substringAfterLast('/').toIntOrNull() }
                                    .toSet()
                            if (records.any { it.id !in knownIds }) {
                                val remote = api.chapters(mangaId)
                                requireAccount(account)
                                if (remote.isNotEmpty()) syncChapters.await(remote.map(source::toSChapter), manga, source)
                            }
                            val local = chapters.getChapterByMangaId(manga.id).associateBy { it.url.substringAfterLast('/').toIntOrNull() }
                            for (record in records) {
                                requireAccount(account)
                                val chapter = local[record.id] ?: continue
                                if (outbox.contains("$account:${record.id}")) continue
                                chapters.update(
                                    ChapterUpdate(chapter.id) {
                                        read = record.isRead
                                        lastPageRead = record.lastPageRead.toLong()
                                    },
                                )
                                if (!isHidden(manga.url, chapter.url, record.lastReadAt)) {
                                    histories.upsertHistory(HistoryUpdate(chapter.id, Date(record.lastReadAt * 1000), 0))
                                }
                            }
                        }
                        offset += page.size
                    } while (page.size == 100)
                    mutableStatus.value = if (outbox.all.keys.any { (it.startsWith("$account:") || it.startsWith("favorite:$account:")) }) "阅读记录等待同步" else "书架与阅读记录已同步"
                }
            }
        }

    /** Pull before choosing the reader's initial chapter/page, and after refreshing chapter metadata. */
    suspend fun pullManga(manga: Manga) =
        withContext(Dispatchers.IO) {
            check(owns(manga.url)) { "这部漫画属于旧连接或其他账号，请从当前账号的浏览页重新打开" }
            attempt {
                mutex.withLock {
                    val account = preferences.accountKey
                    flush(account)
                    val remote = api.chapters(preferences.mangaId(manga.url))
                    requireAccount(account)
                    val byId = remote.associateBy { it.id }
                    for (chapter in chapters.getChapterByMangaId(manga.id)) {
                        val value = byId[chapter.url.substringAfterLast('/').toIntOrNull()] ?: continue
                        if (outbox.contains("$account:${value.id}")) continue
                        chapters.update(
                            ChapterUpdate(chapter.id) {
                                read = value.isRead
                                lastPageRead = value.lastPageRead.toLong()
                            },
                        )
                        if (value.lastReadAt > 0 && !isHidden(manga.url, chapter.url, value.lastReadAt)) {
                            histories.upsertHistory(HistoryUpdate(chapter.id, Date(value.lastReadAt * 1000), 0))
                        }
                    }
                }
            }
        }

    /** Explicit shelf edits only; old local favorites are never uploaded automatically. */
    suspend fun setFavorite(manga: Manga, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!owns(manga.url)) return@withContext false
        if (favorite && manga.source != SuwayomiSource.ID) {
            mutableStatus.value = "请先添加到服务器资源库，再从浏览页收藏"
            return@withContext false
        }
        val account = preferences.accountKey
        mutex.withLock {
            requireAccount(account)
            val id = preferences.mangaId(manga.url)
            if (favorite) {
                try {
                    check(api.manga(id, account).inServerLibrary) { "这部漫画已从服务器资源库移除" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mutableStatus.value = "书架收藏失败：${e.message ?: "请联网后重试"}"
                    return@withContext false
                }
            }
            val key = "favorite:$account:$id"
            // Persist before changing the cached shelf, so interruption cannot lose the edit.
            check(outbox.edit().putBoolean(key, favorite).commit())
            mangas.update(MangaUpdate(manga.id) {
                favoriteAt = if (favorite) System.currentTimeMillis() else null
            })
            mutableStatus.value = "书架变更等待同步"
        }
        wakeUp.trySend(Unit)
        true
    }

    private suspend fun pullLibrary(account: String) {
        val remote = api.libraryManga()
        requireAccount(account)
        val ids = remote.map { it.id }.toSet()
        for (value in remote) {
            requireAccount(account)
            val local = mangas.getMangaByUrlAndSourceId(value.localUrl, SuwayomiSource.ID)
                ?: mangas.insertNetworkManga(listOf(
                    Manga.create().copyFrom(value.toSManga()).copy(
                        url = value.localUrl, source = SuwayomiSource.ID, title = value.title,
                    ),
                )).single()
            if (!outbox.contains("favorite:$account:${value.id}")) {
                mangas.update(MangaUpdate(local.id) {
                    favoriteAt = value.inLibraryAt.takeIf { it > 0 }?.times(1000)
                        ?: local.favoriteAt ?: System.currentTimeMillis()
                })
            }
            // Populate chapters for newly pulled shelf entries, preserving account-scoped progress.
            if (chapters.getChapterByMangaId(local.id).isEmpty()) {
                val list = api.chapters(value.id, account)
                requireAccount(account)
                if (list.isNotEmpty()) syncChapters.await(list.map(source::toSChapter), local, source)
            }
        }
        for (local in mangas.getFavorites().filter { owns(it.url) }) {
            requireAccount(account)
            val id = preferences.mangaId(local.url)
            if ((id !in ids || local.source != SuwayomiSource.ID) && !outbox.contains("favorite:$account:$id")) {
                mangas.update(MangaUpdate(local.id) { favoriteAt = null })
            }
        }
    }

    private suspend fun flush(account: String) {
        if (account.isBlank()) return
        val pending = synchronized(storageLock) { outbox.all.toMap() }
        for ((key, raw) in pending) {
            if (!key.startsWith("favorite:$account:")) continue
            requireAccount(account)
            val id = key.substringAfterLast(':').toInt()
            try {
                if (raw == true) api.addMangaToLibrary(id, account) else api.removeMangaFromLibrary(id, account)
            } catch (e: Exception) {
                // A removed shared resource cannot be favorited. Reconcile this permanent rejection
                // rather than letting one stale edit block every subsequent shelf/progress sync.
                if (raw == true && e.message?.contains("Manga must be added to the server library first") == true) {
                    requireAccount(account)
                    outbox.edit().remove(key).commit()
                    mangas.getMangaByUrlAndSourceId("account/$account/$id", SuwayomiSource.ID)?.let { local ->
                        mangas.update(MangaUpdate(local.id) { favoriteAt = null })
                    }
                }
                throw e
            }
            requireAccount(account)
            if (outbox.all[key] == raw) outbox.edit().remove(key).commit()
        }
        for ((key, raw) in pending) {
            if (!key.startsWith("$account:")) continue
            requireAccount(account)
            val value = Json.parseToJsonElement(raw as String).jsonObject
            val id = key.substringAfter(':').toInt()
            val remote = api.chapterProgress(id)
            requireAccount(account)
            // The newer reading event wins. A delayed offline upload must not rewind another device.
            val fingerprint = "${remote.lastReadAt}:${remote.lastPageRead}:${remote.isRead}"
            if (remote.lastReadAt <= value.getValue("time").jsonPrimitive.long ||
                outbox.getString("ack:$key", null) == fingerprint
            ) {
                val saved =
                    api.updateProgress(
                        account,
                        id,
                        value.getValue("page").jsonPrimitive.int,
                        value.getValue("read").jsonPrimitive.boolean,
                    )
                outbox.edit().putString("ack:$key", "${saved.lastReadAt}:${saved.lastPageRead}:${saved.isRead}").commit()
            }
            synchronized(storageLock) {
                if (outbox.getString(key, null) == raw) outbox.edit().remove(key).commit()
            }
        }
        mutableStatus.value = if (outbox.all.keys.any { (it.startsWith("$account:") || it.startsWith("favorite:$account:")) }) "阅读记录等待同步" else "书架与阅读记录已同步"
    }

    private fun requireAccount(account: String) {
        check(account == preferences.accountKey) { "账号已切换，请重新打开页面" }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mutableStatus.value = "书架或阅读记录尚未同步，将在联网后重试；若持续失败，请在服务器设置中重新登录"
        }
    }
}
