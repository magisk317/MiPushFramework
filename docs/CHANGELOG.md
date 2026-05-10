# 更新日志 (CHANGELOG)

本日志记录了项目近期的主要变更。

---

## [v0.4.1] - Unreleased
- `[feat(notification)]` 对齐 stock 7.4.67 通知样式：新增 focus 删除过滤、VoIP 来电样式、SweetTag `<ft>` 富文本渲染、通知按钮与全屏来电入口。
- `[fix(notification)]` 修复 focus 图片按 key 取图、focus 删除状态持久化、VoIP style type 6 `cust_btn_*` 按钮、`voip_type=0` 结束事件和 sequence 旧消息过滤。
- `[docs/architecture]` 更新模块边界和运行时调用链文档，当前模块以 `xmsf/core/legacy/pinned/protocol` 为准，device dump 参考路径为 `device_dumps`。

---

## [v0.4.0] - 2026-05-10
- 版本：`versionCode 105` / `versionName 0.4.0`。
- 发布说明：本次为大规模重构版本，完成 monorepo 整合、命名空间迁移与 legacy-runtime Kotlin 化，同时修复多项推送与通知链路问题。
- `[refactor/monorepo]` 将 MiPush 各模块合并至 MiPushFramework 单仓，重构 protocol、legacy-runtime、push-service、push-core、common 等模块边界，删除冗余 typealias 与重复源文件。
- `[refactor/namespace]` 命名空间从 `com.magisk317` 迁移至 `io.github.magisk317.mipush`，同步更新 app 层、runtime store 与 settings。
- `[refactor/kotlin]` legacy-runtime 全面 Kotlin 化，消除编译警告，统一使用 Napier 结构化日志替代 `printStackTrace`。
- `[feat/keepalive]` 从 xinyi-relay 迁移保活逻辑，桥接设置到系统 hooks，修复迁移后的设置接线。
- `[feat(xmsf)]` 新增 vc105 product flavor，加固 manifest 安全与权限合规。
- `[feat(push)]` 新增 per-app 屏蔽列表与注册-通知控制；增强通知样式与消息处理器；改进事件列表过滤与降级诊断。
- `[fix(push)]` 重放通知窗口放宽至 6 小时，避免会话重连后近期消息被误丢弃。
- `[fix(xposed)]` 处理 Android 17 通知 API 的 SecurityException；抑制 legacy RemoteViews 字段的弃用警告。
- `[fix(xmsf)]` 通知 channel 查询失败时优雅降级；按目标包名过滤通知 channel；修复通知小图标指向目标应用。
- `[fix(push)]` channel 关闭后自动重连；重置 slim 连接 shutdown 状态；处理空推送 payload；添加详细 slim 连接诊断。
- `[fix(push)]` 修复 force register 递归问题；前台服务类型切换为 `remoteMessaging` 修复超时崩溃；恢复 Android 12+ 自动通知。
- `[fix(push)]` 加强通知派发去重；解密失败时抛出 DecryptException 并回退 regSec 候选；消除主线程 `runBlocking`。
- `[fix(registration)]` 分离观察到的活动状态与确认注册状态。
- `[fix(xmsf)]` 移除 `manageSpaceActivity` 恢复默认清除数据行为；缓存 manifest 服务检查；屏蔽应用配置刷新崩溃。
- `[fix(notification)]` sdk37+ 优先使用 identity-first posting；稳定小米模块增强投递。
- `[security(push)]` 替换 `usesCleartextTraffic` 为 network security config。
- `[build/ci]` 迁移至自动化依赖安全覆盖机制；CI debug 构建启用 minify；修复 artifact 上传与 Telegram 通知；更新 Gradle 至 9.5.0-rc-3、AGP 至 9.3.0-alpha04。
- `[perf(config)]` 本地快照优先加载，远端刷新后置。

> Full Changelog: https://github.io/github/magisk317/MiPushFramework/compare/v0.3.17...v0.4.0

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
