# ncnc漫画 ui_login 多用户接入与阅读同步

更新日期：2026-10-09；对应 App `0.20.9` / versionCode `37`。

本说明取代之前仅讨论 Basic Auth 和待实现功能的接入指导。App 现在只对接 `ui_login`，不再兼容旧认证模式。

## 登录

首次安装在引导中配置；已有安装进入“更多 → 设置 → 服务器”。输入服务器根地址、Suwayomi 用户名与密码，点击测试并保存连接。不要填 `/api/graphql` 或把 Token 当作密码。

1. 未带旧令牌调用 `login(input: { username, password })`，获得访问令牌与刷新令牌。
2. 携带访问令牌查询 `user { id username }`。
3. 两步成功后保存会话；失败保留原账号。密码不保存。
4. GraphQL、封面、图标、阅读图片和下载共享 JWT 拦截器。只给匹配服务器协议、域名、端口与路径的请求附加令牌。
5. 临近令牌过期时串行刷新；并发请求复用刷新结果。401 或 GraphQL Unauthorized 最多重试一次，权限不足不会反复刷新。刷新失败提示重新登录。
6. 设置页提供退出账号。退出不删除任何账号的本地阅读数据及待同步队列。

## 阅读记录

- 实际翻页及退出阅读器时，先持久化待同步记录，再发送 `updateChapter(input: { id, patch: { lastPageRead, isRead } })`。
- 服务端从 Bearer Token 确定用户，自动更新此用户的 `lastReadAt`，无需客户端传入 userId。
- 阅读器继续使用从零开始的页码。隐私/匿名阅读不写入队列。
- 打开漫画、刷新章节和打开阅读器时拉取当前用户进度；主页在前台启动、恢复和每分钟拉取历史，每页 100 条，直到完成。
- 云端历史可在新安装上恢复：自动建立本地漫画及章节记录，但不自动加入本地收藏。
- 服务端书库与本地收藏仍是两项操作；本次没有把本地收藏自动上传为服务器收藏。

### 离线与冲突

待同步队列存放在 `suwayomi_reading_outbox`，按服务器与用户 ID 隔离。同章连续翻页合并为最后一次记录，应用进程退出后保留。再次启动、恢复主页、继续阅读或在设置中点击立即同步时重试。

发送前查询云端进度，按阅读事件时间避免旧离线记录覆盖其他设备的新进度；记录上次自身上传的服务端响应，避免服务器接收时间误伤上传期间新产生的本机翻页。设备时钟应正确。服务端没有条件写入/CAS 接口，同秒或查询与写入之间的并发变更不能实现严格全局排序。

服务器不允许客户端指定 `lastReadAt`，所以离线补传后的云端历史时间是接收时间，阅读时长不会上传。

### 账号隔离与升级

本地漫画 URL 为 `account/<SHA-256(规范化服务器地址 + 用户ID)>/<mangaId>`，章节 URL 在后面追加章节 ID。请求和离线队列保留发起者身份；切换账号后不把旧用户的进度上传到新用户。书架和历史只显示当前账号的 Suwayomi 条目；普通本地漫画仍按原方式显示。

**旧版没有账号归属的 Suwayomi 条目不会自动认领。** 数据保留在本机但不混入当前账号书架/历史。升级后需重新登录，从“浏览”重新收藏所需漫画；云端已有历史自动恢复。旧版仅存在手机上的记录不会自动上传，避免归属错误。下载文件不自动迁移到新的账号条目。

历史删除在本机按账号隐藏，服务器当前 GraphQL 不提供清除历史时间接口；因此其他设备的历史不会随本机删除而消失，再次阅读会重新显示。

## 代码入口

- `SuwayomiSession.kt`：登录、刷新与账号标识。
- `SuwayomiServerPreferences.kt`：原子保存会话，密码不持久化。
- `SuwayomiAuthInterceptor.kt`：统一鉴权、并发刷新和受控重试。
- `SuwayomiServerApi.kt`：历史/章节查询与进度写入。
- `SuwayomiReadingSync.kt`：持久化待同步队列、冲突处理、云端历史落库。
- `ReaderViewModel.kt`：翻页上传、恢复进度、匿名阅读处理。
- `HomeScreen.kt`：前台同步与重试。
- `HistoryViewModel.kt` / `LibraryViewModel.kt`：账号过滤。
- `SettingsServerScreen.kt`：登录、退出、同步状态及手动同步。

令牌保存在应用私有 SharedPreferences，尚未使用 Android Keystore 加密。网络详细日志已隐藏 Authorization、Cookie、Set-Cookie。

## 验证

已对实际部署执行只读接口检查：登录、刷新、用户身份、历史分页、章节进度和封面鉴权。进度 mutation 使用 `@skip(if: true)` 仅验证 schema，没有写入真实阅读数据。脚本为 `tools/verify_suwayomi_contract.py`，凭据通过环境变量 `SUWAYOMI_URL`、`SUWAYOMI_USERNAME`、`SUWAYOMI_PASSWORD` 或交互输入传递，不写入项目。

遵循 README 要求未编译 App。全模块格式检查存在原有 Compose 命名等问题，本次修改的 21 个 Kotlin 文件已通过定向格式检查。本次只有一个可用测试账号，没有声称完成两个真实账号的隔离测试。

编译安装后的必要验收：

1. 账号 A 阅读到中间页，退出，再在 WebUI/另一设备确认历史、页码和已读状态。
2. 新安装登录 A，等待同步，确认历史出现且可恢复到对应页。
3. 同一安装切换 B，确认 A 的书架/历史隐藏；B 阅读同章，A 的进度不变；切回 A 恢复自己的数据。
4. 断网阅读后杀进程，恢复网络重开 App，确认补传；切到 B 时不能上传 A 的队列。
5. 访问令牌过期后同时加载多张图，确认单次刷新，密码或刷新会话失效时提示重新登录。
6. 匿名阅读不上传，删除历史仅影响当前账号在本机的显示。
7. 同账号两设备交替阅读，确认较新进度优先，并单独检查弱网下的冲突边界。

服务端依据：相邻项目的 `docs/Multi-User-Authentication.md`、`graphql/mutations/UserMutation.kt`、`graphql/mutations/ChapterMutation.kt` 和 `graphql/queries/ChapterQuery.kt`。账号由服务端鉴权指令注入，章节个人状态保存在 `ChapterUserTable`。
