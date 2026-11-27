package suwayomi.tachidesk.manga.impl.cache

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.MangasPage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import suwayomi.tachidesk.server.serverConfig
import java.util.EnumMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class SourceBrowseOperation {
    POPULAR,
    LATEST,
    SEARCH,
}

data class SourceBrowseCacheKey(
    val sourceId: Long,
    val operation: SourceBrowseOperation,
    val page: Int,
    val query: String = "",
    val filtersFingerprint: String = "",
) {
    init {
        require(page > 0) { "page = $page is not in valid range" }
    }
}

data class SourceBrowseCacheConfig(
    val ttl: Duration,
    val maximumSize: Int = 2000,
)

data class SourceBrowseCacheStats(
    val hits: Map<SourceBrowseOperation, Long>,
    val misses: Map<SourceBrowseOperation, Long>,
    val loads: Map<SourceBrowseOperation, Long>,
    val loadErrors: Map<SourceBrowseOperation, Long>,
    val coalesced: Map<SourceBrowseOperation, Long>,
    val currentSize: Int,
    val evictionCount: Long,
)

/**
 * A bounded, process-local cache for extension browse results.
 *
 * Database processing deliberately happens after this cache. The cached value is the extension's
 * [MangasPage], not database ids, so every caller retains the existing insert/update behaviour.
 */
