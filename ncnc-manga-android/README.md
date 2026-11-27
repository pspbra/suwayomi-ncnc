# ncnc漫画 · Android 客户端（Mihon + Suwayomi）

本项目是在 Mihon 基础上定制的 Android 漫画阅读器。Suwayomi 漫画源已直接内置到应用中，客户端无需再安装 `tachiyomi-extension`，也不使用 Suwayomi WebUI；漫画库、章节、图片、服务器图源和服务器扩展均通过 Suwayomi GraphQL API 访问。

应用显示名（桌面图标名称）由 `i18n/src/commonMain/moko-resources/base/strings.xml` 里的 `app_name` 决定，当前为 **ncnc漫画**；包名 `applicationId = "app.ncncmanhua"`（在各 build type 下带 `.dev` 等后缀）独立设置，改显示名不影响包名、升级和已存数据。

## 版本管理注意事项

- 当前应用版本为 `0.20.10`，`versionCode` 为 `38`。
- **每次更改项目代码或功能时，都必须同步将版本号向前递增一次**：`versionCode` 加 `1`，并将 `versionName` 的末位版本加 `1`。版本配置位于 `app/build.gradle.kts`。

注意 `app_name` 还兼作新手引导里**默认存储目录**的目录名（`AndroidStorageFolderProvider`，即 `/storage/emulated/0/<app_name>`）。已有安装的目录记在偏好 `storage_dir` 里，且引导的存储步骤只有在用户显式选过目录后才会放行（`isSet()` = 键存在），因此改名不会让老安装“丢失”漫画；只有全新安装会看到新目录名，且用户可以自己另选。

修改代码后不进行编译，提醒进行编译即可

## 服务器配置

当前开发和生产联调服务器：

| 配置 | 值 |
| --- | --- |
| 服务器地址 | `https://mangasub.filexiazaiwr.work:82` |
| GraphQL 地址 | `https://mangasub.filexiazaiwr.work:82/api/graphql` |
| 认证方式 | `ui_login`（多用户 JWT） |

账号由服务器管理员创建，登录时填写自己的用户名和密码。项目不保存测试账号密码。登录成功后仅把服务器地址、用户名、用户 ID、访问令牌和刷新令牌保存在应用私有 `SharedPreferences` 中；不再保存密码。当前未额外使用 Keystore 加密令牌。

重新配置服务器：**更多 → 设置 → 服务器**（`SettingsServerScreen`）。该页面使用与其他设置页一致的标题栏、分组和滚动布局，保存时会先验证连接。

## 当前功能

底部导航栏结构：

1. **书架**：Mihon 本地书架。
2. **浏览**：直接进入内置 Suwayomi 源，展示服务器书库中的漫画。
3. **历史**：当前服务器账号的云端阅读历史与本机阅读记录，自动同步。
4. **添加**：管理服务器图源、浏览远程图源漫画、给服务器添加漫画以及管理服务器扩展。
5. **更多**：保留 Mihon 原有设置和工具。

具体能力：

- 首次启动时在引导流程的服务器步骤中验证并保存 Suwayomi 连接信息。
- 浏览服务器书库中的漫画。
- 搜索服务器书库。
- 获取漫画详情、章节列表和阅读图片。
- 图片请求自动携带 Bearer JWT。
- 查看服务器已经安装的图源。
- 点开图源后进入与“浏览”标签页完全一致的原生浏览页，浏览、搜索服务器扩展中的漫画。
- 在漫画详情页用「添加到服务器」把图源返回的漫画加入 Suwayomi 服务器书库，之后可在“浏览”页面把它加入 Mihon 本地书架。
- 查看服务器扩展仓库。
- 安装、更新和卸载服务器扩展。
- 如果设备仍安装旧 Suwayomi 客户端扩展，内置源会优先于外部扩展。

## 代码结构

### Suwayomi 内置功能

