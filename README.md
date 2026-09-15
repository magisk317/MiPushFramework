# MiPushFramework

<!-- badges:platform:start -->
[![CI](https://img.shields.io/github/actions/workflow/status/magisk317/MiPushFramework/ci.yml?branch=beta&style=flat-square&label=CI&logo=github-actions&logoColor=white)](https://github.com/magisk317/MiPushFramework/actions/workflows/ci.yml) [![Release](https://img.shields.io/github/actions/workflow/status/magisk317/MiPushFramework/release.yml?style=flat-square&label=Release&logo=github-actions&logoColor=white)](https://github.com/magisk317/MiPushFramework/actions/workflows/release.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/MiPushFramework?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/MiPushFramework/releases) [![License](https://img.shields.io/github/license/magisk317/MiPushFramework?style=flat-square)](LICENSE)
<!-- badges:platform:end -->

<!-- badges:tech:start -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Java](https://img.shields.io/badge/Java-26%2B-E76F00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.07.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.7.0-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.3.1-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-28-brightgreen?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-102-orange?style=flat-square)](https://github.com/libxposed/api) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)
<!-- badges:tech:end -->

在非 MIUI 系统上使用接近 MIUI 的小米系统级推送。

MiPushFramework 会以 `com.xiaomi.xmsf` 的形式提供系统推送服务，让接入小米推送的应用尽量把推送委托给统一的框架进程，而不是各自常驻后台运行 `XMPushService`。项目同时保留通知改写、事件观测、配置规则、焦点通知、HyperIsland 超级岛和 Android 实况通知等兼容能力。

当前开发分支最低支持 Android 9.0（API 28）。构建环境以 `gradle/libs.versions.toml` 为准，目前使用 Java 26+ / Gradle 9.x，并保持较激进的 Android Gradle Plugin、Kotlin 和 JDK 版本，用来尽早暴露兼容问题。release 或紧急修复如需稳定通道，应单独处理，不在常规架构收口中回退工具链。

## 与原仓库的区别

本 fork 仍以非 MIUI 设备上的系统级小米推送为核心目标，但从代码形态看，已经不是在原仓库上做少量补丁的维护分支，而是把早期单体推送代理改造成分层的 XMSF 兼容运行时：

- **XMSF 外部契约由产品代码托管**：根 `:xmsf` application 将 `xmsf/shell/` 打包为 `com.xiaomi.xmsf`，由 shell 保留外部应用会访问的包名、组件名、Provider、Service、Intent 和桥接入口；stock-facing 行为通过 shell 自有适配层承接，而不是把新业务直接堆进 Xiaomi runtime。
- **长连接、协议和业务层分离**：`vendor/` 保留仍在工作的 Xiaomi 长连接、网络和运行时栈，`pinned/` 固定 thrift/protobuf 等协议序列化表面，`core/` 放平台无关的运行时契约与统计模型；新增业务优先走 `xmsf` 适配器或 `core` facade。
- **运行时管线重建**：推送入口从 `MiPushFacadeService` 进入 `PushRuntime` 和 `PushRuntimeExecutionBridge`，再衔接 shell 的 `XMPushService` ABI facade、私有 vendor `XMPushServiceCore`、下游 `PushMessageProcessor` 与通知发布层；这里已经包含注册重放、待处理队列、连接状态、下游投递、通知计数和兼容降级等状态管理。
- **管理端和配置代码现代化**：`xmsf/` 只作为外壳，`manager/ui/` 承担 Compose 管理界面和设置入口，运行时依赖 Koin、Room、DataStore 等组件；应用注册、事件、通知记录、配置加载和运行时操作都有明确的 UI/adapter 边界。
- **通知发布不再只是简单转发**：`MIPushNotificationPublishHelper`、`NotificationController`、`NotificationManagerEx` 等代码对齐 stock XMSF 的分组、点击、按钮、VoIP、SweetTag、focus 删除和渠道兼容行为，并把普通通知、配置显式焦点通知、生成式超级岛代理区分为不同路径。
- **Hook 与超级岛是独立运行面**：`xposed/` 维护 libxposed/LSPosed 入口、SystemUI `MiPushIslandHook`、XMSF `UnlockFocusAuthHook` 和 provider 同步的 `IslandPreferences`；生成的 HyperIsland 代理通知不会和原始通知栏通知混成同一分支，也会避让独立 HyperIsland 模块。
- **面向新平台通知语义**：进度类推送会通过 `ProgressStyleBuilder` 在 Android 16+ 使用 `Notification.ProgressStyle` 和 promoted ongoing，低版本保留常规进度通知；非 MIUI/AOSP 路径会避免泄漏 `miui.focus.param`、`miui.focus.pics` 等 MIUI 私有 extras。
- **诊断和可观测性落在代码里**：`LogBundleExporter` 负责 JSONL 日志选择、旧文本日志清理、敏感字段脱敏、可选 LSPosed 日志采集和分享流程；root/shell 行为通过 `AppRootAccessFacade`、`BoundedShellRunner` 等受控入口执行。

## 项目定位

小米推送在 MIUI 上由系统服务集中处理，应用通常不需要为推送单独保活；在非 MIUI 系统上，接入小米推送的应用往往会启动自己的 `XMPushService` 长连接。MiPushFramework 的目标是把这部分能力集中到一个系统级服务里：

- 保留小米推送基础收发能力。
- 尽量减少多个应用各自维护后台长连接的成本。
- 给用户提供注册、推送、通知和日志的可观测入口。
- 通过配置规则控制通知标题、内容、动作和展示策略。
- 在兼容范围内补齐 stock XMSF 的通知、Provider、Service 和桥接行为。

这不是小米官方项目，也不保证所有 ROM 或所有应用都能获得相同行为。涉及厂商 ROM、目标应用策略、系统权限和 Xposed/LSPosed 环境时，实际效果需要以设备验证为准。

## 核心能力

- **集中推送服务**：以 `com.xiaomi.xmsf` 提供系统推送入口，承接应用注册、长连接、下游派发和基础 ACK/错误反馈。
- **应用注册与事件观测**：在管理界面查看已接入应用、注册状态、连接状态、推送事件和通知记录。
- **配置规则引擎**：通过配置文件改写消息标题、内容和行为，支持忽略、亮屏、正常通知和拉起应用等操作。
- **通知兼容**：对齐部分 stock XMSF 行为，支持通知分组、点击行为、按钮、VoIP 来电样式、SweetTag 富文本、空内容过滤等。
- **焦点通知与超级岛**：支持显式 `miui.focus.param`，并集成 HyperIsland ToolKit、SystemUI 代理投递、焦点认证开关和多模板样式路由。
- **实况通知**：对进度类消息提供 Android 16+ `Notification.ProgressStyle` / promoted ongoing 支持，低版本回退为常规进度通知。
- **日志导出**：运行日志使用 JSONL，按天轮转，并在导出时清理旧文本日志、脱敏 token 等敏感字段。

## 自动化维护边界

- CI 公共逻辑固定到 `magisk-ci-toolkit` 的不可变提交；GitLab `include.ref`、作业变量和
  本地/GitHub resolver 必须保持同一 SHA。resolver 使用 exact fetch，因此仍可由维护者
  显式覆盖为 branch/tag，但仓库默认值不得回退到浮动 `main`。
- Telegram CI 通知：GitHub 侧由 `.github/workflows/notification.yml` 处理，GitLab 侧由 `.gitlab-ci.yml` 的 `telegram:ci` 处理；GitLab 通知在 debug APK 构建完成后发送，不等待 quality 阶段。
- Renovate：GitHub 侧继续使用 `.github/renovate-config.js`；GitLab 侧使用 `.gitlab/renovate-config.js` 和隐藏变量 `RENOVATE_TOKEN`。`renovate.json` 会忽略 GitHub/GitLab workflow 配置，避免两个平台互相改 CI 入口。
- Release 发布：GitHub 与 GitLab 使用相同的版本/tag 门禁、正式签名要求、XMSF/MiPush/Zygisk 构建和主 Release 资产集合，均发布 APK、Zygisk zip、mapping 与 native symbols，并从 `docs/CHANGELOG.md` 提取同一份说明。缺少平台能力或所需密钥时对应步骤会明确跳过且不阻断后续流程；真正开始后的构建、签名或上传错误仍会失败。Xposed-Modules-Repo 同步仅保留在 GitHub workflow。
- GitLab 发版使用固定的 MiPushZygisk commit；同一 tag 重试会重新校验该源，并复用同一流水线已成功生成的上游 artifacts，避免 `main` 漂移改变旧版本。
- Dependabot 与 GitHub dependency workflows 暂不迁移，现有 GitHub Dependency Graph / alerts 相关流程保持不变。

## 安装与使用

1. 从 [GitHub Releases](https://github.com/magisk317/MiPushFramework/releases)、[GitLab Releases](https://gitlab.com/magisk3171/MiPushFramework/-/releases) 或 [CI](https://github.com/magisk317/MiPushFramework/actions/workflows/ci.yml) 下载 APK。
2. 安装后跟随向导完成基础设置。
3. 建议将框架安装为系统应用。部分应用没有完整应用列表权限，只有在系统应用列表中看到 `com.xiaomi.xmsf` 时才会注册系统推送。
4. 可选开启“推送服务保活”等高级选项。
5. 如果目标应用仍不向框架注册，通常需要配合伪装增强模块，让应用识别当前设备具备 MIUI/XMSF 推送环境。

请尽量不要用黑域、绿色守护或其他限制工具压制 `com.xiaomi.xmsf`。这类限制会直接影响长连接、注册重放、通知发布和日志采集。

## normal 与 vc105

Release 页面通常提供 `normal` 和 `vc105` 两类构建，核心区别是目标应用向 XMSF 传递消息的方式。

| 版本 | versionCode | 传递方式 | 建议 |
|------|-------------|----------|------|
| `normal` | `1003003000` | `bindService` | 默认首选，稳定性更好 |
| `vc105` | `105` | `startService` | 兼容旧路径或部分 bind 异常 ROM |

小米推送 SDK 会根据 XMSF 的 `versionCode` 选择传递方式：

- `versionCode >= 106`：使用 `bindService`。
- `versionCode == 105`：使用 `startService`。

优先尝试 `normal`。如果某些 ROM 或应用无法通过 bind 路径传递消息，再切换到 `vc105`。在 MIUI 上使用 `normal` 还有一个额外好处：重启后更不容易被系统还原成官方 XMSF。

## 常见问题

### 是否支持分身或 999 用户应用？

Xposed 模块不再固定三方应用作用域。需要伪装增强的主应用、分身应用或 999 用户应用，可以在 LSPosed 中手动勾选对应作用域。

当前运行时配置、注册状态、通知和事件数据均按 Android user scope 隔离，999 用户应用在源码和协议层有独立路径。完整的分身注册、收发、通知和清理链路仍需要真实设备验证，不能仅凭构建或 JVM 测试宣称完成。

### 配置文件有什么用？

配置文件可以控制特定应用或特定内容的通知行为，例如改标题、改内容、忽略、亮屏、打开应用或正常通知。大多数官方配置可以直接使用；是否启用某个配置，请参考 [MiPushConfigurations](https://github.com/magisk317/MiPushConfigurations) 中的说明、配置名和 `description` 字段。

### 必须安装为系统应用吗？

不绝对必须，但推荐。某些应用只能看到系统应用列表，若框架不是系统应用，它们可能无法发现 `com.xiaomi.xmsf`，从而不会注册系统推送。

### `com.xiaomi.push.service.XMPushService` 被禁用正常吗？

正常。目标应用在发现系统推送服务后，自己的 `XMPushService` 通常就不应该继续承担长连接工作，相关能力会委托给系统推送服务。

### Root 或 Xposed 是必须的吗？

基础推送服务不强制要求 Root 或 Xposed。为了让更多应用自动识别系统推送环境、启用伪装增强、补齐焦点通知或 SystemUI 超级岛链路，实际使用中可能需要系统应用安装、Root、Xposed/LSPosed 或对应模块支持。

### 哪些通知会变成焦点通知、超级岛或实况通知？

- 服务端或应用显式携带 `miui.focus.param` / `miui.focus.rv` 的推送会优先保留自身的 MIUI/HyperOS 焦点通知语义。
- 未显式配置但符合条件的 MiPush 通知，可在 MIUI/HyperOS 上通过 SystemUI 代理生成超级岛展示。
- 非 MIUI/AOSP 上不会保留 MIUI 私有 focus extras；进度类语义会尽量翻译成 Android 实况通知或普通进度通知。

如果要验证真实展示，不要只看应用日志中的“已发布”记录，还要结合 `dumpsys notification --noredact`、logcat 和真实推送 payload。

管理端主界面的顶栏/底栏支持随滚动折叠。若后续调整该交互，注意切页时要把共享的
`ScrollChromeState` 复位到顶部，而不是只切换显隐；否则半收起状态可能串到设置页、
事件页等短页面，表现为顶部被削掉，严重时还会因为负 padding 导致 Compose 直接崩溃。

## 反馈问题

遇到问题请先搜索 Issues。提交新 Issue 时，请尽量带上：

- ROM 名称和 Android 版本。
- 使用的构建类型：`normal` 或 `vc105`。
- 是否安装为系统应用。
- 是否使用 Root、Xposed/LSPosed、伪装增强或其他限制类工具。
- 问题涉及的目标应用和大致复现步骤。
- 设置页“获取日志”导出的日志包。

日志会自动按天轮转，默认保留 2 天，最低可设置为 1 天。导出包会清理旧文本日志，并对 token 等敏感字段脱敏。分享前仍建议在预览弹窗中确认内容。

## 开发入口

项目当前使用多模块 Gradle 结构。模块边界、运行时链路和重构记录见：

- [隐私政策](docs/PRIVACY.md)
- [模块边界](docs/architecture/boundary-model.md)
- [运行时调用链](docs/architecture/current-runtime-call-flow.md)
- [Xposed 通知边界](docs/architecture/xposed-notification-boundary.md)

常用验证命令直接通过 Gradle wrapper 运行：

```bash
./gradlew :core:jvmTest
./gradlew :xmsf:shell:testNormalDebugUnitTest
./gradlew :xposed:compileDebugKotlin
./gradlew :xmsf:assembleNormalDebug
./gradlew :mipush:assembleDebug
./gradlew verifyModuleBoundaries
```

打正式包时优先使用：

```bash
scripts/build_release.sh
```

## 已知限制

- 小众 ROM 的特殊适配只会在不破坏推送主链路的前提下处理。
- 部分应用或 ROM 可能不会自动停用自身 `XMPushService`，可尝试系统应用安装和伪装增强。
- 某些通知特性依赖 ROM、系统权限、SystemUI 行为或目标应用 payload，无法保证跨设备一致。
- 部分通知可能仍显示为推送框架发出，而不是完全表现为目标应用自身发出。

## 致谢

- @Rachel030219 提供文件。
- Android Open Source Project, AndroidX, Jetpack Compose, Material 3, Kotlin, kotlinx.coroutines, kotlinx.serialization, Koin, Room, DataStore, Navigation, Lifecycle, AppCompat, libxposed API, LSPosed HiddenApiBypass, libsu, Napier, Miuix, JetBrains Markdown, JUnit, MockK, Robolectric, Detekt, Kover, KSP。
- [HyperIsland](https://github.com/1812z/HyperIsland) 提供超级岛实现参考。
- [HyperIsland-ToolKit](https://github.com/D4vidDf/HyperIsland-ToolKit) 提供 HyperIsland SDK 支持。
- 酷安 @PzHown @lmnm011223 @苏沐晨风丶（未采纳）提供图标。
