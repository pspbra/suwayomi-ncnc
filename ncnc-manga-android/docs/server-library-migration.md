# 服务器资源库适配（2026-10-10）

依据后端 `suwayomi-server/docs/Server-Library-WebUI-APK-Migration.md`，保留原有底部导航、漫画封面网格、详情页和书架组件。

## 中断时的代码状态

- 已有 JWT 登录、按账号区分的漫画/章节 URL、阅读记录同步，以及“浏览”“添加”两个入口。
- 浏览仍查询 `inLibrary`，展示的是个人收藏，而不是共享资源库。
- “添加到服务器”调用个人收藏接口，然后调用已经停用的旧下载接口。
- 个人收藏仍主要写入本地数据库，尚未拉取完整的云端个人书架。

## 本次实现

- 书架只显示当前登录账号、统一服务器漫画源下的个人收藏。分页拉取云端 `inLibrary`；云端取消收藏会移除本地书架缓存，服务器资源库已移除但个人仍收藏的作品会保留。
- 新的本地收藏操作先检查资源库成员状态；收藏和取消收藏使用当前账号的 `updateManga`。操作持久保存至账号独立的待同步队列，约 2 秒后提交，失败时保留并重试。离线期间可取消已有收藏；新增收藏需要联网确认资源仍在库中。
- 启动和回到前台同步，首页处于前台时每 15 秒同步；书架下拉刷新改为同步个人数据。
- 浏览直接分页查询 `serverLibrary`，搜索交给服务器；打开浏览时刷新，浏览页面显示期间每 30 秒刷新。个人收藏按钮继续复用原有组件。
- 添加页面仍使用已安装在服务器的资源源。详情页只调用 `addServerLibraryManga`，不自动个人收藏、不自动下载，也不显示共享资源删除按钮。长按源漫画会打开详情页。
- 个人书架和详情页关闭下载/删除下载入口；禁用下载滑动操作、阅读器自动下载和读后删除。下载管理器也拒绝对账号漫画执行本地下载/删除。
- 手动标记已读/未读接入已有阅读进度队列，避免后续云端同步覆盖操作。原有阅读记录同步继续使用当前账号。
- 本次同步范围为收藏和阅读进度；分类编辑沿用 APK 的本地实现，未新增分类或书签的云端编辑接口。

## 验证

- 构建命令：`./gradlew.bat :app:assembleRelease --console=plain`。
- 最终结果：`BUILD SUCCESSFUL in 1m 37s`；`apksigner verify --verbose` 通过，APK v2 签名有效。
- APK：`app/build/outputs/apk/release/app-arm64-v8a-release.apk`。
- 包名仍为 `app.ncncmanhua`，版本名 `0.20.9`、版本号 `37`，仅构建 arm64-v8a；文件 SHA-256：`FC3A88583F638280D4D631471E8A7F345E089DB7B9BE85427AA4E229E9CE9699`。
- `tools/verify_suwayomi_contract.py` 已补充共享资源库与个人收藏状态的只读检查；运行该脚本需要服务器地址及测试账号，脚本不会输出或保存凭据。
- 未连接 Android 设备，未进行实机 UI 或两个账号的服务器联调。必须先部署包含新接口的后端。

建议验收：账号 A 在添加页加入资源库 → A/B 的浏览均可见但个人书架尚未收藏 → A 从浏览收藏 → A 的其他设备或 WebUI 可见 → B 的书架仍为空 → A 取消收藏 → 共享漫画及下载仍保留 → 切换账号时书架不串号 → 前台等待 15 秒或下拉刷新可看到 WebUI 收藏变更。

---

# 下载归属拆分（2026-11-13）

上一版为了避开停用的个人下载接口，把所有下载入口都关掉了。本次按后端的实际模型拆开：
**服务器资源库的下载在服务器上执行、由所有账号共享；只有个人书架才下载到本机。**

## 判定方式

- 是否走服务器下载由「从哪个页面进入详情页」决定，而不是看是否已收藏：
  `source is SuwayomiBaseSource && isFromSource`（浏览页、添加页的源浏览都传 `fromSource = true`，书架不传）。
- 同一个账号从浏览页打开和从书架打开同一个标题，会分别得到服务器视图和本机视图。

## 接口

| 用途 | GraphQL | 权限 |
| --- | --- | --- |
| 服务器下载队列 | `downloadStatus` | 仅管理员（客户端降级处理） |
| 提交下载 | `enqueueServerLibraryDownloads` | `DOWNLOAD_CHAPTERS` |
| 取消排队 | `dequeueChapterDownloads` | 仅管理员 |
| 删除共享文件 | `deleteServerLibraryDownloads` | `MANAGE_SERVER_LIBRARY` |

