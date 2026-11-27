# 服务器资源库 WebUI 适配交接

记录日期：2026-10-09（第二轮：接手并完成实现）。

第一轮只完成了接口定义、页面初稿和权限枚举补充；本轮把 A–E 全部实施完成，并通过类型检查、lint、格式检查和生产构建。

## 1. 当前状态

**代码已完成，静态验证通过；尚未与运行中的后端联调，也尚未在浏览器中验证。**

已执行并通过：

- `npx tsgo --noEmit`（类型检查，0 错误）
- `npm run lint`（oxlint，0 错误）
- `oxfmt --check`（仅对本次变更的 57 个文件）
- `npx vite build`（生产构建成功）

未执行：

- `pnpm gql:codegen`：`src/lib/graphql/schema.json` 是旧快照，不含 `serverLibrary` 系列字段，且本机没有运行后端可以重新生成。见第 6 节。
- 任何浏览器/运行时验证，任何后端联调。
- `pnpm test` 只是输出 `imagine`，没有实际测试覆盖。

## 2. 后端契约（依据）

`../suwayomi-server/docs/Server-Library-WebUI-APK-Migration.md`

本轮逐条核对了后端源码，确认了以下事实（与第一轮的记录一致）：

- `ServerLibraryQuery.serverLibrary`：`offset >= 0`、`limit` 1–200、可选 `search`/`sourceId`；按 `serverLibraryAddedAt` 倒序。
- `serverLibraryStorage`、`updateServerLibrary`、`libraryUpdateStatus`、`updateStatus`、`downloadStatus` 都调用 `ServerLibrary.requireAdmin(userId)`，普通账号一律被拒绝。
- 旧接口现状：`enqueueChapterDownload(s)` 直接 `throw IllegalArgumentException("Use enqueueServerLibraryDownloads from the server library")`；`Chapter.deleteDownloadedChapters` 直接 throw；`dequeueChapterDownload(s)`、`startDownloader`、`stopDownloader`、`clearDownloader`、`reorderChapterDownload(s)` 都是 admin-only。
- `lastUpdateTimestamp` 只要求登录，不要求管理员。
- 单用户模式下请求用户是用户 1（`ADMIN`），所以 `user` 查询不需要令牌也能成功。

## 3. 本轮实际完成的修改

### A. 路由、导航与新页面

- `src/base/AppRoute.constants.ts`：新增 `serverLibrary`（`/server-library`）及其子路由 `children.manga`（`/server-library/:mangaId`）。
- `src/App.tsx`：按需加载并接入两个页面；`/downloads` 用 `ServerAdminGuard` 包裹；新增 `<AdminSessionGate />`。
- `src/features/navigation-bar/NavigationBar.constants.ts`：新增“Server library”入口（`show: 'desktop'`，因此出现在桌面侧栏和移动端 More 页，不占用移动底栏）；个人书架入口改名 `My library`；下载入口增加 `useIsVisible`，非管理员不显示。
- `NavigationBar.types.ts` / `NavigationBarItem.tsx`：新增可选 `useIsVisible`，`NavigationBarItem` 在不满足时返回 `null`（hook 调用顺序保持不变）。
- `src/features/settings/screens/More.tsx`：改为渲染 `NavigationBarItem`，不再在循环里直接调用 `useBadge`。
- `Settings.tsx`、`Library.tsx`、`LibrarySettings.tsx`：个人书架文案统一为 `My library` / `我的书架`。

### B. 来源添加改为共享收藏

