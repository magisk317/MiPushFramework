# MiPushFramework

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=io.github.magisk317.mipush">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/MiPushFramework/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
    <a href="https://gitlab.com/magisk3171/MiPushFramework/-/releases">
        <img src="https://img.shields.io/badge/Get%20it%20on-GitLab-FC6D26?style=for-the-badge&logo=gitlab&logoColor=white" alt="Get it on GitLab" height="40"/>
    </a>
</div>

<div align="center">

<!-- badges:platform:start -->
[![GitLab](https://img.shields.io/badge/GitLab-magisk3171/MiPushFramework-FC6D26?style=flat-square&logo=gitlab&logoColor=white)](https://gitlab.com/magisk3171/MiPushFramework) [![CI](https://img.shields.io/gitlab/pipeline-status/magisk3171%2FMiPushFramework?branch=beta&style=flat-square&logo=gitlab&label=CI)](https://gitlab.com/magisk3171/MiPushFramework/-/pipelines?ref=beta) [![Latest Release](https://img.shields.io/gitlab/v/release/magisk3171%2FMiPushFramework?include_prereleases&style=flat-square&logo=gitlab)](https://gitlab.com/magisk3171/MiPushFramework/-/releases) [![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)
<!-- badges:platform:end -->

<!-- badges:tech:start -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.5.0--Beta1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Java](https://img.shields.io/badge/Java-27%2B-E76F00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.10.00-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.9.0--milestone--2-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.5.0--alpha10-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-28-brightgreen?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-102-orange?style=flat-square)](https://github.com/libxposed/api) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)
<!-- badges:tech:end -->

</div>

在非 MIUI 系统上使用接近 MIUI 的小米系统级推送。

MiPushFramework 以 `com.xiaomi.xmsf` 的形式提供系统推送服务：接入小米推送的应用把推送委托给统一的框架进程，而不是各自常驻后台运行 `XMPushService`。项目同时保留通知改写、事件观测、配置规则、焦点通知、HyperIsland 超级岛和 Android 实况通知等兼容能力，当前开发分支最低支持 Android 9.0（API 28）。

这不是小米官方项目，也不保证所有 ROM 或所有应用都能获得相同行为；涉及厂商 ROM、目标应用策略、系统权限和 Xposed/LSPosed 环境时，实际效果以设备验证为准。

## 重构内容

本 fork 仍以非 MIUI 设备上的系统级小米推送为核心目标，但已把早期单体推送代理改造成分层的 XMSF 兼容运行时：

- **XMSF 外部契约由产品代码托管**：`:xmsf` 应用模块将 `xmsf/shell/` 打包为 `com.xiaomi.xmsf`，由 shell 保留外部应用会访问的包名、组件名、Provider、Service、Intent 与桥接入口；面向 stock 的行为通过 shell 自有适配层承接，而不是把新业务直接堆进 Xiaomi runtime。
- **长连接、协议与业务分层**：`vendor/` 保留仍在工作的 Xiaomi 长连接、网络与运行时栈，`pinned/` 固定 thrift/protobuf 等协议序列化表面，`core/` 放平台无关的运行时契约与统计模型；新增业务优先走 `xmsf` 适配器或 `core` facade。
- **运行时管线重建**：推送入口从 `MiPushFacadeService` 进入 `PushRuntime` 与 `PushRuntimeExecutionBridge`，再衔接 shell 的 `XMPushService` ABI facade、私有 vendor `XMPushServiceCore`、下游 `PushMessageProcessor` 与通知发布层；覆盖注册重放、待处理队列、连接状态、下游投递、通知计数与兼容降级等状态管理。
- **xmsf 运行时独立交付**：运行时 APK 独立打包交付（`:xmsf:*` 模块族），设置项展示运行时状态与版本探测。
- **管理端现代化**：`manager/` 模块族（contract / port / application / client / ui / xmsfDelivery）承担 Compose 管理界面与设置入口，运行时依赖 Koin、Room、DataStore；应用注册、事件、通知记录、配置加载与运行时操作都有明确的 UI/adapter 边界。
- **通知发布对齐 stock XMSF**：`MIPushNotificationPublishHelper`、`NotificationController`、`NotificationManagerEx` 对齐分组、点击、按钮、VoIP、SweetTag、focus 删除与渠道兼容行为，普通通知、配置显式焦点通知与生成式超级岛代理区分不同路径。
- **Hook 与超级岛独立运行面**：`xposed/` 维护 libxposed/LSPosed 入口、SystemUI `MiPushIslandHook`、XMSF `UnlockFocusAuthHook` 与 provider 同步的 `IslandPreferences`；HyperIsland 代理通知不与原始通知栏通知混入同一分支，并避让独立 HyperIsland 模块。
- **面向新平台的通知语义**：进度类推送通过 `ProgressStyleBuilder` 在 Android 16+ 使用 `Notification.ProgressStyle` 与 promoted ongoing，低版本保留常规进度通知；非 MIUI/AOSP 路径避免泄漏 `miui.focus.param`、`miui.focus.pics` 等 MIUI 私有 extras。
- **诊断与可观测落在代码里**：`LogBundleExporter` 负责 JSONL 日志选择与旧文本日志清理；归档、敏感字段脱敏与采集范围由 `magisk-xposed-kit` 的日志策略按构建类型判定，发行构建导出脱敏后的有界内容，debug/CI 构建才采集 LSPosed 日志、全量 logcat 与 root 诊断；设置项只能提高脱敏、不能取消它；root/shell 行为通过 `AppRootAccessFacade`、`BoundedShellRunner` 等受控入口执行。**导出需要 root 权限**：归档包含 `/data/adb/lspd/log` 等模块日志目录，非 root 设备在选择保存位置前即会被拦截；正式版不采集这类 root 诊断，需要完整导出请安装 debug/CI 构建。

## normal 与 vc105

Release 页面通常提供 `normal` 和 `vc105` 两类构建，核心区别是目标应用向 XMSF 传递消息的方式。

| 版本 | versionCode | 传递方式 | 建议 |
|------|-------------|----------|------|
| `normal` | `1003003000` | `bindService` | 默认首选，稳定性更好 |
| `vc105` | `105` | `startService` | 兼容旧路径或部分 bind 异常 ROM |

小米推送 SDK 会根据 XMSF 的 `versionCode` 选择传递方式：`versionCode >= 106` 使用 `bindService`，`versionCode == 105` 使用 `startService`。

优先尝试 `normal`；如果某些 ROM 或应用无法通过 bind 路径传递消息，再切换到 `vc105`。在 MIUI 上使用 `normal` 还有一个额外好处：重启后更不容易被系统还原成官方 XMSF。

## 文档
- [隐私政策](docs/PRIVACY.md)
- [赞助与捐赠 (Donations)](docs/DONATIONS.md)

## 致谢

- @Rachel030219 提供文件。
- [@KiritoXDone](https://github.com/KiritoXDone) 提供新版分层自适应启动图标（layered adaptive launcher icon）。
- Android Open Source Project, AndroidX, Jetpack Compose, Material 3, Kotlin, kotlinx.coroutines, kotlinx.serialization, Koin, Room, DataStore, Navigation, Lifecycle, AppCompat, libxposed API, LSPosed HiddenApiBypass, libsu, Napier, Miuix, JetBrains Markdown, JUnit, MockK, Robolectric, Detekt, Kover, KSP。
- [HyperIsland](https://github.com/1812z/HyperIsland) 提供超级岛实现参考。
- [HyperIsland-ToolKit](https://github.com/D4vidDf/HyperIsland-ToolKit) 提供 HyperIsland SDK 支持。
- 酷安 @PzHown @lmnm011223 @苏沐晨风丶（未采纳）提供图标。

# 赞助与捐赠
如果本项目对你有帮助，欢迎支持开发者。你的支持会直接用于项目维护与持续迭代。

赞助名单与说明请见：[赞助与捐赠文档](docs/DONATIONS.md)。

| 支付宝收款码 | 微信赞赏码 | 微信收款码 |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |
