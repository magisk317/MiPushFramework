# MiPushFramework

[![CI](https://github.com/magisk317/MiPushFramework/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/magisk317/MiPushFramework/actions/workflows/ci.yml)
[![Release](https://github.com/magisk317/MiPushFramework/actions/workflows/release.yml/badge.svg)](https://github.com/magisk317/MiPushFramework/actions/workflows/release.yml)
[![Dependency Force Manager](https://github.com/magisk317/MiPushFramework/actions/workflows/dependency-force-manager.yml/badge.svg)](https://github.com/magisk317/MiPushFramework/actions/workflows/dependency-force-manager.yml)
[![Dependency Submission](https://github.com/magisk317/MiPushFramework/actions/workflows/dependency-submission.yml/badge.svg?branch=dev)](https://github.com/magisk317/MiPushFramework/actions/workflows/dependency-submission.yml)
[![Latest Release](https://img.shields.io/github/v/release/magisk317/MiPushFramework)](https://github.com/magisk317/MiPushFramework/releases)
[![License GPL-3.0](https://img.shields.io/badge/license-GPLv3.0-blue.svg)](LICENSE)
![Android 9.0+](https://img.shields.io/badge/Android-9.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-25%2B-E76F00?logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.x-02303A?logo=gradle&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)

在非 MIUI 系统上体验小米系统级推送。

当前开发分支的最低支持版本已经提升到 Android 9.0（API 28），构建环境也已经对齐到 Java 25+（JDK 25）/ Gradle 9.x。如果你是从旧文档或历史 release 了解这个项目，请以 `gradle/libs.versions.toml` 中的构建参数为准。

当前 `dev` 使用较激进的 Android Gradle Plugin、Kotlin 和 JDK 版本以便尽早暴露兼容问题；release 或紧急修复如果需要稳定通道，应单独开任务降风险，而不是在常规架构收口里回退工具链。

## 什么是小米系统级推送，为什么会有这个项目

小米推送是小米公司提供的推送服务，许多 App 都在使用（如酷安）。

它非常轻量，会在 MIUI 设备上自动启用系统推送，而非 MIUI 设备则在后台保持长连接。



### 系统级推送

类似 GCM，小米推送的系统级推送是在 MIUI 完成的。

接入小米推送的应用在启动时，会根据系统是否为 MIUI ROM 来决定如何向用户推送消息。
- 对于 MIUI ROM，推送工作都由系统完成，应用**无需后台**，更省电。
- 对于非 MIUI ROM，每个应用都会**在后台启动**一个 `XMPushService` 服务，该服务会创建一个用于接收推送消息的连接。相比于 MIUI ROM 由系统实现的推送能力，多个应用启动的多个服务与连接，会更加的耗电、内存和流量。



### 本项目的意义

本项目起源于这样一个想法：让任何非 MIUI ROM 的用户都能用上类似 MIUI ROM 的系统级推送能力，在应用没有常驻后台的情况下也能向用户推送消息。


## 功能

- 基本的推送能力。基本与 MIUI ROM 中的推送服务（`com.xiaomi.xmsf`）一致，不过默认禁止拉起应用
- 对通知的改写。可以通过配置文件，修改消息的标题、内容及样式等，控制收到消息时忽略、亮屏或自动弹出等
- 通知样式兼容。对齐部分 stock XMSF 行为，支持 focus 通知、HyperIsland ToolKit 超级岛参数、SystemUI 超级岛代理投递与设置页开关、VoIP 来电样式和 SweetTag 富文本
- 观测。可以在本项目的应用界面中，查看都有什么应用接入了小米推送服务及其推送的消息内容

### 注意

* 给予 `com.xiaomi.xmsf` 最大的权限，请勿使用 黑域、绿色守护、Xposed 等模块对其做限制，这可能会导致推送不稳定
* 在 MIUI ROM 上，部分依靠推送的功能不可用，如 网络短信，目前明确可用的有 查找手机
* 服务本身不需要 Root、Xposed 支持，但是为了伪装为 MIUI ROM，使应用自动向 `com.xiaomi.xmsf` 注册信息，建议使用伪装增强模块



## 优点

* 简单，安装非常简单
* 使用后，其他应用的 `XMPushService` 会自动禁用，就像在 MIUI，同时还能保证推送
* 完整事件记录，可以监控每个应用的 注册和推送
* 保留小米推送基础推送能力，默认禁用遥测/数据上报功能
* 自定义能力，定制你的消息内容与行为




## 开始使用

安装步骤非常简单 ：

* 前往 [Releases](https://github.com/magisk317/MiPushFramework/releases) 或 [CI](https://github.com/magisk317/MiPushFramework/actions/workflows/ci.yml)，下载最新的 APK 并安装。
* 跟着向导进行设置
* 可选：开启高级配置中的 推送服务保活 选项

### normal 与 vc105 版本的区别

Release 页面提供两个版本：`normal` 和 `vc105`，核心区别在于**消息传递方式**：

| 版本 | versionCode | 传递方式 | 说明 |
|------|------------|---------|------|
| normal | 1003003000 | bindService | 默认版本，推荐优先使用 |
| vc105 | 105 | startService | 兼容版本 |

小米推送 SDK 根据 xmsf 的 versionCode 决定传递方式：
- `versionCode >= 106`：使用 `bindService` 传递消息
- `versionCode == 105`：使用 `startService` 传递消息

**选择建议**：
- 优先使用 **normal** 版本，稳定性更好
- 部分 ROM（如 ColorOS）无法使用 bind 方式时，切换为 **vc105**
- MIUI 使用 normal 版本的额外好处：重启后不会被系统还原成官方版本

### 常见问题

- 是否支持分身（999）应用？
    - 目前没有这方面的计划，也没有测试过，不接受相关反馈


- 配置文件都有什么作用？我应该使用配置文件吗？
    - 配置文件可以修改消息的标题、内容及样式等，控制收到消息时忽略、亮屏或自动弹出等
    - 目前大部分“官方”配置都可以无脑使用，部分配置是否要使用，参见[仓库说明](https://github.com/magisk317/MiPushConfigurations)、配置名或配置中的 description 字段


- 是否应该安装为系统应用？
    - 推荐安装为系统应用。某些应用没有查看设备应用列表的权限，在获取应用列表时，**只能获取到系统应用列表**
    - 将推送服务安装为系统应用，可以让这些应用发现推送服务，从而进行服务注册


- 应用用上推送服务后，`com.xiaomi.push.service.XMPushService` 服务被禁用，是否正常？
    - 这是正常的，`XMPushService` 服务只应在没有系统推送服务的情况下启用
    - 当存在系统推送服务时，该服务上的相关功能直接由应用委托给系统推送服务处理



## 反馈问题

遇到任何问题，请先看看 Issues 里面有没有人提过。（常见问题：无法收到推送）
如果没有找到答案，请为每个问题提交一份 Issue，并务必带上如下内容，以便开发者解决：

* 你的 ROM 是什么，Android 版本是什么
* 有没有使用框架等工具

同时，请使用 设置-获取日志 打开日志预览，确认内容后分享日志文件并写进 Issue。

## 日志

框架会自动记录 JSONL 运行日志，按天轮转，默认保留 7 天，最低可设置为 1 天。您可以前往 设置-高级配置-获取日志 预览、分享或清空日志；导出包会清理旧文本日志并对 token 等敏感字段脱敏。

## UI 与主题约定

- UI 统一采用 Compose Material 3 Expressive 方案（含动态色与纯黑模式）。
- 对话框动作区统一使用 `DialogActionRow`，避免在 `AlertDialog` 按钮槽内手写按钮行。
- 列表页统一使用 `RefreshableLazyColumn`，加载体验统一使用最短可见时长策略。
- 动态路由统一使用 `AppDestinations.*.route(...)` 构造，避免手写字符串拼接。
- 运行时主链梳理见 `docs/architecture/current-runtime-call-flow.md`。
- 模块边界以 `docs/architecture/boundary-model.md` 为准，stock dump 只作为行为参考，不进入源码图。
- `./gradlew verifyModuleBoundaries` 会阻止 UI/settings/viewmodel 继续新增 deep `com.xiaomi.*` 依赖；确需例外时先放到 runtime/bridge adapter，再评估 baseline。



## 参与项目

请参考 [Contribution Guide](docs/CONTRIBUTION.md)

## 已知问题

* 对于部分小众的ROM （如 360OS）导致无法正常工作的情况，我们只会竭尽全力保证推送的运行，其它不妨碍推送的「特殊适配」会被忽略。对于这些情况，建议您更换更好的 ROM 以获得最佳体验。
* 努比亚ROM应用（第三方使用 MiPush 的应用）可能不会自动禁用其 XMPushService 并启动服务，请尝试将框架设为系统应用
* 锤子 ROM 下，Push 可以正确收到通知，但是通知栏没有提示 #143
* 一些通知 Feature 可能无法使用（如通知都会显示为推送框架发出，而不是目标应用）

## 感谢

* @Rachel030219 提供文件
* Android Open Source Project, MultiType, greenDao, SetupWizardLibCompat, Condom, MaterialPreference，GreenDaoUpgradeHelper, epic, Log4a，helplib，RxJava RxAndroid，RxActivityResult，RxPermissions, hiBeaver
* [HyperIsland](https://github.com/1812z/HyperIsland) 提供超级岛实现参考
* [HyperIsland-ToolKit](https://github.com/D4vidDf/HyperIsland-ToolKit) 提供 HyperIsland SDK 支持
* 酷安 @PzHown @lmnm011223 @苏沐晨风丶（未采纳） 提供图标