- 新增 `src/lib/graphql/server-library/`（`ServerLibraryFragments.ts` / `ServerLibraryQuery.ts` / `ServerLibraryMutation.ts`），放在 `gql_codegen.ts` 的 documents 扫描范围内。文档是手写 `TypedDocumentNode` 加局部类型，因为当前无法运行 codegen。
- `MANGA_BASE_FIELDS` 增加 `inServerLibrary`、`serverLibraryAddedAt`；`Manga.types.ts` 新增 `MangaInServerLibraryInfo`。
- 新增 `src/features/server-library/hooks/useManageServerLibraryState.ts`：`addToServerLibrary()` 调用 `addServerLibraryManga`，幂等，不会加入个人书架，成功后重查资源库列表。
- `MangaCard`（source 模式点击/长按）与 `MangaBadges`（source 模式悬停按钮）改为调用 `addToServerLibrary`；徽标分别显示 `In server library` / `In my library`。
- `MangaDetails`：`inServerLibrary` 为真时显示个人收藏按钮（沿用原有 `In Library` / `Add To Library` 文案），否则显示“Add to server library”；个人已收藏但服务器已移除时给出说明；新增常驻的“Shared downloads”链接，用于进入服务器章节管理页清理保留文件。
- `useManageMangaLibraryState`：`isInLibrary` 的同步 effect 依赖增加到 `manga.inLibrary`；新增 `isInServerLibrary` 返回值；在作品尚未进入服务器资源库时阻止个人收藏并给出明确提示。

### C. 移除个人下载/删除入口与副作用

删除的文件：

- `chapter/components/buttons/ChapterDownloadButton.tsx`、`ChapterDownloadRetryButton.tsx`
- `chapter/components/actions/ChaptersDownloadActionMenuItems.tsx`
- `downloads/components/DownloadAheadSetting.tsx`、`DeleteChaptersWhileReadingSetting.tsx`
- `downloads/Downloads.types.ts`、`downloads/Downloads.constants.ts`

移除的操作：

- `Chapters.download` / `delete` / `cancelDownload`、`Chapters.performAction` 的 `download`/`download_cancel`/`delete` 分支、`ChapterAction` 对应的三个取值。
- `Mangas.downloadChapters` / `deleteChapters`、`MangaAction` 的 `download`/`delete`、以及只为它们存在的 `isNotDownloaded`/`isFullyDownloaded`/`isPartiallyDownloaded` 等辅助方法。
- `MangaActionMenuItems` 的下载子菜单和“删除下载”；`ChapterActionMenuItems` 的下载/取消/删除；`ChaptersToolbarMenu` 的下载菜单；`ReaderNavBarDesktopActions` 的下载/删除按钮；`ChapterDownloadButton`/`RetryButton` 在更新、历史、下载队列卡片中的全部使用。
- `DownloadStateIndicatorCircular` 的“撤销任务”按钮（只保留只读进度）。
- 阅读器自动预下载：`ReaderService.downloadAhead` 及其队列、`ReaderControls` 的调用、`Reader.utils` 的 `getChapterIdsForDownloadAhead`。
- 阅读后自动删除：`ReaderService.useUpdateChapter` 不再计算/提交 `chapterIdsToDelete`，`Reader.utils.getChapterIdsToDeleteForChapterUpdate` 已删除。
- 手动标记已读后删除：`Chapters.markAsRead` 不再传 `chapterIdsToDelete`。
- `RequestManager.updateChapter` / `updateChapters` 不再接受 `chapterIdToDelete` / `chapterIdsToDelete`；`UPDATE_CHAPTER(S)` 文档里的 `deleteDownloadedChapter(s) @include(...)` 已移除，`DownloaderMutation.ts` 中旧的 `ENQUEUE_CHAPTER_DOWNLOAD(S)` 与 `DELETE_DOWNLOADED_CHAPTER(S)` 文档已删除，对应的 RequestManager 包装方法也已删除。
- 迁移流程的“删除已下载章节”选项（`MangaMigration`、`MigrationOptionsDialog`、`Migration.types.ts`、`RequestManager` 相关参数）。
- `DownloadSettings` 中已失效的个人选项（手动标记已读后删除、阅读后删除、允许删除已收藏章节、预下载章节数）及相关 metadata key、转换器和默认值。

保留的管理员路径：

- `DownloadQueue`（已限制为管理员）：重试改用 `enqueueServerLibraryDownloads`，取消改用 `removeChaptersFromDownloadQueue`（后端 admin-only），启动/停止/清空/重排不变。

### D. 管理员全局状态边界

- `AppInitializer`：
  - `fetchRequiredData` 不再请求 `downloadStatus`（旧实现会让普通账号的启动请求被拒绝并无限重试，导致永远停在启动画面）。
  - `fetchBackgroundData` 不再建立 `downloadSubscription` / `updaterSubscription`。
  - 新增 `startAdminData()`（幂等，建立两个订阅并填充 `downloadStatus` 缓存）与 `stopAdminData()`（退订并清除 `downloadStatus`/`libraryUpdateStatus`/`lastUpdateTimestamp`/`serverLibraryStorage` 的缓存）。
