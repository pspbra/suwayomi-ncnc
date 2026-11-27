# ncnc 漫画 · Suwayomi 技术栈

基于 [Mihon](https://github.com/mihonapp/mihon) 与 [Suwayomi](https://github.com/Suwayomi) 定制的一套自用漫画阅读方案：安卓客户端、服务端与 WebUI 、一起发布。主要修改在于增加了服务区资源库的概念，让漫画下载到服务器资源库，用户可以从服务器资源库中收藏书籍，不同的有用户有不同的书架和单独的下载。

```text
.
├── ncnc-manga-android/   Android 客户端（Mihon fork，内置 Suwayomi 源，不依赖 WebUI）
├── suwayomi-server/      Suwayomi 服务端（fork，新增服务器资源库 serverLibrary 接口）
└── suwayomi-webui/       Suwayomi WebUI（fork，新增服务器资源库页面）
```

## 子项目

| 目录 | 说明 | 上游 | 许可 |
| --- | --- | --- | --- |
| [`ncnc-manga-android/`](ncnc-manga-android/README.md) | 应用名 ncnc漫画，包名 `app.ncncmanhua`。底部导航为书架 / 浏览 / 历史 / 添加 / 更多，通过 Suwayomi GraphQL API 访问服务器书库、图源、扩展与阅读历史。 | [Mihon](https://github.com/mihonapp/mihon) | Apache-2.0 |
| [`suwayomi-server/`](suwayomi-server/README.md) | 在 Suwayomi-Server 上增加全站共享的「服务器资源库」、多用户 JWT 登录与共享下载，供客户端和 WebUI 使用。 | [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) | MPL-2.0 |
| [`suwayomi-webui/`](suwayomi-webui/README.md) | 增加服务器资源库页面、管理员会话门禁和相关权限控制。 | [Suwayomi-WebUI](https://github.com/Suwayomi/Suwayomi-WebUI) | MPL-2.0 |

三者通过服务端 GraphQL 契约耦合，契约说明见
[`suwayomi-server/docs/Server-Library-WebUI-APK-Migration.md`](suwayomi-server/docs/Server-Library-WebUI-APK-Migration.md)。

## 构建

各子项目独立构建，命令见各自 README：

```bash
# 安卓客户端（Windows 用 gradlew.bat）
cd ncnc-manga-android
gradlew :app:assembleDebug

# 服务端（含内置 WebUI 的完整 jar 见 suwayomi-server/CONTRIBUTING.md）
cd suwayomi-server
gradlew :server:shadowJar

# WebUI
cd suwayomi-webui
pnpm install
pnpm build
```

服务端默认会从资源里读取 `WebUI.zip` 作为内置 WebUI；WebUI 的构建产物需要按
`suwayomi-webui/BUILDING.md` 的说明打包后放入服务端后再发布。

## 开发文档

- [`ncnc-manga-android/README.md`](ncnc-manga-android/README.md)：客户端功能、代码结构、GraphQL 调用、开发与排障
- [`ncnc-manga-android/docs/`](ncnc-manga-android/docs)：服务器资源库适配记录、多用户接入说明
- [`suwayomi-server/docs/`](suwayomi-server/docs)：服务端配置、多用户认证、资源库契约
- [`suwayomi-webui/SERVER-LIBRARY-WEBUI-HANDOFF.md`](suwayomi-webui/SERVER-LIBRARY-WEBUI-HANDOFF.md)：WebUI 资源库改造交接记录

## 许可证

本仓库是多个上游项目的组合 fork，按目录遵循各自上游的许可证：

- `ncnc-manga-android/`：Apache License 2.0，见 [`ncnc-manga-android/LICENSE`](ncnc-manga-android/LICENSE)
- `suwayomi-server/`、`suwayomi-webui/`：Mozilla Public License 2.0，见各目录下的 `LICENSE`

上游版权声明与第三方依赖许可以各子项目内文件为准。