```text
app/src/main/java/eu/kanade/tachiyomi/source/suwayomi/
├── SuwayomiServerPreferences.kt  # URL、用户名、密码的持久化
├── SuwayomiServerApi.kt          # 原生 GraphQL API 客户端及服务器 DTO
├── SuwayomiAuthInterceptor.kt    # 给 Coil 图片请求补上 Bearer JWT
├── SuwayomiBaseSource.kt         # 两个 HttpSource 共用的详情/章节/图片逻辑
├── SuwayomiSource.kt             # 服务器书库适配器（"浏览"标签页）
├── SuwayomiRemoteSource.kt       # 服务器上单个图源的代理源
├── SuwayomiSourceCache.kt        # 服务器图源列表的持久化
└── SuwayomiSetupDialog.kt        # 旧版升级时补全服务器配置的 Compose 对话框
```

#### `SuwayomiServerPreferences.kt`

- SharedPreferences 文件：`suwayomi_server`
- 键：`url`、`username`、`user_id`、`access_token`、`refresh_token`；密码仅用于登录，不持久化。
- 自动去除 URL 结尾的 `/`。
- 未输入协议时自动补充 `https://`。

#### `SuwayomiServerApi.kt`

这是服务器访问的统一入口：

- 基于 Mihon 已有的 `OkHttpClient`。
- 每次请求动态读取当前凭据并添加 Bearer JWT，因此修改服务器配置后不需要重启应用。
- 请求地址固定为 `${serverUrl}/api/graphql`。
- 使用 `kotlinx.serialization.json` 解析 GraphQL 返回值。
- GraphQL 返回 `errors` 时抛出第一个错误消息。
- 相对图片 URL 统一转换为服务器绝对 URL。

主要 DTO：`ServerManga`、`ServerChapter`、`ServerSource`、`ServerExtension` 和 `ServerMangaPage`。

#### `SuwayomiSource.kt`

把服务器书库包装成 Mihon 原生 `HttpSource`，继承 `SuwayomiBaseSource`：

- 源名称：`Suwayomi`
- 语言：`all`
- 固定源 ID：`3100117499901280806`
- 每页 40 部漫画。
- `getPopularManga()`：按标题展示服务器书库。
- `getLatestUpdates()`：按加入服务器书库时间倒序。
- `getSearchManga()`：搜索标题、作者、画师和简介。
- `getMangaUpdate()`：获取详情和章节。
- `getPageList()`：获取章节图片。
- `imageRequest()`：图片请求携带 Bearer JWT。

固定 ID 与原 Suwayomi Tachiyomi 扩展一致，便于兼容之前由该扩展建立的书架数据。

#### `SuwayomiBaseSource.kt`

`SuwayomiSource` 与 `SuwayomiRemoteSource` 共用的抽象基类：`client`、`baseUrl`、`getHomeUrl()`、`getMangaUpdate()`、`getPageList()`、`getMangaUrl()`、`getChapterUrl()`、`imageRequest()` 和 `headersBuilder()`。

抽基类而不是各写一份，是为了让章节 URL 的约定（`account/<账号标识>/<mangaId>/<chapterId>`，由 `toSChapter` 产生、由 `getPageList()` 解析）只有一处定义——两处定义必然会发散，而发散的后果是阅读器取不到图片。

#### `SuwayomiRemoteSource.kt`

把服务器上安装的单个图源（如"拷贝漫画 (ZH)"）代理成 Mihon 原生 `HttpSource`，使"添加"里点开图源后能直接复用原生浏览页：

