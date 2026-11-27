# 服务器资源库：后端行为与 WebUI / APK 适配

日期：2026-10-09。本次在已有服务器资源库实现上修正升级迁移、补齐模式切换兼容和同步入口校验，并记录完整客户端契约。本文中的页面调整需要后续在 WebUI / APK 实现；本仓库后端接口不会自动为旧客户端增加页面。

## 1. 数据归属与页面流程

资源端 → **添加到服务器资源库** → 在服务器资源库选择 **添加到我的书架**。

- 服务器资源库是全站共享集合，使用 `MangaTable.inServerLibrary` 和 `serverLibraryAddedAt`，不属于任何用户。
- 个人书架、分类、阅读进度和书签仍按用户隔离。`inLibrary` / `user.inLibrary` 仅表示当前用户的个人收藏，不能用来判断服务器收藏。
- 所有下载以服务器为单位共享。章节下载状态来自 `ChapterTable.isDownloaded`，不记录“是谁下载的”。
- 个人书架只保留收藏管理、阅读和进度功能；移除个人收藏不会删除服务器资源或下载。
- 从服务器资源库移除作品不会清除其他用户的个人收藏或历史。保留的个人收藏仍可显示；客户端可用 `inServerLibrary = false` 标识“已从服务器资源库移除”。
- 管理人员移除服务器作品时可选择保留下载，或同时删除下载。两种操作都会撤销该作品待下载任务。保留的下载仍占用磁盘，可通过服务器下载删除接口清理。
- 客户端必须移除个人书架、阅读器和个人批处理中的下载/删除下载入口；后端不能辨别调用来自哪个页面，新的服务器资源库接口是操作边界。

## 2. GraphQL 接口

入口沿用 `/api/graphql`，沿用当前登录凭据；`userId` 由服务器从认证上下文提供，不是客户端参数。

### 资源库列表

```graphql
query {
  serverLibrary(offset: 0, limit: 50, search: "") {
    totalCount
    hasNextPage
    items {
      manga {
        id
        title
        thumbnailUrl
        sourceId
        inServerLibrary
        serverLibraryAddedAt
        inLibrary
      }
      chapterCount
      downloadedChapterCount
      queuedChapterCount
    }
  }
}
```

`offset >= 0`，`limit` 范围为 1–200；可选 `sourceId`，`search` 按标题匹配。列表按加入服务器资源库时间倒序、漫画 ID 倒序排列。时间戳单位为秒。

`chapterCount` 为当前已缓存的章节数；首次加入时元数据/章节更新可能仍在队列中。`queuedChapterCount` 是该漫画当前下载队列中的章节数，不表示某个用户的任务。普通客户端可轮询此列表获取下载变化，不订阅管理员全局队列。

返回的 `MangaType` 可查询当前用户自己的状态，但没有其他用户的收藏人、下载人或阅读进度。资源库卡片建议只展示封面、标题、来源、章节总数、已下载数和待处理数；个人收藏状态只用于“添加到我的书架”按钮。

现有 `mangas` 查询也支持 `condition.inServerLibrary` / `filter.inServerLibrary`，可继续复用已有书架排序和分页组件。个人书架继续使用当前用户的 `inLibrary` 筛选。

### 修改接口

