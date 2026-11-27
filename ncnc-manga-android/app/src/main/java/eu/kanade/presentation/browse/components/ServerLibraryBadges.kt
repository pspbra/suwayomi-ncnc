package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import eu.kanade.tachiyomi.source.suwayomi.ServerLibraryPresence
import tachiyomi.presentation.core.components.Badge

/**
 * The shared collection of the server as the last browse page saw it, keyed by manga url.
 *
 * Downloads belong to the server, so the cards have to show its counters instead of the ones of
 * the local download manager. The paging sources fill this in while they load a page.
 */
val LocalServerLibrary = compositionLocalOf<Map<String, ServerLibraryPresence>?> { null }

/**
 * Download state of a title as the server knows it.
 *
 * The numbers describe the files the server stores for every account, not what this device has.
 * A title which is part of the shared collection but whose counters are not known yet only gets a
 * marker, which is what the source pages show to explain why the title can be downloaded at all.
 */
@Composable
fun RowScope.ServerDownloadBadges(url: String) {
    val presence = LocalServerLibrary.current?.get(url) ?: return
    if (!presence.inServerLibrary) return

    if (!presence.hasCounts) {
        Badge(text = "服务器")
        return
    }

    if (presence.chapterCount > 0 || presence.downloadedChapterCount > 0) {
        Badge(
            text = if (presence.chapterCount > 0) {
                "${presence.downloadedChapterCount}/${presence.chapterCount}"
            } else {
                presence.downloadedChapterCount.toString()
            },
        )
    }
    if (presence.queuedChapterCount > 0) {
        Badge(
            text = "+${presence.queuedChapterCount}",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}