- 源 ID：直接取 Suwayomi 返回的 `SourceType.id`（`mihonSourceId()`）。Suwayomi 暴露的就是该源在 Tachiyomi 侧的 `Long` id，所以跨重启稳定，也与真机装同名扩展时的 id 一致。
- 源名称、语言、`supportsLatest` 均取自服务器。
- `getPopularManga()` / `getLatestUpdates()` / `getSearchManga()` 分别对应 `fetchSourceManga` 的 `POPULAR` / `LATEST` / `SEARCH`。
- 详情和章节沿用基类的 `manga(id)` / `chapters(id)`：`fetchSourceManga` 会把拉到的漫画写进服务器数据库（只是不加进书库），所以代理漫画同样查得到。
- 服务器扩展的动态筛选器仍未映射，`getFilterList()` 返回空，因此工具栏不显示筛选芯片。

#### `SuwayomiSourceCache.kt`

- SharedPreferences 文件：`suwayomi_sources`，存一份服务器图源列表（id、名称、语言、图标、`supportsLatest`、所属扩展）。
- 暴露 `StateFlow<List<ServerSource>>`，在"添加"页每次成功拉取图源后写入。
- `AndroidSourceManager` 用它重建代理源，因此浏览过的服务器漫画在**重启后仍能解析到源**——`NetworkToLocalManga` 把漫画写进本地库时记的是代理源 id，这个 id 必须在重启后依然有效，否则详情页和阅读器会失效。启动时不需要联网，读缓存即可。

#### `SuwayomiAuthInterceptor.kt`

- 只对协议、host、端口及路径前缀匹配服务器的请求添加 Bearer JWT，其他图片域名不携带令牌。
- GraphQL、阅读图片、下载与 Coil 共用 JWT 刷新逻辑；临近过期时刷新，并发刷新串行合并。401 / GraphQL Unauthorized 最多重试一次，403 不刷新。
- 通过动态读取 `SuwayomiServerPreferences`，修改服务器配置后无需重启。
- 注册位置：`SuwayomiServerApi.client` 与 `App.newImageLoader()`。
- 必要性：图源图标、扩展图标和漫画封面都是以纯 URL 交给 Coil 加载的，不走 `SuwayomiSource.client`，缺少该拦截器时 nginx 会返回 401。

### UI 和导航

```text
app/src/main/java/eu/kanade/tachiyomi/ui/
├── home/HomeScreen.kt
└── browse/
    ├── ServerBrowseTab.kt
    ├── ServerAddTab.kt
    └── source/browse/BrowseSourceScreen.kt
```

#### `HomeScreen.kt`

- 将原来的 `UpdatesTab` 替换为 `ServerBrowseTab`。
- 将原来的 `BrowseTab` 替换为 `ServerAddTab`。
- 未配置服务器且引导流程已完成时显示不可取消的 `SuwayomiSetupDialog`（例如从旧版本升级、保留了引导完成标记但没有服务器配置的用户）。全新安装的首次配置由引导流程的服务器步骤负责，因此该对话框不会在引导流程显示期间弹出。
- 原有内部导航请求 `Tab.Updates` 会打开新的“浏览”。
- 原有内部导航请求 `Tab.Browse` 会打开新的“添加”。

#### 引导流程

`eu.kanade.presentation.more.onboarding` 中的步骤依次为：主题、存储位置、服务器、权限、使用指引。

服务器步骤（`ServerStep.kt`）要求填入服务器地址、用户名和密码，点击“测试并保存连接”并通过 `SuwayomiServerApi.validate()` 验证后才能进入下一步。

该步骤的内容位于 `InfoScreen` 已经垂直滚动的列中，因此步骤自身不能再添加 `Modifier.verticalScroll`，否则会因无限高度约束直接崩溃。

#### `ServerBrowseTab.kt`

使用内嵌 Voyager `Navigator` 打开：

```kotlin
BrowseSourceScreen(
    sourceId = SuwayomiSource.ID,
    listingQuery = null,
    embedded = true,
)
```

因此漫画网格、搜索、详情页、章节页、阅读器、下载和加入 Mihon 本地书架等仍使用 Mihon 原生实现。

#### `BrowseSourceScreen.kt`

增加了 `embedded` 参数。作为底栏根页面时隐藏返回按钮；从其他页面压栈打开时维持 Mihon 原有返回行为。

#### 服务器图源漫画的详情页

从“添加 → 服务器图源”打开的漫画还没进本地书架（`SuwayomiRemoteSource` + `!manga.favorite`），详情页按此调整（`eu.kanade.presentation.manga.MangaScreen` 的 `onAddToServer` 参数）：

- 不显示「添加到书架 / 进度记录 / 追踪 / 在浏览器打开」那一整行，也不显示右下角「开始/继续阅读」悬浮按钮——这些动作都建立在「漫画已在本地书架」之上。
- 在连载状态（`连载中` / `已完结`）下方显示「添加到服务器」按钮，按钮下是一行说明：「添加到服务器后可在浏览页面收藏到自己书架」。
- 按钮有三种状态：可点 / 进行中（禁用 + 进度圈）/ 已添加（禁用变灰，文案变「已添加到服务器」）。已添加时旁边显示橙红色完整圆角「取消添加」按钮；宽度不足时该按钮会整体换行。点击后先显示「本取消将删除服务器漫画，请确定」，仅在用户二次确定后，才通过 `deleteDownloadedChapters` 删除该作品的服务器章节下载，再用 `updateManga(inLibrary: false)` 移出服务器书库。
- 按钮走 `MangaViewModel.addToServerLibrary()`：先 `updateManga(inLibrary: true)` 加入服务器书库，紧接着调 `downloadAllChapters()`，通过 `enqueueChapterDownloads` 把全部章节加入 Suwayomi 下载队列。书库入账成功即变灰；后续提交下载失败只提示「章节下载未完成」，不回滚按钮状态。
- 首次打开服务器图源中尚未被 Suwayomi 访问过的漫画时，若章节列表为空，会先调用 `fetchMangaAndChapters(fetchManga: true, fetchChapters: true)` 刷新服务器缓存，再同步到本地详情页，行为与手动「刷新」一致。
- 右上角菜单的「刷新」会先调用 `fetchMangaAndChapters(fetchManga: true, fetchChapters: true)`，强制 Suwayomi 从对应图源重新拉取作品详情和章节，再同步到本地详情页。
- 打开页面时若源是 `SuwayomiRemoteSource`，会查一次 `manga(id).inLibrary` 来初始化按钮状态——否则关掉再进来按钮又会变回可点。
- 阅读仍然可用：直接点章节列表即可，不依赖悬浮按钮。
- 一旦该漫画被加入本地书架（在“浏览”页面操作），详情页恢复 Mihon 原生样式。

其他入口（书架、历史、浏览）打开的漫画 `onAddToServer` 为 `null`，详情页行为完全不变。

#### `ServerAddTab.kt`

纯 Compose 原生页面，不加载 Suwayomi WebUI：

- `Content()` 里套了一层自己的 `Navigator`，与 `ServerBrowseTab` 一样，页面跳转留在本标签页内，底栏保持可见。
- “服务器图源”显示已安装在 Suwayomi 的图源，进入标签页时拉取一次并写入 `SuwayomiSourceCache`。
- 点击图源不再内联列出漫画，而是等代理源注册完成后 `navigator.push(BrowseSourceScreen(...))`，直接进入与“浏览”标签页**同一个**原生浏览页（`SuwayomiRemoteSource` 提供数据，列表数据来自 `fetchSourceManga`）。
- 初始 `listingQuery` 传 `QUERY_POPULAR`，即进入图源就展示热门；`null` 会退化成空关键词搜索，对远程图源结果不可预期。
- `awaitServerSource()` 会等 `AndroidSourceManager` 完成重建再跳转，避免解析成 stub 而显示 `MissingSourceScreen`。
- “服务器扩展”分页读取扩展仓库，每次增加 100 项，操作调用 `updateExtension`。
- 刷新按钮重新请求当前页面。

### 内置源注册