| 操作 | 接口及输入 | 权限 / 语义 |
| --- | --- | --- |
| 从资源端加入服务器 | `addServerLibraryManga(input: { mangaId })` | 已登录；幂等；不会自动加入个人书架 |
| 从服务器加入个人书架 | `updateManga(input: { id, patch: { inLibrary: true } })` | 已登录；漫画必须已经在服务器资源库中 |
| 移除个人收藏 | `updateManga(input: { id, patch: { inLibrary: false } })` | 只影响当前用户 |
| 服务器下载 | `enqueueServerLibraryDownloads(input: { chapterIds })` | `DOWNLOAD_CHAPTERS`；漫画必须在服务器资源库中 |
| 移除服务器作品 | `removeServerLibraryManga(input: { mangaId, deleteDownloads: false })` | `MANAGE_SERVER_LIBRARY`；默认保留文件 |
| 移除作品及下载 | 同上，`deleteDownloads: true` | `MANAGE_SERVER_LIBRARY` |
| 只删除服务器下载 | `deleteServerLibraryDownloads(input: { chapterIds })` | `MANAGE_SERVER_LIBRARY`；也能清理已移除作品的保留下载 |
| 更新整个服务器资源库 | `updateServerLibrary` | 管理员；元数据请求仍遵循缓存 TTL |

上述带 `input` 的新服务器资源库接口都支持可选 `clientMutationId`，结果原样返回该值。作品操作返回 `manga`；下载操作返回 `chapterIds`。入队会去重并过滤已下载章节；返回的是本次接受的未下载章节 ID，不能据此推断任务所属用户或已下载完成。

示例：

```graphql
mutation {
  addServerLibraryManga(input: { mangaId: 123 }) {
    manga { id inServerLibrary }
  }
}
```

```graphql
mutation {
  enqueueServerLibraryDownloads(input: { chapterIds: [456, 457] }) {
    chapterIds
  }
}
```

客户端在操作成功后重新查询资源库和相关章节，避免继续使用旧书架的本地下载归属状态。

### 旧接口兼容边界

- 旧 `enqueueChapterDownload` / `enqueueChapterDownloads` 以及个人 REST 下载入口不再接受下载，提示使用 `enqueueServerLibraryDownloads`。
- 旧个人下载删除接口不再删除共享文件，提示使用 `deleteServerLibraryDownloads`。
- 旧个人收藏接口不会把资源端作品直接加入书架；作品尚未进入服务器资源库时会失败，客户端应先提供服务器收藏操作，再在资源库提供个人收藏操作。
- REST 客户端应为新的资源库操作增加 GraphQL 调用；本次没有增加平行的资源库 REST 接口。
- 同步收藏也不能绕过资源库成员检查。备份导入沿用现有实现：收藏作品先进入服务器资源库，个人收藏是否恢复取决于已有资源库成员状态；它不是普通书架的下载入口。

## 3. 权限和隐私

`MANAGE_SERVER_LIBRARY` 是资源删除权限，管理员自动具备，可授予指定用户。`DOWNLOAD_CHAPTERS` 控制共享下载。普通用户默认不会因能下载而获得删除共享资源的权限。

以下全局能力仅管理员可用，不应在普通用户页面查询：

- `downloadStatus`、`downloadChanged`、`downloadStatusChanged`。
- `updateStatus`、`libraryUpdateStatus` 及对应更新订阅。
- 全局下载器启动、停止、清空、重排和撤销任务操作。
- `serverLibraryStorage`：`downloadBytes`、`availableBytes`、`totalBytes`、`measuredAt`。

磁盘数值单位为字节；`downloadBytes` 遍历服务器下载目录统计，包含已从资源库移除但保留的文件，不等于整个数据目录大小。`availableBytes` / `totalBytes` 指下载目录所在磁盘。统计需要遍历文件，不建议高频轮询。

普通用户只展示作品级共享下载状态和自己的阅读进度。不要将某个管理员的书架或进度作为公共展示数据。

## 4. 全局缓存与请求合并

资源请求不以用户 ID 作为缓存键。所有用户、后台更新与下载共同使用服务端缓存：

| 数据 | 缓存位置 / 合并方式 | 设置与默认值 |
| --- | --- | --- |
| 热门、最新、搜索和筛选列表 | 进程共享缓存；相同键的并发请求合并 | `sourceBrowseCacheTtl`，12 小时 |
| 漫画详情与章节列表 | 数据库缓存；漫画级互斥，进入锁后再次判断新鲜度 | `sourceMetadataCacheTtl`，15 分钟 |
| 章节分页和解析后的图片地址 | 数据库缓存；章节级互斥 | `sourcePageCacheTtl`，5 分钟 |
| 图片和封面 | 共享文件缓存；同一路径互斥加载 | 命中文件时直接读取；持久下载优先 |

