# 更新日志 (CHANGELOG)

本日志记录了项目近期的主要变更。

---

## [v0.3.17] - 2026-04-03
- 版本：`versionCode 7` / `versionName 0.3.17`。
- 发布说明：GitHub 渠道继续提供按 ABI 拆分的 `MiPushFramework` release APK，并附带 `.idsig` 签名文件；发布前校验、tag 创建、draft release 与 Telegram 通知链路继续收敛到同一套自动化流程。
- `[config]` 新增独立“配置”工作区，可直接浏览远端配置、搜索文件或包名，并从主界面进入配置列表与编辑页。
- `[config]` 支持选择本地目录、批量导入 JSON、手动拉取远端配置、显示同步进度，以及在本地/远端双视图之间预览、校验、保存和重置配置。
- `[config]` 远端源现在支持自定义 `repository@branch`，便于切换到自有配置仓库、测试分支或临时镜像。
- `[register]` 重构强制注册分发策略，补充服务不可直达时的广播兜底，并细化 `direct_sdk`、`receiver_only`、`bridge_wrapper` 等诊断结果。
- `[diagnostics]` 本地注册态探测继续加强，同时覆盖 `shared_prefs` 与 `keva` 路径，并补充 root 探测缓存、批量探测预算、抖音场景专项诊断和 bridged module logs 导出能力。
- `[notification/debug]` 过滤无有效文本的空内容框架通知；模拟通知链路优先走 modern helper，旧路径异常时自动回退。
- `[config/runtime]` 修复配置替换中的包名占位符处理问题，改进 `${name}` 与 `$$` 转义替换，减少配置命中后的错替换。
- `[build/ci]` GitHub Release、tag 校验、Telegram 通知、Android SDK 平台别名和共享子模块检查继续收敛，发版链路更稳定一致。

> Full Changelog: https://github.io/github/magisk317/MiPushFramework/compare/v0.3.16...v0.3.17

---

## [v0.3.16] - 2026-04-01
- 版本：`versionCode 7` / `versionName 0.3.16`。
- 发布说明：GitHub 渠道继续提供按 ABI 拆分的 `MiPushFramework` release APK，并保持系统级推送、配置同步与运行时兼容链路的稳定发布节奏。
- `[runtime]` 继续维护系统级推送、配置同步与运行时兼容链路，优先保证 GitHub 渠道版本的稳定性与可回溯性。
- `[build/ci]` 构建链路对齐 Android 37 / JDK 25，并继续收敛发布前校验与 GitHub Release 体验。

> Full Changelog: https://github.io/github/magisk317/MiPushFramework/compare/v0.3.15...v0.3.16