class SourceBrowseCache(
    private val config: () -> SourceBrowseCacheConfig,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class VersionedKey(
        val key: SourceBrowseCacheKey,
        val sourceRevision: Long,
        val globalRevision: Long,
    )

    private data class CacheEntry(
        val value: MangasPage,
        val expiresAtNanos: Long,
    )

    private val logger = KotlinLogging.logger {}
    private val cacheLock = Any()
    private val entries = LinkedHashMap<VersionedKey, CacheEntry>(16, 0.75f, true)
    private val inFlight = ConcurrentHashMap<VersionedKey, CompletableDeferred<MangasPage>>()
    private val sourceRevisions = ConcurrentHashMap<Long, AtomicLong>()
    private val globalRevision = AtomicLong()
    private var lastConfig: SourceBrowseCacheConfig? = null

    private val hits = operationCounters()
    private val misses = operationCounters()
    private val loads = operationCounters()
    private val loadErrors = operationCounters()
    private val coalesced = operationCounters()
    private val evictionCount = AtomicLong()

    suspend fun getOrLoad(
        key: SourceBrowseCacheKey,
        bypassCache: Boolean = false,
        loader: suspend () -> MangasPage,
    ): MangasPage {
        if (key.sourceId == LocalSource.ID) return loader()
        val (currentConfig, versionedKey) =
            synchronized(cacheLock) {
                val current = config()
                if (lastConfig != null && lastConfig != current) invalidateAll()
                lastConfig = current
                current to key.versioned()
            }
        if (currentConfig.ttl <= Duration.ZERO) return loader()
        if (!bypassCache) {
            getCached(versionedKey)?.let {
                hits.getValue(key.operation).incrementAndGet()
                logger.debug { "sourceBrowseCache event=HIT operation=${key.operation} sourceId=${key.sourceId} page=${key.page}" }
                return it
            }
        }

        misses.getValue(key.operation).incrementAndGet()
        logger.debug { "sourceBrowseCache event=MISS operation=${key.operation} sourceId=${key.sourceId} page=${key.page}" }

        val leader = CompletableDeferred<MangasPage>()
        val existing = inFlight.putIfAbsent(versionedKey, leader)
        if (existing != null) {
            coalesced.getValue(key.operation).incrementAndGet()
            logger.debug {
                "sourceBrowseCache event=COALESCED operation=${key.operation} sourceId=${key.sourceId} page=${key.page}"
            }
            return existing.await()
        }

        try {
            // Another load may have completed between the first lookup and winning single-flight.
            val result =
                if (!bypassCache) {
                    getCached(versionedKey) ?: loadAndCache(versionedKey, currentConfig, loader)
                } else {
                    loadAndCache(versionedKey, currentConfig, loader)
                }
            leader.complete(result)
            return result
        } catch (throwable: Throwable) {
            leader.completeExceptionally(throwable)
            throw throwable
        } finally {
            inFlight.remove(versionedKey, leader)
        }
    }

    fun invalidateSource(sourceId: Long) {
        SourceCachePolicy.invalidateSource(sourceId)
        synchronized(cacheLock) {
            sourceRevisions.computeIfAbsent(sourceId) { AtomicLong() }.incrementAndGet()
            entries.keys.removeAll { it.key.sourceId == sourceId }
        }
        logger.debug { "sourceBrowseCache event=INVALIDATE_SOURCE sourceId=$sourceId" }
    }

    fun invalidateAll() {
        SourceCachePolicy.invalidateAll()
        synchronized(cacheLock) {
            globalRevision.incrementAndGet()
            entries.clear()
        }
        logger.debug { "sourceBrowseCache event=INVALIDATE_ALL" }
    }

    fun stats(): SourceBrowseCacheStats {
        val size =
            synchronized(cacheLock) {
                removeExpired(nanoTime())
                entries.size
            }
        return SourceBrowseCacheStats(
            hits = hits.snapshot(),
            misses = misses.snapshot(),
            loads = loads.snapshot(),
            loadErrors = loadErrors.snapshot(),
            coalesced = coalesced.snapshot(),
            currentSize = size,
            evictionCount = evictionCount.get(),
        )
    }

    private suspend fun loadAndCache(
        versionedKey: VersionedKey,
        currentConfig: SourceBrowseCacheConfig,
        loader: suspend () -> MangasPage,
    ): MangasPage {
        val key = versionedKey.key
        val startedAt = nanoTime()
        loads.getValue(key.operation).incrementAndGet()
        return try {
            val result = loader()
            val ttl = currentConfig.ttl
            if (ttl > Duration.ZERO && versionedKey == key.versioned()) {
                put(versionedKey, result, ttl, currentConfig.maximumSize)
            }
            logger.debug {
                "sourceBrowseCache event=LOAD_SUCCESS operation=${key.operation} sourceId=${key.sourceId} " +
                    "page=${key.page} latencyMs=${(nanoTime() - startedAt) / NANOS_PER_MILLISECOND}"
            }
            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            loadErrors.getValue(key.operation).incrementAndGet()
            logger.debug(throwable) {
                "sourceBrowseCache event=LOAD_FAIL operation=${key.operation} sourceId=${key.sourceId} " +
                    "page=${key.page} latencyMs=${(nanoTime() - startedAt) / NANOS_PER_MILLISECOND}"
            }
            throw throwable
        }
    }

    private fun getCached(key: VersionedKey): MangasPage? =
        synchronized(cacheLock) {
            val entry = entries[key] ?: return@synchronized null
            if (entry.expiresAtNanos <= nanoTime()) {
                entries.remove(key)
                return@synchronized null
            }
            entry.value
        }

    private fun put(
        key: VersionedKey,
        value: MangasPage,
        ttl: Duration,
        maximumSize: Int,
    ) {
        if (maximumSize <= 0) return
        val now = nanoTime()
        val ttlNanos = ttl.inWholeNanoseconds
        val calculatedExpiry = now + ttlNanos
        val expiresAt = if (calculatedExpiry < now) Long.MAX_VALUE else calculatedExpiry
        synchronized(cacheLock) {
            if (key != key.key.versioned()) return
            removeExpired(now)
            entries[key] = CacheEntry(value, expiresAt)
            while (entries.size > maximumSize) {
                val eldest = entries.entries.iterator()
                eldest.next()
                eldest.remove()
                evictionCount.incrementAndGet()
            }
        }
    }

    private fun removeExpired(now: Long) {
        entries.entries.removeAll { it.value.expiresAtNanos <= now }
    }

    private fun SourceBrowseCacheKey.versioned(): VersionedKey =
        VersionedKey(
            key = this,
            sourceRevision = sourceRevisions.computeIfAbsent(sourceId) { AtomicLong() }.get(),
            globalRevision = globalRevision.get(),
        )

    private fun operationCounters(): EnumMap<SourceBrowseOperation, AtomicLong> =
        EnumMap<SourceBrowseOperation, AtomicLong>(SourceBrowseOperation::class.java).apply {
            SourceBrowseOperation.entries.forEach { put(it, AtomicLong()) }
        }

    private fun Map<SourceBrowseOperation, AtomicLong>.snapshot(): Map<SourceBrowseOperation, Long> =
        mapValues { (_, counter) -> counter.get() }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        val shared: SourceBrowseCache by lazy {
            SourceBrowseCache(
                config = {
                    SourceBrowseCacheConfig(
                        ttl = serverConfig.sourceBrowseCacheTtl.value.coerceAtLeast(1.seconds),
                    )
                },
            )
        }
    }
}