搜索缓存键包含来源、操作、页码、搜索词和筛选条件指纹。普通“刷新”不会绕过有效 TTL。旧的列表 TTL `0s` 在共享缓存中按至少 1 秒处理。无法安全序列化的筛选状态会报错，不会偷偷改为直连资源端。

首次访问、缓存过期或缓存不存在时会加载资源；缓存不是离线镜像。列表缓存有 2000 项容量限制，重启、容量淘汰、管理员清理或来源配置变更也可能导致重新加载。来源配置变更会使相关缓存失效。图片文件缓存以文件存在性为准，不使用元数据 TTL；本地漫画源不受远端列表缓存约束。

客户端不要实现按用户强制刷新来源的策略；服务器返回有效缓存即为成功结果。

## 5. 升级与模式切换

`M0067_ServerLibrary` 初始化共享字段：

- 单用户模式：使用原用户 1 的书架。更早的单用户版本会先由 M0066 将旧书架迁入用户 1。
- 多用户模式（`UI_LOGIN`）：只使用唯一 `ADMIN` 的书架，不合并普通用户收藏，也不因为其他用户下载过作品就自动加入资源库。
- 保留所有个人书架、阅读记录和现有下载文件；共享下载状态保留在章节表，旧按用户记录的下载标志被清空。
- 不是恰好一个管理员时不任意选取某个账号，不合并其他用户书架。

新增 `M0068_ServerLibraryMode` 保存上次模式。启动和运行中切换认证模式时都会检查：从单用户切到多用户，补入用户 1 的原书架；从多用户切回单用户，补入唯一管理员的原书架。只补入尚未在资源库中的作品，不替换已有资源，不删除任何下载。单用户的不同认证方式之间切换不重复执行。

相同模式下重启不重新导入，避免已删除作品被自动恢复。跨模式切换会再次补入原模式书架中仍然收藏的作品，这是兼容导入的预期行为。

如果数据库已经执行过旧版 M0067，修改迁移文件不会使它再次执行；本次不会自动删除这类数据库中已经共享的作品，避免误删后续正常加入的资源。该情况下可由有权限的用户在资源库中整理，新增 M0068 仍会执行。

## 6. 本次修改与验证

- 修正 M0067 的初始来源：由“全部用户收藏及下载作品”改为指定旧书架。
- 新增 M0068 和 `ServerLibraryMode`，处理运行中及停机后配置修改导致的模式切换。
- 启动迁移和配置订阅接入模式兼容处理。
- 同步收藏变更增加服务器资源库成员检查。
- 新增 `ServerLibraryMigrationTest`，覆盖单用户、唯一管理员、下载/个人书架保留、模式切换补入和同模式重启不恢复已移除作品。
- 本次只进行了代码静态检查，没有执行 Gradle、编译或测试。已有资源库、下载、权限和缓存实现也需要在自行编译后联调。

建议联调顺序：两个普通用户共享同一作品 → 重复下载 → 各自收藏和阅读 → 单人移除个人收藏 → 无权限删除失败 → 管理员保留/删除下载两种移除 → 普通用户读取全局任务失败 → 两用户同时查询同一资源命中缓存 → 升级和模式切换。

## 7. 自行编译

在仓库根目录使用 JDK 21，在 PowerShell 执行：

```powershell
.\gradlew.bat :server:shadowJar
```

输出 JAR 位于 `server/build/`。如需运行新增回归测试（会触发测试编译）：

```powershell
.\gradlew.bat :server:test --tests "suwayomi.tachidesk.server.database.migration.ServerLibraryMigrationTest"
```