文件：`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt`

`AndroidSourceManager` 创建 `SuwayomiSource` 并放入 `sourcesMapFlow`。注册发生在外部扩展加载之后，确保旧 Suwayomi APK 即使仍然安装，也不会覆盖内置实现。

## 使用的 Suwayomi GraphQL API

所有请求均为：

```http
POST /api/graphql
Authorization: Bearer <accessToken>
Content-Type: application/json
```

### 验证服务器

```graphql
query ValidateServer {
  mangas(condition: { inLibrary: true }, first: 1) {
    totalCount
  }
}
```

配置页先调用 `login`，再查询 `user` 确认身份；两者成功后才原子保存新会话。上述查询可用来额外检查当前用户书库。

### 获取服务器书库

使用 `mangas(condition: { inLibrary: true }, first: 1000)`，读取 `id`、`title`、`artist`、`author`、`description`、`genre`、`status`、`thumbnailUrl`、`inLibrary`、`inLibraryAt` 和 `source.displayName`。

当前实现一次最多读取 1000 部服务器书库漫画，然后在客户端排序、搜索和分页。若服务器书库未来超过 1000 部，需要改成 GraphQL 游标或 `offset` 分页。

### 获取漫画详情

```graphql
query Manga($id: Int!) {
  manga(id: $id) {
    id
    title
    artist
    author
    description
    genre
    status
    thumbnailUrl
    inLibrary
    inLibraryAt
    source { displayName }
  }
}
```

### 获取章节

```graphql
query Chapters($id: Int!) {
  chapters(
    condition: { mangaId: $id }
    order: { by: SOURCE_ORDER, byType: DESC }
  ) {
    nodes {
      id
      mangaId
      sourceOrder
      name
      chapterNumber
      uploadDate
      scanlator
    }
  }
}
```

### 获取阅读图片

```graphql
mutation Pages($id: Int!) {
  fetchChapterPages(input: { chapterId: $id }) {
    pages
  }
}
```

服务器返回的图片路径经过 `absoluteUrl()` 转换；Mihon 阅读器随后通过带 Bearer JWT 的图片请求读取。

### 获取服务器图源

```graphql
query Sources {
  sources(first: 1000, order: { by: NAME }) {
    nodes {
      id
      name
      displayName
      lang
      iconUrl
      supportsLatest
      extension { pkgName }
    }
  }
}
```

本地文件源 `localsourcelang` 会在客户端过滤掉。

### 浏览服务器图源漫画

```graphql
mutation BrowseSource(
  $source: LongString!
  $page: Int!
  $query: String!
  $type: FetchSourceMangaType!
) {
  fetchSourceManga(
    input: {
      source: $source
      page: $page
      query: $query
      type: $type
    }
  ) {
    hasNextPage
    mangas {
      id
      title
      artist
      author
      description
      genre
      status
      thumbnailUrl
      inLibrary
      inLibraryAt
      source { displayName }
    }
  }
}
```

- 搜索词为空时使用 `POPULAR`。
- 搜索词非空时使用 `SEARCH`。
- `fetchSourceManga` 会让服务器调用其扩展并把返回漫画写入或更新到 Suwayomi 数据库，但不会自动加入书库。

### 添加漫画到服务器书库

```graphql
mutation AddManga($id: Int!) {
  updateManga(input: { id: $id, patch: { inLibrary: true } }) {
    manga { id }
  }
}
```

该操作只修改 Suwayomi 服务器书库。Mihon 本地书架与服务器书库是两个不同概念。

### 获取服务器扩展

```graphql
query Extensions($offset: Int!, $limit: Int!) {
  extensions(
    first: $limit
    offset: $offset
    order: { by: NAME }
  ) {
    nodes {
      pkgName
      name
      lang
      iconUrl
      versionName
      isInstalled
      hasUpdate
      isObsolete
    }
  }
}
```

### 刷新服务器扩展仓库

API 已封装为 `refreshExtensions()`，对应：

```graphql
mutation RefreshExtensions {
  fetchExtensions(input: {}) {
    clientMutationId
  }
}
```

### 安装、更新或卸载扩展

```graphql
mutation UpdateExtension(
  $id: String!
  $install: Boolean!
  $update: Boolean!
  $uninstall: Boolean!
) {
  updateExtension(
    input: {
      id: $id
      patch: {
        install: $install
        update: $update
        uninstall: $uninstall
      }
    }
  ) {
    extension {
      pkgName
      isInstalled
      hasUpdate
    }
  }
}
```

## 数据流

```text
Compose UI
   │
   ├── “浏览” ──> BrowseSourceScreen ──> SuwayomiSource ──┐
   │                                                     │
   ├── “添加” → 点图源 ──> BrowseSourceScreen ──> SuwayomiRemoteSource ──┤
   │       （与浏览同一套原生界面，只是数据源不同）      │
   │                                                     ├──> SuwayomiServerApi ──> GraphQL
   │                  两类源都用同一套 ───────────────────┤
   │                  SManga / SChapter / Page ──> Mihon 详情页和阅读器
   │
   └── “添加” ──> SuwayomiServerApi ──> 图源 / 扩展 / 服务器书库 Mutation
```

“浏览”与“添加”两条路径的封面网格、显示模式、长按菜单、分页全部来自同一个 `BrowseSourceScreen`；区别只在于 `sourceId` 指向 `SuwayomiSource`（服务器书库）还是某个 `SuwayomiRemoteSource`（服务器上的单个图源）。

## 开发环境

推荐环境：

- Windows 10/11 x64
- Android Studio 2026.1.4.7 或更新版本
- Temurin JDK 21
- Android SDK：`C:\Android\Sdk`
- Android SDK Platform 36（常规新项目）
- Android SDK Platform 37.2（本项目 `compileSdk`）
- Android Build Tools 36.0.0 和 37.0.0
- Android Platform Tools
- Android NDK 29.0.14206865

环境变量：

```text
JAVA_HOME=<Temurin JDK 21 安装目录>
ANDROID_HOME=C:\Android\Sdk
ANDROID_SDK_ROOT=C:\Android\Sdk
```

用户 `Path` 应包含：

```text
%JAVA_HOME%\bin
%ANDROID_HOME%\platform-tools
%ANDROID_HOME%\cmdline-tools\latest\bin
```

新终端中检查：

```powershell
java -version
adb version
sdkmanager --version
```

## 构建

本项目使用 Gradle Wrapper，无需额外安装 Gradle。Windows 下用 `gradlew.bat`，macOS/Linux 下用 `./gradlew`。

以下命令以 Windows 为例。代码修改完成后不必由工具自动编译，编译由开发者手动执行。

```powershell
# 代码格式检查
.\gradlew.bat :app:spotlessKotlinCheck

# 编译 Kotlin
.\gradlew.bat :app:compileDebugKotlin

# 生成 Debug APK
.\gradlew.bat :app:assembleDebug
```

主要输出：

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

`assembleDebug` 就是生成该 APK 的任务，没有 `assembleArm64V8aDebug` 之类的按 ABI 单独命名的任务。

`app/build.gradle.kts` 中的 `splits.abi` 目前只包含 `arm64-v8a`，且 `isUniversalApk = false`，所以只会产出上面这一个 APK。大多数现代 Android 手机使用 `arm64-v8a`；需要其他 ABI 或通用 APK 时修改该 `splits` 配置即可。

## 联调与排障

### 首次配置一直提示 401

- 确认服务器为 `ui_login`，输入的是 Suwayomi 多用户账号及密码。App 自动管理令牌，不填写 Token，也不支持代理层额外的 Basic 登录。
- 确认地址包含端口 `:82`。
- 地址只填写服务器根地址，不要填写 `/browse` 或 `/api/graphql`。

### 漫画列表正常但封面或阅读图片 401

- 检查 `SuwayomiAuthInterceptor` 是否完成登录和令牌刷新，且图片 URL 与配置的服务器协议、域名及端口匹配。
- 检查 `App.newImageLoader()` 是否仍注册了 `SuwayomiAuthInterceptor`：Coil 用纯 URL 加载图源图标、扩展图标和漫画封面，这条路径不走源客户端。
- 检查 nginx 是否对图片路径采用与 GraphQL 相同的认证配置。

### 单个图源报 Connection reset（对照图源正常）

典型现象：进入“添加 → 服务器图源 → 某个图源”后页面报错，换另一个图源却正常。

这类失败几乎都在服务器侧，与 App 无关，排查顺序：

1. 绕开 App 直接请求服务器复现：

   ```bash
   curl -i -H 'Authorization: Bearer <accessToken>' -X POST https://<服务器>/api/graphql \
     -H 'Content-Type: application/json' \
     -d '{"query":"mutation B($source: LongString!, $page: Int!, $query: String!, $type: FetchSourceMangaType!) { fetchSourceManga(input: { source: $source, page: $page, query: $query, type: $type }) { mangas { id title } } }","variables":{"source":"<源ID>","page":1,"query":"","type":"POPULAR"}}'
   ```

2. 堆栈停在 `SSLSocketImpl.startHandshake` / `SSLSocketInputRecord.readHeader`，说明是**服务器出口**在 TLS 握手阶段被重置，TCP 已连上但 ClientHello 被 RST，HTTP 请求根本没发出去——因此和请求头、Cookie、User-Agent、插件逻辑都无关。
3. 换一个图源做对照。对照源能返回结果，就说明服务器出网正常，问题只出在该站点。
4. 到服务器上直接验证同一个域名：

   ```bash
   dig +short www.manhuagui.com
   curl -v https://www.manhuagui.com/
   ```

   服务器上也 reset，就需要给服务器换出口（代理/隧道），或改用可用的镜像域名。
5. 漫画柜插件（1.4.28）的 `ConfigurableSource` 只提供限速和 R18 开关，**没有域名切换设置**，无法在插件内换域名绕开。

App 侧只负责展示，`SuwayomiServerApi.errorMessage()` 会剥掉 graphql-java 包装并只保留第一行原因，避免把整个 JVM 堆栈渲染到界面上。

### 修改服务器后仍显示旧内容

- 在“添加”右上角设置中重新保存并验证。
- 返回页面后点击刷新。
- `SuwayomiServerApi` 会动态读取新凭据，不需要强制停止应用。

### GraphQL 字段报错

服务器升级后如果 schema 改动，优先检查：

```text
app/src/main/graphql/mihon/graphql/suwayomi/suwayomi.graphqls
app/src/main/java/eu/kanade/tachiyomi/source/suwayomi/SuwayomiServerApi.kt
```

内置源目前使用手写 GraphQL 字符串，不依赖 Apollo 生成类；schema 文件仍可作为字段和类型参考。

## 已知限制和后续建议

