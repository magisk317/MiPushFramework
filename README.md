# MiPushFramework

<div align="center">
    <a href="https://github.com/magisk317/MiPushFramework/releases">
        <img src="https://img.shields.io/badge/Get%20it%20on-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="Get it on GitHub" height="40"/>
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
[![Kotlin](https://img.shields.io/badge/Kotlin-2.5.0--RC2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Java](https://img.shields.io/badge/Java-27%2B-E76F00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.11.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.9.2-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.5.2-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-28-brightgreen?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-102-orange?style=flat-square)](https://github.com/libxposed/api) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)
<!-- badges:tech:end -->

</div>

在非 MIUI 系统上使用接近 MIUI 的小米系统级推送。

MiPushFramework 以 `com.xiaomi.xmsf` 的形式提供系统推送服务：接入小米推送的应用把推送委托给统一的框架进程，而不是各自常驻后台运行 `XMPushService`。项目同时保留通知改写、事件观测、配置规则、焦点通知、HyperIsland 超级岛和 Android 实况通知等兼容能力，当前开发分支最低支持 Android 9.0（API 28）。

这不是小米官方项目，也不保证所有 ROM 或所有应用都能获得相同行为；涉及厂商 ROM、目标应用策略、系统权限和 Xposed/LSPosed 环境时，实际效果以设备验证为准。

## 快速开始

1. 按设备架构从 [Releases](https://github.com/magisk317/MiPushFramework/releases) 下载两个 APK：`*_MiPush_*.apk`（管理器）与 `*_xmsf_*.apk`（XMSF 运行时）。
   - 运行时默认选 `normal`；两者区别见 [normal 与 vc105](docs/USER_GUIDE.md#normal-与-vc105)。
2. 安装两个 APK，在 LSPosed 中勾选**推荐作用域**。
3. 重启设备，按管理器引导完成授权。各架构资产见 Release 页的 **Download based on your ABI** 表格；Zygisk 模块的选择见 [用户使用指南](docs/USER_GUIDE.md#mipushzygisk-模块)。

## 文档

- [用户使用指南（本 fork 差异与架构）](docs/USER_GUIDE.md)
- [MiPush Framework 使用指南（上游完整教程）](https://blog.bzmshang.top/MiPush-Framework_User-Guide)
- [隐私政策](docs/PRIVACY.md)

## 致谢

- [@KiritoXDone](https://github.com/KiritoXDone) 提供新版分层自适应启动图标。
- [HyperIsland](https://github.com/1812z/HyperIsland) 提供超级岛实现参考。
- [HyperIsland-ToolKit](https://github.com/D4vidDf/HyperIsland-ToolKit) 提供 HyperIsland SDK 支持。
- @Rachel030219 提供文件；酷安 @PzHown @lmnm011223 @苏沐晨风丶（未采纳）提供图标。
- Android Open Source Project, AndroidX, Jetpack Compose, Material 3, Kotlin, kotlinx.coroutines, kotlinx.serialization, Koin, Room, DataStore, Navigation, Lifecycle, Miuix, JetBrains Markdown, JUnit, MockK, kotest, Detekt, Kover, KSP, coil3, OkHttp, ANIP SDK, libsu, libxposed API, LSPosed HiddenApiBypass。

# 赞助与捐赠

如果本项目对你有帮助，欢迎支持开发者。你的支持会直接用于项目维护与持续迭代。

赞助名单与说明请见：[赞助与捐赠文档](docs/DONATIONS.md)。

| 支付宝收款码 | 微信赞赏码 | 微信收款码 |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |
