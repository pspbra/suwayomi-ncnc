package suwayomi.tachidesk.manga.impl.cache

import eu.kanade.tachiyomi.source.local.LocalSource
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/** Persisted metadata is shared; source configuration changes invalidate its freshness. */
object SourceCachePolicy {
    private val invalidatedSources = ConcurrentHashMap<Long, Long>()
    @Volatile private var invalidatedAllAt = 0L

    fun invalidateSource(sourceId: Long) {
        invalidatedSources[sourceId] = Instant.now().epochSecond
    }

    fun invalidateAll() {
        invalidatedAllAt = Instant.now().epochSecond
        invalidatedSources.clear()
    }

    fun isFresh(sourceId: Long, fetchedAt: Long, ttl: Duration, now: Long = Instant.now().epochSecond): Boolean =
        sourceId != LocalSource.ID && fetchedAt > 0 &&
            fetchedAt > maxOf(invalidatedAllAt, invalidatedSources[sourceId] ?: 0L) &&
            now >= fetchedAt && now - fetchedAt < ttl.inWholeSeconds.coerceAtLeast(1)
}
