package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiServerPreferences
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiSetupDialog
import eu.kanade.tachiyomi.ui.browse.ServerAddTab
import eu.kanade.tachiyomi.ui.browse.ServerBrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.MoreTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.presentation.core.util.collectAsState

object HomeScreen : Screen() {
    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "HomeTabs"

    private val TABS =
        listOf(
            LibraryTab,
            ServerBrowseTab,
            HistoryTab,
            ServerAddTab,
            MoreTab,
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val basePreferences = remember { context.appGraph.basePreferences }
        val onboardingCompleted by basePreferences.shownOnboardingFlow.collectAsState()
        val serverPreferences = remember { SuwayomiServerPreferences(context) }
        var serverConfigured by remember {
            mutableStateOf(serverPreferences.isConfigured)
        }
        val accountKey by SuwayomiServerPreferences.accountChanges.collectAsState()
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(accountKey, navigator.lastItem) {
            serverConfigured = serverPreferences.isConfigured
        }
        LaunchedEffect(accountKey, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (serverPreferences.isConfigured) {
                    context.appGraph.suwayomiReadingSync.synchronize()
                    kotlinx.coroutines.delay(15_000)
                }
            }
        }
        TabNavigator(
            tab = LibraryTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                val tabletUi = isTabletUi()
                val navigationSuiteType =
                    if (tabletUi) {
                        NavigationSuiteType.NavigationRail
                    } else {
                        NavigationSuiteType.NavigationBar
                    }
                val navigationSuiteState = rememberNavigationSuiteScaffoldState()
                LaunchedEffect(navigationSuiteState, tabletUi) {
                    if (tabletUi) navigationSuiteState.show()
                    showBottomNavEvent.receiveAsFlow().collectLatest { show ->
                        if (tabletUi || show) {
                            navigationSuiteState.show()
                        } else {
                            navigationSuiteState.hide()
                        }
                    }
                }

                NavigationSuiteScaffold(
                    navigationSuiteType = navigationSuiteType,
                    state = navigationSuiteState,
                    navigationSuiteColors =
                        NavigationSuiteDefaults.colors(
                            navigationRailContainerColor =
                                MaterialTheme.colorScheme
                                    .surfaceColorAtElevation(3.dp),
                        ),
                    navigationItemVerticalArrangement = Arrangement.Center,
                    navigationItems = {
                        TABS.fastForEach { NavigationSuiteItem(it, navigationSuiteType) }
                    },
                ) {
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            materialFadeThroughIn(
                                initialScale = 1f,
                                durationMillis = TabFadeDuration,
                            ) togetherWith materialFadeThroughOut(durationMillis = TabFadeDuration)
                        },
                        label = "tabContent",
                    ) {
                        tabNavigator.saveableState(key = "currentTab", it) {
                            it.Content()
                        }
                    }
                }
            }

            val goToLibraryTab = { tabNavigator.current = LibraryTab }

            BackHandler(enabled = tabNavigator.current != LibraryTab, onBack = goToLibraryTab)

            LaunchedEffect(Unit) {
                launch {
                    librarySearchEvent.receiveAsFlow().collectLatest {
                        goToLibraryTab()
                        LibraryTab.search(it)
                    }
                }
                launch {
                    openTabEvent.receiveAsFlow().collectLatest {
                        tabNavigator.current =
                            when (it) {
                                is Tab.Library -> {
                                    LibraryTab
                                }

                                Tab.Updates -> {
                                    ServerBrowseTab
                                }

                                Tab.History -> {
                                    HistoryTab
                                }

                                is Tab.Browse -> {
                                    ServerAddTab
                                }

                                is Tab.More -> {
                                    MoreTab
                                }
                            }

                        if (it is Tab.Library && it.mangaIdToOpen != null) {
                            navigator.push(MangaScreen(it.mangaIdToOpen))
                        }
                        if (it is Tab.More && it.toDownloads) {
                            navigator.push(DownloadQueueScreen)
                        }
                    }
                }
            }
        }
        // First-time setup is part of the onboarding flow. This dialog is only a
        // fallback for installs that already finished onboarding (e.g. an app update
        // keeping the old preferences) but still have no server configured, so it
        // must never pop up while the onboarding is on screen.
        if (!serverConfigured && onboardingCompleted) {
            SuwayomiSetupDialog(
                dismissible = false,
                onConfigured = { serverConfigured = true },
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming")
    private fun NavigationSuiteItem(
        tab: eu.kanade.presentation.util.Tab,
        navigationSuiteType: NavigationSuiteType,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationSuiteItem(
            navigationSuiteType = navigationSuiteType,
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = {
                Icon(
                    painter = tab.options.icon!!,
                    contentDescription = tab.options.title,
                )
            },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            badge = tabBadge(tab),
        )
    }

    @Suppress("UNUSED_PARAMETER")
    @Composable
    private fun tabBadge(tab: eu.kanade.presentation.util.Tab): (@Composable () -> Unit)? = null

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class Library(
            val mangaIdToOpen: Long? = null,
        ) : Tab

        data object Updates : Tab

        data object History : Tab

        data class Browse(
            val toExtensions: Boolean = false,
        ) : Tab

        data class More(
            val toDownloads: Boolean,
        ) : Tab
    }
}