- 只支持 `ui_login` 多用户模式，不再支持 `basic_auth` / `simple_login`。
- 登录令牌存放在应用私有 SharedPreferences，但未使用 Android Keystore 加密；密码不持久化。
- 服务器书库查询上限为 1000 部。
- 服务器图源浏览支持热门、最新和文字搜索，尚未映射服务器扩展的动态筛选器（`getFilterList()` 返回空，因此工具栏不显示筛选芯片）。
- 图源漫画列表按 `hasNextPage` 分页（每页 25 部，由 `PagingConfig` 决定）。
- 扩展列表在客户端文字过滤；数据量很大时可改为 GraphQL `filter` 服务端搜索。
- UI 文案目前直接使用中文字符串。若需要多语言，应迁移到 `i18n` 模块。
- 添加漫画和安装扩展会修改服务器状态，自动化测试应使用专用测试服务器。
- 服务器图源的代理源 id 直接取 Suwayomi 的 source id。若手机上也装了同 id 的扩展，**扩展优先**，此时从服务器侧拉到的漫画（`url` 是服务器漫画 id）在该扩展下打不开详情——这是刻意取舍，反过来遮蔽用户的扩展影响更大。
- 服务器图源会出现在 `SourceManager.getOnlineSources()` 里，因此也会进入设置里的源列表和迁移目标。**全局搜索已排除它们**（`SearchViewModel.getEnabledSources()` 里 `filterNot { it is SuwayomiRemoteSource }`）——否则一次全局搜索会让服务器把每个扩展的搜索都跑一遍。迁移搜索走自己的 `getEnabledSources()` 重写，不受影响。
- 详情页的「添加到服务器」会触发服务器逐章解析图片，章节多时耗时较长，期间会一直保持禁用状态；若中途离开详情页，协程随 `viewModelScope` 取消，已解析的章节仍然有效。

## 多用户与阅读同步（0.20.9）

详见 [多用户接入与验收说明](docs/multi-user-auth.md)。

- 登录调用 `login` / `user`，刷新调用 `refreshToken`。GraphQL、封面、阅读图片和下载统一发送 Bearer Token。
- 翻页和退出阅读器把页码、已读状态保存到按账号隔离的本地待同步队列，再调用 `updateChapter`；服务端自动写入 `lastReadAt`。
- 打开漫画/阅读器时拉取进度；主页在前台启动、恢复及每分钟拉取当前账号历史，支持历史分页和新设备恢复。
- 本地漫画 URL 使用 `account/<SHA-256(服务器地址+用户ID)>/<mangaId>`，章节 URL 再追加章节 ID。书架、历史按账号过滤，切换账号不删除旧数据，也不上传旧账号队列。
- 旧版无账号标识的本地 Suwayomi 数据保留，但不自动归属到新用户；升级后请重新登录，从“浏览”打开并收藏需要的漫画。服务端已有历史会自动恢复，旧本机历史不会自动上传。
- 离线队列在再次使用 App、恢复前台或手动同步时重试；系统杀进程后队列仍在。匿名阅读不上传。
- 多设备冲突比较服务器 `lastReadAt` 和本机事件时间，较新的事件优先；服务器没有原子版本比较接口，极短时间内的并发写入仍以最终写入为准。离线事件的云端历史时间是服务端接收时间，阅读时长仍仅在本机保存。
- 历史删除只在当前设备隐藏；服务器当前 GraphQL 没有历史时间清除接口，因此不会伪装成云端删除。再次阅读会恢复显示。
- “更多 → 设置 → 服务器”可查看同步状态、立即同步或退出账号。

## 验证记录（2026-10-09）

使用实际部署服务器完成只读检查：JWT 登录、用户身份、刷新令牌、个人历史分页、单章与章节列表进度、封面鉴权。`updateChapter` 的参数及返回字段通过 GraphQL `@skip(if: true)` 校验，未运行写入处理器，未修改真实阅读记录。复现脚本：`tools/verify_suwayomi_contract.py`，通过环境变量或交互提示提供凭据，不记录令牌。

按照本项目要求，没有编译 App 或生成 APK。全模块 Spotless 检查遇到原有 Compose 函数命名及 CloudflareInterceptor 注释位置问题；本次修改的 21 个 Kotlin 文件已通过定向格式检查。手机实际翻页写入、断网重试、两账号切换、进程重启恢复仍需编译安装后验收。

## 许可证

本项目基于 [Mihon](https://github.com/mihonapp/mihon) 修改，遵循 Apache License 2.0，全文见 [LICENSE](LICENSE)。

```text
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
```

第三方依赖与上游组件的版权声明、许可证以各依赖自带的声明为准。
