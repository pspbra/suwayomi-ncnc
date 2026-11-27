package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.browse.components.LocalServerLibrary
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.source.suwayomi.ServerExtension
import eu.kanade.tachiyomi.source.suwayomi.ServerSource
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiServerApi
import eu.kanade.tachiyomi.source.suwayomi.SuwayomiServerPreferences
import eu.kanade.tachiyomi.source.suwayomi.mihonSourceId
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.service.SourceManager

data object ServerAddTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(3u, "添加", rememberVectorPainter(MaterialSymbols.Rounded.Add))

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val cache = remember { context.appGraph.suwayomiSourceCache }
        val library by cache.library.collectAsStateWithLifecycle()

        // Source pages mark the results which already belong to the shared collection, because only
        // those can be downloaded by the server.
        CompositionLocalProvider(LocalServerLibrary provides library) {
            // A navigator of its own keeps pushes inside this tab, so the bottom bar stays visible.
            Navigator(ServerAddHomeScreen)
        }
    }
}

private object ServerAddHomeScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val graph = context.appGraph
        val api = remember {
            SuwayomiServerApi(SuwayomiServerPreferences(context), graph.networkHelper.client)
        }
        val sourceCache = remember { graph.suwayomiSourceCache }
        val sourceManager = remember { graph.sourceManager }
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var section by remember { mutableStateOf(Section.Sources) }
        var sources by remember { mutableStateOf(sourceCache.sources.value) }
        var extensions by remember { mutableStateOf<List<ServerExtension>>(emptyList()) }
        var query by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var refreshKey by remember { mutableIntStateOf(0) }
        var extensionLimit by remember { mutableIntStateOf(100) }
        var showLanguageFilter by remember { mutableStateOf(false) }
        var languageFilter by remember { mutableStateOf(setOf("zh", "zh-rCN", "zh-rTW")) }

        fun action(block: suspend () -> Unit) = scope.launch {
            loading = true
            error = null
            runCatching { block() }.onFailure { error = it.message ?: "操作失败" }
            loading = false
        }

        LaunchedEffect(section, refreshKey, extensionLimit) {
            loading = true
            error = null
            runCatching {
                if (section == Section.Sources) {
                    val loaded = api.sources()
                    sources = loaded
                    sourceCache.save(loaded)
                } else {
                    extensions = api.extensions(limit = extensionLimit)
                }
            }.onFailure { error = it.message ?: "加载失败" }
            loading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("添加") },
                    actions = {
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(MaterialSymbols.Rounded.Refresh, "刷新")
                        }
                        if (section == Section.Extensions) {
                            IconButton(onClick = { showLanguageFilter = true }) {
                                Icon(MaterialSymbols.Rounded.FilterList, "语言筛选")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(section == Section.Sources, { section = Section.Sources }, label = { Text("服务器图源") })
                    FilterChip(section == Section.Extensions, { section = Section.Extensions }, label = { Text("服务器扩展") })
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                error?.let {
                    Text("错误：$it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                val visibleSources = sources
                    .filter { it.name.contains(query, true) || it.lang.contains(query, true) }
                val visibleExtensions = extensions
                    .filter { it.name.contains(query, true) || it.lang.contains(query, true) }
                    .filter { languageFilter.isEmpty() || it.lang in languageFilter }
                val empty = if (section == Section.Sources) {
                    visibleSources.isEmpty()
                } else {
                    visibleExtensions.isEmpty()
                }
                if (loading && empty) {
                    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                    }
                } else if (section == Section.Sources) {
                    SourceList(visibleSources) { source ->
                        scope.launch {
                            error = null
                            runCatching {
                                val proxyId = source.mihonSourceId()
                                awaitServerSource(sourceManager, proxyId)
                                navigator.push(
                                    BrowseSourceScreen(
                                        sourceId = proxyId,
                                        listingQuery = GetRemoteManga.QUERY_POPULAR,
                                        embedded = false,
                                    ),
                                )
                            }.onFailure { error = it.message ?: "打开图源失败" }
                        }
                    }
                } else {
                    ExtensionList(
                        extensions = visibleExtensions,
                        busy = loading,
                        canLoadMore = extensions.size >= extensionLimit,
                        onAction = { extension ->
                            action {
                                api.setExtensionInstalled(
                                    extension.packageName,
                                    !extension.isInstalled,
                                    extension.hasUpdate,
                                )
                                extensions = api.extensions(limit = extensionLimit)
                            }
                        },
                        onLoadMore = { extensionLimit += 100 },
                    )
                }
            }
        }

        if (showLanguageFilter) {
            DropdownMenu(expanded = true, onDismissRequest = { showLanguageFilter = false }) {
                listOf(
                    "zh" to "中文",
                    "zh-rCN" to "中文 (HANS)",
                    "zh-rTW" to "中文 (HANT)",
                ).forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(if (code in languageFilter) "✓ $label" else label) },
                        onClick = {
                            languageFilter = if (code in languageFilter) languageFilter - code else languageFilter + code
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("全部语言") },
                    onClick = { languageFilter = emptySet(); showLanguageFilter = false },
                )
            }
        }
    }

    private enum class Section { Sources, Extensions }
}

/**
 * Waits until the tapped server source is registered as a Mihon source.
 *
 * `AndroidSourceManager` rebuilds its map from `SuwayomiSourceCache` asynchronously, so pushing the
 * browse screen straight away could resolve a stub instead. The cache is written when this tab loads
 * the list, so by the time a source is tappable that rebuild is already under way.
 */
private suspend fun awaitServerSource(sourceManager: SourceManager, id: Long) {
    withTimeoutOrNull(PROXY_WAIT_MS) {
        while (sourceManager.get(id) == null) {
            delay(PROXY_POLL_MS)
        }
    } ?: error("图源注册超时，请重试")
}

private const val PROXY_WAIT_MS = 5_000L
private const val PROXY_POLL_MS = 50L

@Composable
private fun SourceList(sources: List<ServerSource>, onClick: (ServerSource) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(sources, key = { it.id }) { source ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick(source) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = source.iconUrl,
                    contentDescription = source.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Text("${source.lang}  ·  ${source.extensionPackage}", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ExtensionList(
    extensions: List<ServerExtension>,
    busy: Boolean,
    canLoadMore: Boolean,
    onAction: (ServerExtension) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(extensions, key = { it.packageName }) { extension ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = extension.iconUrl,
                    contentDescription = extension.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(extension.name, style = MaterialTheme.typography.titleMedium)
                    Text("${extension.lang}  ·  ${extension.versionName}", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onAction(extension) },
                    enabled = !busy && !extension.isObsolete,
                ) {
                    Text(if (extension.hasUpdate) "更新" else if (extension.isInstalled) "卸载" else "安装")
                }
            }
            HorizontalDivider()
        }
        if (canLoadMore) {
            item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更多")
                }
            }
        }
    }
}
