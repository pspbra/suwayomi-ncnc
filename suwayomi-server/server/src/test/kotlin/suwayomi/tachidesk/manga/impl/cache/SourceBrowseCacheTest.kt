package suwayomi.tachidesk.manga.impl.cache

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SourceBrowseCacheTest {
    private val page = MangasPage(emptyList(), false)

    @Test
    fun `same key is loaded only once`() =
        runTest {
            val cache = cache()
            val calls = AtomicInteger()
            val key = key()

            repeat(2) {
                cache.getOrLoad(key) {
                    calls.incrementAndGet()
                    page
                }
            }

            assertEquals(1, calls.get())
            assertEquals(1L, cache.stats().hits[SourceBrowseOperation.POPULAR])
        }

    @Test
    fun `expired value is loaded again`() =
        runTest {
            var now = 0L
            val cache = cache(nanoTime = { now })
            val calls = AtomicInteger()
            val key = key()

            cache.getOrLoad(key) {
                calls.incrementAndGet()
                page
            }
            now += 61.seconds.inWholeNanoseconds
            cache.getOrLoad(key) {
                calls.incrementAndGet()
                page
            }

            assertEquals(2, calls.get())
        }

    @Test
    fun `keys distinguish source operation and page`() =
        runTest {
            val cache = cache()
            val calls = AtomicInteger()
            val keys =
                listOf(
                    key(sourceId = 1, operation = SourceBrowseOperation.POPULAR, page = 1),
                    key(sourceId = 2, operation = SourceBrowseOperation.POPULAR, page = 1),
                    key(sourceId = 1, operation = SourceBrowseOperation.LATEST, page = 1),
                    key(sourceId = 1, operation = SourceBrowseOperation.POPULAR, page = 2),
                )

            keys.forEach { currentKey ->
                cache.getOrLoad(currentKey) {
                    calls.incrementAndGet()
                    page
                }
            }

            assertEquals(keys.size, calls.get())
        }

    @Test
    fun `one hundred concurrent cold requests use one loader`() =
        runTest {
            val cache = cache()
            val calls = AtomicInteger()
            val releaseLoader = CompletableDeferred<Unit>()

            val requests =
                List(100) {
                    async {
                        cache.getOrLoad(key()) {
                            calls.incrementAndGet()
                            releaseLoader.await()
                            page
                        }
                    }
                }
            runCurrent()
            assertEquals(1, calls.get())

            releaseLoader.complete(Unit)
            requests.awaitAll()

            assertEquals(1, calls.get())
            assertEquals(99L, cache.stats().coalesced[SourceBrowseOperation.POPULAR])
        }

    @Test
    fun `loader failures are not cached`() =
        runTest {
            val cache = cache()
            val calls = AtomicInteger()

            assertFailsWith<IllegalStateException> {
                cache.getOrLoad(key()) {
                    calls.incrementAndGet()
                    error("source failed")
                }
            }
            cache.getOrLoad(key()) {
                calls.incrementAndGet()
                page
            }

            assertEquals(2, calls.get())
            assertEquals(1L, cache.stats().loadErrors[SourceBrowseOperation.POPULAR])
        }

    @Test
    fun `coalesced callers receive the loader failure`() =
        runTest {
            val cache = cache()
            val calls = AtomicInteger()
            val releaseLoader = CompletableDeferred<Unit>()

            val requests =
                List(100) {
                    async {
                        runCatching {
                            cache.getOrLoad(key()) {
                                calls.incrementAndGet()
                                releaseLoader.await()
                                error("source failed")
                            }
                        }.exceptionOrNull()
                    }
                }
            runCurrent()
            assertEquals(1, calls.get())

            releaseLoader.complete(Unit)
            val failures = requests.awaitAll()

            assertEquals(100, failures.count { it is IllegalStateException })
            cache.getOrLoad(key()) {
                calls.incrementAndGet()
                page
            }
            assertEquals(2, calls.get())
        }

    @Test
    fun `invalidating one source does not affect another`() =
        runTest {
            val cache = cache()
            val calls = mutableMapOf(1L to 0, 2L to 0)

            suspend fun load(sourceId: Long) {
                cache.getOrLoad(key(sourceId = sourceId)) {
                    calls[sourceId] = calls.getValue(sourceId) + 1
                    page
                }
            }

            load(1)
            load(2)
            cache.invalidateSource(1)
            load(1)
            load(2)

            assertEquals(mapOf(1L to 2, 2L to 1), calls)
        }

    @Test
    fun `filter fingerprint is stable and includes state`() {
        val first = FilterList(TestTextFilter("query"), TestCheckBoxFilter(true))
        val same = FilterList(TestTextFilter("query"), TestCheckBoxFilter(true))
        val changed = FilterList(TestTextFilter("different"), TestCheckBoxFilter(true))

        assertEquals(SourceBrowseFilterFingerprint.create(first), SourceBrowseFilterFingerprint.create(same))
        assertNotEquals(SourceBrowseFilterFingerprint.create(first), SourceBrowseFilterFingerprint.create(changed))
    }

    @Test
    fun `all browse operations use the same ttl measured from load`() =
        runTest {
            var now = 0L
            val cache = cache { now }
            val calls = mutableMapOf<SourceBrowseOperation, Int>()

            suspend fun loadAll() {
                SourceBrowseOperation.entries.forEach { operation ->
                    cache.getOrLoad(key(operation = operation)) {
                        calls[operation] = (calls[operation] ?: 0) + 1
                        page
                    }
                }
            }
            loadAll()
            now = 59.seconds.inWholeNanoseconds
            loadAll()
            assertEquals(listOf(1, 1, 1), SourceBrowseOperation.entries.map { calls[it] })
            now = 60.seconds.inWholeNanoseconds
            loadAll()
            assertEquals(listOf(2, 2, 2), SourceBrowseOperation.entries.map { calls[it] })
        }

    @Test
    fun `ttl changes invalidate existing results and zero disables caching`() =
        runTest {
            var ttl = 1.minutes
            val cache = SourceBrowseCache(config = { SourceBrowseCacheConfig(ttl) })
            var calls = 0

            suspend fun load() =
                cache.getOrLoad(key()) {
                    calls++
                    page
                }
            load()
            load()
            assertEquals(1, calls)
            ttl = 2.minutes
            load()
            assertEquals(2, calls)
            ttl = 0.seconds
            load()
            load()
            assertEquals(4, calls)
            assertEquals(0, cache.stats().currentSize)
        }

    @Test
    fun `invalidated in flight results are not reused`() =
        runTest {
            val cache = cache()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val first =
                async {
                    cache.getOrLoad(key()) {
                        calls++
                        release.await()
                        page
                    }
                }
            runCurrent()
            cache.invalidateSource(1)
            cache.getOrLoad(key()) {
                calls++
                page
            }
            release.complete(Unit)
            first.await()
            cache.getOrLoad(key()) {
                calls++
                page
            }
            assertEquals(2, calls)
            assertEquals(1, cache.stats().currentSize)
        }

    @Test
    fun `query and filter changes do not reuse other search results`() =
        runTest {
            val cache = cache()
            val first = SourceBrowseCacheKey(1, SourceBrowseOperation.SEARCH, 1, "one", "filter-a")
            val keys = listOf(first, first.copy(query = "two"), first.copy(filtersFingerprint = "filter-b"))
            var calls = 0
            repeat(2) {
                keys.forEach {
                    cache.getOrLoad(it) {
                        calls++
                        page
                    }
                }
            }
            assertEquals(3, calls)
        }

    @Test
    fun `cache evicts least recently used pages at capacity`() =
        runTest {
            val cache = SourceBrowseCache(config = { SourceBrowseCacheConfig(1.minutes, maximumSize = 2) })
            var calls = 0

            suspend fun load(pageNumber: Int) =
                cache.getOrLoad(key(page = pageNumber)) {
                    calls++
                    page
                }
            load(1)
            load(2)
            load(1)
            load(3)
            load(1)
            assertEquals(3, calls)
            load(2)
            assertEquals(4, calls)
            assertEquals(2, cache.stats().currentSize)
        }

    @Test
    fun `generated config has one browse ttl defaulting to twelve hours`() {
        val config =
            com.typesafe.config.ConfigFactory
                .parseResources("server-reference.conf")
        assertEquals(java.time.Duration.ofHours(12), config.getDuration("server.sourceBrowseCacheTtl"))
        val keys = config.entrySet().map { it.key }.filter { it.startsWith("server.sourceBrowseCache") }
        assertEquals(listOf("server.sourceBrowseCacheTtl"), keys)
    }

    private fun cache(nanoTime: () -> Long = System::nanoTime): SourceBrowseCache =
        SourceBrowseCache(
            config = {
                SourceBrowseCacheConfig(
                    ttl = 1.minutes,
                    maximumSize = 10,
                )
            },
            nanoTime = nanoTime,
        )

    private fun key(
        sourceId: Long = 1,
        operation: SourceBrowseOperation = SourceBrowseOperation.POPULAR,
        page: Int = 1,
    ) = SourceBrowseCacheKey(sourceId, operation, page)

    private class TestTextFilter(
        state: String,
    ) : Filter.Text("text", state)

    private class TestCheckBoxFilter(
        state: Boolean,
    ) : Filter.CheckBox("checkbox", state)
}
