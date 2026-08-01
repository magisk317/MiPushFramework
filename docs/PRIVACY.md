# Privacy Policy / 隐私政策

**Effective Date / 生效日期:** 2026-08-01

[English Version](#privacy-policy-for-mipush-manager) | [中文版本](#mipush-管理器隐私政策)

---

## Privacy Policy for MiPush Manager

### 1. Scope

This policy applies to **MiPush Manager** (`io.github.magisk317.mipush`), the management application distributed through Google Play. It does not cover the separately installed XMSF runtime (`com.xiaomi.xmsf`), which cannot be distributed through Google Play under that package name.

MiPush Manager displays and manages the state of a compatible XMSF runtime through an authenticated local Binder interface. Without that separately installed runtime, runtime-related management features are unavailable.

### 2. Data Processed by the App

#### 2.1 Installed Applications and Usage Information

MiPush Manager may access:

- installed application package names, labels, icons, versions, and MiPush compatibility or registration state;
- application usage events or the current foreground application, when you grant Usage Access.

This information is used locally to list MiPush-capable applications, show their status, apply per-application settings, and support compatibility features. The complete installed-app list and usage history are not included in technical analytics. When you perform an operation on a specific app, the target package name may be included with that operation's technical result as described below.

#### 2.2 Push Runtime State and Records

When a compatible XMSF runtime is installed, MiPush Manager can display and manage runtime data such as:

- application registration and channel state;
- connection health and message counters;
- push registration, delivery, command, and notification event records;
- notification channels, runtime settings, and diagnostic logs.

This data may contain package names, push metadata, device or registration identifiers, and message-related diagnostic content. It is read from the local XMSF runtime and is not uploaded by MiPush Manager as part of normal management operations.

#### 2.3 Settings and Local Files

MiPush Manager stores settings, a random per-installation identifier used for analytics, manager logs, and temporary diagnostic export files in its private app storage. Android may include eligible app data in system backup because app backup is enabled.

You can delete manager-owned data by clearing MiPush Manager's app data or uninstalling it. Runtime records owned by the separate XMSF package must be cleared through the in-app record/log controls, by clearing the XMSF package data, or by uninstalling that runtime.

#### 2.4 Google Play Donations

The Google Play build offers optional one-time donations through Google Play Billing. Google processes the transaction and payment account information. MiPush Manager receives product information, purchase status, and a purchase token only as needed to complete and consume the donation purchase. It does not receive card or bank-account details, does not grant an account-based entitlement, and does not send purchase tokens to a project-operated server.

Google handles this data under the [Google Privacy Policy](https://policies.google.com/privacy).

### 3. Permissions and Special Access

Depending on Android version and the features you use, MiPush Manager may request or declare:

- **Internet:** remote configuration retrieval, project links, optional technical analytics, and Google Play Billing in the Play build;
- **Notifications:** manager status, test, and diagnostic notifications;
- **All packages access:** locating MiPush-capable applications and presenting per-app controls;
- **Usage Access:** foreground-app and recent-usage detection for compatibility behavior;
- **Display over other apps:** compatibility or foreground-detection behavior you explicitly enable;
- **Battery optimization exemption and exact alarms:** reliability and scheduled relaunch behavior;
- **Root access:** optional advanced operations, requested only after an explicit user action;
- **XMSF manager binding permission:** authenticated local communication with the separately installed runtime.

Permissions and special access are used only for the corresponding features. You can deny or revoke optional access in Android settings, although the related feature may stop working.

### 4. Technical Analytics

Technical analytics is enabled by default and can be disabled in MiPush Manager settings. When enabled, the app sends pseudonymous technical telemetry to the project's GitLab observability endpoint, including:

- a randomly generated per-installation identifier;
- app version and release/debug environment;
- coarse daily, weekly, and monthly activity markers;
- operation names and technical result fields such as stage, success/failure, and reason;
- the target application's package name for specific actions such as force registration, diagnostic message replay, or event deletion.

The random installation identifier is not the Android advertising ID and is not intentionally linked to an account, name, or email address, but its transmission is still data collection. Telemetry is intended for feature usage, reliability, and failure diagnosis. It must not contain push message bodies, registration secrets, tokens, the complete installed-app list, or usage-history records.

Disabling analytics stops new telemetry events from being sent. The local installation identifier remains in private app storage until app data is cleared or the app is uninstalled. Telemetry already transmitted is retained according to the project's observability-service settings and cannot currently be deleted through an in-app account or per-installation deletion control.

### 5. Diagnostic Export and Sharing

MiPush Manager can build a diagnostic archive containing manager and XMSF runtime logs. Log sanitization can redact tokens, registration identifiers, device identifiers, and other sensitive values, but no automated redaction can guarantee removal of every sensitive value.

Diagnostic archives are not uploaded automatically. They leave the device only when you explicitly share or otherwise export them through Android. Review an archive before sharing it and send it only to a recipient you trust.

### 6. Network Services and Data Sharing

MiPush Manager does not sell personal data and does not operate a mandatory backend that collects your push records. Network access may involve:

- GitHub or GitLab when retrieving project information or remote configuration files;
- the GitLab observability service when technical analytics is enabled;
- Google Play when you view or make an optional in-app donation;
- websites or applications you choose to open from project links.

The separate XMSF runtime connects to the XMPP host configured by you or by the runtime defaults. That connection and the push data it carries are outside the Google Play manager package covered by this policy.

### 7. Security

Runtime management uses a restricted local Binder permission and caller validation. App-private files are not exported directly; diagnostic sharing uses temporary content URIs granted only to the selected recipient. Network transport uses HTTPS where the contacted service supports it.

### 8. Children's Privacy

MiPush Manager is not directed to children under 13. We do not knowingly collect personal data from children.

### 9. Policy Updates

This policy may be updated as the application changes. Updated versions will be published in this repository.

### 10. Contact

For privacy questions, contact:

**Email:** play@usdt.edu.kg

---

## MiPush 管理器隐私政策

### 1. 适用范围

本政策适用于通过 Google Play 分发的 **MiPush 管理器**（`io.github.magisk317.mipush`），不适用于另行安装的 XMSF 运行时（`com.xiaomi.xmsf`）；后者因包名原因不通过 Google Play 分发。

MiPush 管理器通过经过身份校验的本地 Binder 接口显示和管理兼容 XMSF 运行时的状态。未另行安装运行时的情况下，与运行时相关的管理功能不可用。

### 2. 应用处理的数据

#### 2.1 已安装应用与使用情况

MiPush 管理器可能访问：

- 已安装应用的包名、名称、图标、版本，以及 MiPush 兼容或注册状态；
- 在你授予“使用情况访问权限”后，访问应用使用事件或当前前台应用信息。

这些信息仅在本地用于列出支持 MiPush 的应用、显示状态、应用单独配置和支持兼容功能。完整的已安装应用列表和使用记录不会包含在技术统计数据中；当你对特定应用执行操作时，目标应用包名可能随该操作的技术结果一并发送，具体见下文。

#### 2.2 推送运行时状态与记录

安装兼容的 XMSF 运行时后，MiPush 管理器可以显示和管理以下运行时数据：

- 应用注册与通道状态；
- 连接健康状态与消息计数；
- 推送注册、投递、命令和通知事件记录；
- 通知渠道、运行时设置和诊断日志。

这些数据可能包含包名、推送元数据、设备或注册标识符，以及与消息有关的诊断内容。数据从设备上的 XMSF 运行时读取，正常管理操作不会由 MiPush 管理器将其上传。

#### 2.3 设置与本地文件

MiPush 管理器会在自身的应用私有存储中保存设置、用于技术统计的随机安装标识符、管理器日志和临时诊断导出文件。由于应用允许 Android 系统备份，符合系统备份条件的应用数据可能由 Android 备份。

你可以通过清除 MiPush 管理器的应用数据或卸载管理器删除其自有数据。由独立 XMSF 包保存的运行时记录，需要通过应用内记录/日志清理功能、清除 XMSF 应用数据或卸载该运行时删除。

#### 2.4 Google Play 捐赠

Google Play 版本通过 Google Play 结算提供可选的一次性捐赠。交易和付款账号信息由 Google 处理。MiPush 管理器仅会为完成并消耗该笔捐赠购买而接收商品信息、购买状态和购买 token，不会获取银行卡或银行账号信息，不会授予与账号绑定的权益，也不会把购买 token 发送到本项目运营的服务端。

Google 按照其[隐私权政策](https://policies.google.com/privacy)处理相关数据。

### 3. 权限与特殊访问

根据 Android 版本和你使用的功能，MiPush 管理器可能声明或申请：

- **网络访问：** 获取远程配置、打开项目链接、发送可选的技术统计，以及在 Play 版本中使用 Google Play 结算；
- **通知权限：** 显示管理器状态、测试和诊断通知；
- **所有应用查询权限：** 查找支持 MiPush 的应用并提供逐应用管理；
- **使用情况访问权限：** 检测前台应用和近期使用情况以支持兼容功能；
- **在其他应用上层显示：** 用于你明确启用的兼容或前台检测功能；
- **忽略电池优化与精确闹钟：** 用于可靠性和计划重新启动行为；
- **Root 权限：** 用于可选高级操作，仅在用户明确操作后请求；
- **XMSF 管理绑定权限：** 与另行安装的运行时进行经过身份校验的本地通信。

权限与特殊访问仅用于对应功能。你可以在 Android 设置中拒绝或撤销可选权限，但相关功能可能无法继续工作。

### 4. 技术统计

技术统计默认开启，可在 MiPush 管理器设置中关闭。开启时，应用会向本项目的 GitLab 可观测性端点发送以下使用假名标识的技术数据：

- 随机生成的、按安装区分的标识符；
- 应用版本和正式版/调试版环境；
- 粗粒度的每日、每周和每月活跃标记；
- 操作名称，以及阶段、成功/失败、原因等技术结果字段；
- 在强制注册、诊断消息重放或删除事件等特定操作中涉及的目标应用包名。

随机安装标识符不是 Android 广告 ID，也不会被有意关联到账号、姓名或邮箱，但发送该标识符仍属于数据收集。这些数据用于分析功能使用情况、可靠性和失败原因，不应包含推送消息正文、注册密钥、token、完整的已安装应用列表或应用使用记录。

关闭技术统计后，应用不会再发送新的统计事件；本地安装标识符会保留在应用私有存储中，直到清除应用数据或卸载应用。已经发送的遥测数据按照项目可观测性服务的设置保留，目前无法通过应用内账号或按安装标识符的删除入口清除。

### 5. 诊断导出与分享

MiPush 管理器可以生成包含管理器和 XMSF 运行时日志的诊断压缩包。日志脱敏功能可以隐藏 token、注册标识符、设备标识符等敏感值，但任何自动脱敏都无法保证移除全部敏感内容。

诊断包不会自动上传。仅当你通过 Android 主动分享或导出时，诊断包才会离开设备。分享前请检查压缩包内容，并仅发送给你信任的接收方。

### 6. 网络服务与数据共享

MiPush 管理器不会出售个人数据，也不运营一个强制收集推送记录的后端。网络访问可能涉及：

- 获取项目信息或远程配置文件时访问 GitHub 或 GitLab；
- 开启技术统计时访问 GitLab 可观测性服务；
- 查看或进行可选应用内捐赠时访问 Google Play；
- 访问你从项目链接中主动打开的网站或应用。

独立 XMSF 运行时会连接到由你配置或运行时默认指定的 XMPP 主机。该连接及其承载的推送数据不属于本政策所覆盖的 Google Play 管理器包。

### 7. 安全措施

运行时管理使用受限的本地 Binder 权限和调用方身份校验。应用私有文件不会直接导出；诊断分享通过临时内容 URI，仅向你选择的接收方授予访问权。网络服务支持时使用 HTTPS 传输。

### 8. 儿童隐私

MiPush 管理器不面向 13 岁以下儿童。我们不会有意收集儿童个人信息。

### 9. 政策更新

本政策可能随应用功能变化而更新，更新版本会发布在本仓库中。

### 10. 联系方式

如有隐私相关问题，请联系：

**邮箱：** play@usdt.edu.kg