`chapters` 查询补了 `isDownloaded`，`serverLibrary` 保留 `chapterCount / downloadedChapterCount / queuedChapterCount`。
账号角色通过 `user { roles permissions }` 读取，用于决定展示与提示。

## 界面

- **浏览页（服务器资源库）**：卡片显示 `已下载/总章节` 与 `+队列数`。数据顺带由分页源写入 `SuwayomiSourceCache`，详情页操作后也会回写，不需要额外请求。
- **添加页（源浏览）**：命中资源库的结果显示「服务器」标记，其余不显示，因为不在资源库的标题无法由服务器下载。
- **详情页（服务器上下文）**：章节行显示服务器状态（排队/下载中进度/已下载/失败），章节头部显示一行「下载由服务器执行 · 已下载 x/y · 队列 n」。下载、取消、删除均转为上述 mutation。
- **个人书架**：恢复本机下载，`SuwayomiBaseSource` 不再被排除（`getPageList` / `imageRequest` 本来就支持）。

## 已知限制

- 非管理员看不到队列，只能看到 `isDownloaded`（每 10 秒轮询章节列表），因此排队中的章节在非管理员设备上显示为「未下载」。
- 非管理员点「取消」「删除」会收到权限提示，按钮未按权限隐藏。
- 未实现独立的「服务器下载队列」页面（WebUI 有，且仅管理员）。
- 服务器队列用轮询而非订阅：`downloadStatusChanged` 需要 WebSocket，客户端现有的手动 JSON 封装没有 WS 通道。

## 浏览页只剩几部的修复（2026-11-13）

现象：从详情页退回浏览页，列表有时只剩几部，切到别的 tab 再回来又正常。

两处叠加导致：

1. `BaseSourcePagingSource.getRefreshKey` 返回 `anchorPage.prevKey ?: anchorPage.nextKey`。
   因为 `prevKey` 恒为 `null`，实际返回的永远是**下一页**，而 Paging 会把它当成刷新的起始页号。
   于是每次 `refresh()` 都把窗口往后推一页，滚得越深推得越远。
2. `BrowseSourceScreen` 里 `LaunchedEffect(accountKey, source)` 每次重新进入组合（含从详情页返回）
   都调用 `mangaList.refresh()`，另外还有 `while (true) { delay(30s); refresh() }`。

二者结合，窗口自己往后走；走到接近集合末尾时那一页只剩几条、`hasNextPage` 也变 false，列表就停住了。

改法：

- `load()` 中 `LoadParams.Refresh` 一律从第 1 页开始；`getRefreshKey()` 返回 `null`（刷新不保留页号）。
- 去掉 30 秒轮询；仅在登录账号变化时刷新。从详情页返回不再刷新 —— 下载角标来自
  `SuwayomiSourceCache`，本来就会实时更新，不需要重载列表。

## 构建环境：AIDL 与中文路径（2026-11-13）
`compileDebugAidl` / `compileReleaseAidl` 在本机必失败：

```
MalformedInputException: Input length = 1
  at DependencyData.processDependencyData(DependencyData.java:118)
  at AidlCompile$DepFileProcessor.processFile(AidlCompile.kt:106)
```

原因不在 AIDL 文件本身（内容是纯 ASCII）。aidl.exe 用**系统代码页（GBK）**写 `.d` 依赖文件，
而 AGP 用**硬编码 UTF-8**（`Files.lines(path, Charsets.UTF_8)`）读它，工程路径含中文就会解码失败。

处理方式：把唯一那个接口的生成物固化为源码，并关闭 AIDL 编译步骤。

- `app/build.gradle.kts` → `buildFeatures { aidl = false }`
- `app/src/main/java/mihon/app/shizuku/IShellInterface.java`：登记的生成接口（含 Stub / Proxy / transaction id）
- `app/src/main/aidl/.../IShellInterface.aidl` 保留为定义，但不参与编译

代价：改这个 AIDL 时要手动重新生成并替换 Java 文件。收益：任意路径、任意机器都能构建，不需要 subst / ASCII 路径。

## 验证

- `./gradlew.bat :app:assembleRelease --console=plain` → `BUILD SUCCESSFUL in 4m 8s`（含 R8）。
- 产物 `app/build/outputs/apk/release/app-arm64-v8a-release.apk`（50 MB），包名 `app.ncncmanhua`，
  `versionName 0.20.9` / `versionCode 37`，`apksigner verify` 通过（复用 debug 证书）。
- 未连接设备，未与后端联调。