- 新增 `src/base/AdminSessionGate.tsx`：跟随当前账号，管理员则 `startAdminData()`，否则 `stopAdminData()`；挂载在需要登录的路由内部，因此登出时会随组件卸载退订并清缓存。
- `UpdateChecker`：非管理员返回 `null`，且两个查询都带 `skip`。
- `DebugInformation`：`useGetGlobalUpdateSummary` 与 `useGetDownloadStatus` 带 `skip`。
- 导航栏下载徽标：`useGetDownloadStatus({ skip: !isAdmin })`。

### E. GraphQL、翻译、格式化

- 新增文档已放在 `src/lib/graphql/server-library/`，会被 `gql_codegen.ts` 的 `documents` 覆盖。
- 手改了两个 generated 文件（与第一轮同样的做法）：
  - `graphql-base.types.ts`：`UserPermission.ManageServerLibrary`、`MangaType.inServerLibrary`、`MangaType.serverLibraryAddedAt`。
  - `graphql.ts`：所有内联了 `MANGA_BASE_FIELDS` 的片段类型补上两个新字段；`UpdateChapter(s)MutationVariables` 去掉 `chapterIdToDelete`/`deleteChapter`/`chapterIdsToDelete`/`deleteChapters`；对应的内联选择集去掉 `deleteDownloadedChapter(s)`。
- `pnpm i18n:extract` 已执行；zh-Hans / zh-Hant 中本次新增的 40 条文案已手工补译文（服务器资源库 / 我的书架 / 共享下载 等）。

## 4. 仍需完成

1. **与运行中的后端联调**（最重要）。本轮所有结论都来自源码阅读和静态检查。
2. **重新生成 GraphQL 类型**：后端可运行后执行 `tools/scripts/generateSchema.ts` 刷新 `src/lib/graphql/schema.json`，再跑 `pnpm gql:codegen`，确认手改的两处 generated 内容被正式生成覆盖。
3. **其余语言的翻译**：`My library`、`Server library` 等新 msgid 目前只有 en/zh-Hans/zh-Hant，其它语言会回退到英文，等 Weblate 同步。
4. **权限细节复核**：`MANAGE_SERVER_LIBRARY` 已加入 `USER_PERMISSION_TO_TRANSLATION`，但未加入任何默认权限集（符合契约）。
5. 移动端布局、错误重试、无权限直达 URL、账号切换等交互仍需人工验证。

## 5. 建议联调清单

- 两个普通用户看到同一共享作品与下载状态，各自收藏和阅读进度独立。
- 从来源添加只影响服务器资源库，不自动影响个人书架。
- 未加入服务器资源库的作品不能直接加入个人书架（应出现提示而不是报错）。
- 重复下载不重复排队；已下载章节被过滤，不能把入队成功当下载完成。
- 普通下载用户不能删除共享文件；被授权管理资源库的普通用户可以删除，但不能访问管理员全局队列或存储统计。
- 移除个人收藏不改变任何共享文件。
- 移除服务器作品的保留/删除下载两种操作均取消待处理任务，个人收藏与历史不丢失。
- 已移除作品的保留文件仍可通过个人详情页的“Shared downloads”进入清理。
- 普通用户启动及页面导航不发送全局队列/更新状态请求和订阅；管理员登录后能看到全局队列徽标。
- 旧个人阅读、标记已读、阅读器预加载不会触发共享下载/删除。
- mutation 后作品和章节状态刷新正确，其他用户操作可在轮询后体现。
- 管理员存储统计只手动刷新，不跟随 15 秒作品列表轮询。
- 简体中文、手机布局、错误重试、无权限直达 URL、账号切换均验证。

## 6. 环境备注

- Shell：PowerShell / Bash 均可用。本机没有运行中的后端（`localhost:4567` 无响应），因此无法 codegen、无法联调。
- 读取中文文件需要 UTF-8（PowerShell 下 `Get-Content -Encoding UTF8`）。
- 本轮没有调用子代理以外的东西，没有留下后台任务。
