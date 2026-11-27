package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.browse.components.LocalServerLibrary
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import mihon.app.di.appGraph

/**
 * The shared collection of the server.
 *
 * Every download shown here runs on the server and is shared by all accounts; the personal shelf is
 * the only place which downloads to the device.
 */
data object ServerBrowseTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val selected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = "浏览",
                icon = rememberAnimatedVectorPainter(image, selected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val cache = remember { context.appGraph.suwayomiSourceCache }
        val library by cache.library.collectAsStateWithLifecycle()

        // The cards report the counters of the server, they are collected while the paging source
        // loads a page, so the provider sits above the navigator.
        CompositionLocalProvider(LocalServerLibrary provides library) {
            Navigator(BrowseSourceScreen(SuwayomiSource.ID, null, embedded = true))
        }
    }
}
