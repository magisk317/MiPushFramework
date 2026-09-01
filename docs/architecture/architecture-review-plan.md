# MiPushFramework Architecture Review

## Final status

运行时双 APK 架构和主要通知 ownership 边界在源码层已收口。源码、JVM、静态检查和构建结果通过；需要真实 ROM、跨进程或跨用户触发的项目仍未宣称完成。

## Architecture invariants

- `:xmsf` 只打包可安装的 `com.xiaomi.xmsf` runtime。
- `:mipush` 拥有 manager UI 和 Xposed 入口。
- manager 与 runtime 通过签名保护的 Binder 合同通信。
- 远端读取失败保持 unavailable，不转换为空成功结果。
- 通知、Island、HyperIsland、proxy、点击路由和生命周期行为保持既有兼容目标。
- 所有可获得的通知、应用、事件、注册、媒体、KeepAlive、Island 和清理状态均使用明确 Android user scope；缺失或非法身份按 fail-closed 处理。

## Completed results

### Runtime and data ownership

- 应用、事件、注册、通知 channel、media-session、dedup、extension、KeepAlive 和 package cleanup 已完成用户隔离。
- God-file 拆分已完成且没有抬高门禁：当前 `ManagerRuntimeClient.kt` 为 893 行，`AndroidPushRuntime.kt` 为 872 行；`ManagerRuntimeBindSession.kt` / `ManagerRuntimeDiagnostics.kt` 分担 manager bind/diagnostic 支持，`AndroidPushRuntimeWindowSupport.kt` 分担 dedup/window/bounded-map 策略，连接状态转移与 session merge 集中在 `RuntimeConnectionObservationCoordinator`。公开 facade、单一 runtime state owner 和锁边界保持不变。
- `PushRuntimeRegistrationChannelObservationSink` 的 registration/channel state 方法现在显式携带 `androidUserId`；shell execution/channel adapters 将已解析的用户传入 runtime adapter，避免跨模块 observation 边界隐式回退到 current user。`MiPushRuntimeBridge` 的 payload dedupe 也继续传递上游已解析 user，`RuntimeRegistrationCoordinator.replayPending()` 不再丢失 user。
- JVM 测试中的 primary-user 场景均显式传入 `userId = 0`；observation sink 合同缺少 user、registration record 查询漏参和 replayPending 丢参已修复，runtime 与 shell 全量测试现已通过。
- manager DTO、Binder 校验、runtime read/write、数据库查询、本地 adapter 和 dedup helper 均使用显式 user scope；生产路径不再用 `coerceAtLeast(0)` 或异常回退到 user 0。legacy 数据库行迁移和输出 DTO 的 `0` 默认值属于兼容协议，不能作为 caller authorization。
- 跨包、跨用户的 stale ID、event、channel、provider、配置和 fallback 读取均按 ownership 校验。
- 注册 secret、last-receive、事件解密和 package cleanup 均使用目标用户作用域。
- EventDb 的 current/显式 user、DatabaseUtils 的初始化/迁移 telemetry user、RuntimeEventQueryPolicy 的查询 user、RegisteredApplicationDb facade 的 current/显式 user、manager application read source/reader 的 current、persisted/transient projection user、manager notification channel reader 的 current/page-token user 以及 ManagerEventRuntimeReader 的入口/显式 query user 均已 fail-closed；manager application/event/channel/write 请求模型缺失 user 时使用 `-1` 并由 Binder/协议校验拒绝，legacy 数据库 schema 的 user `0` 默认值和一次性迁移 SQL 保持不变。输出模型的构造默认值只服务兼容性，不能作为 caller authorization。

- connection status/client-change/channel open/reset/network/redirect、host/GSLB/HostManager、socket candidate/retry/sink-down 与 SLIM handshake/payload/write 的可平台化决策已统一到 `:core commonMain`；`Connection`、`SocketConnection`、`HostManager`、`BlobReader`、`BlobWriter` 真实执行路径均已接线。Android/vendor 继续拥有 URL、socket、I/O、Fallback 持久化、Blob/RC4/CRC/protobuf、Service/Intent 和 telemetry side effects。
- manager contract validation/size arithmetic, focus parsing/planning, page activation/request isolation, snapshot validity/sorting, JSONL encoding/redaction, and daily route quota decisions are also core-owned. Parcelable/AIDL, Compose/NavController, Binder scheduling, and filesystem operations remain adapters in their owning modules.

### Notification and Island

- active notification 查询通过目标包 UID/user identity，local fallback 仅接受目标 marker 和目标用户。
- channel/group 创建、删除、名称补全和 delegated notification lifecycle 保持目标包 ownership。
- Island proxy 的 post、cancel、dedup、ID、visual snapshot、preference 和 broadcast 均携带用户身份。
- Top、Sweet、VoIP、extension、focus、progress、click intent 和 cancellation 状态均具备产品侧用户/包隔离。
- 无法解析目标用户时，发布、取消、provider 读取和配置读取均 fail-closed。

### Boundary and security

- vendor lifecycle 通过 observer/plan 将策略决策交给 xmsf。
- vendor active notification facade 增加显式 user API；旧无用户接口仅保留兼容。
- vendor product import baseline 未新增。
- Android 17 optional reflection 只把明确的缺失类/方法视为 unavailable 并进入受限 fallback；`SecurityException`、`InvocationTargetException` 等真实调用失败继续保持 failure 语义。vendor notification、DeviceInfo 和可选 push-manager 探测均有源码合同，最后一次已安装的 Android 17 smoke 未发现匹配的项目 warning。
- `XSpaceXmsfInstallKeeper` 对 dual-app 状态读取失败保持 unavailable，不执行 root 或 package mutation；user 999 同步只使用显式、固定的包命令。正常同步路径已覆盖，异常注入和 user 999 第三方通知仍不是设备完成证据。
- `MiPushZygisk` native hook 属于外部 source/ref/NDK ABI；MiPushFramework 只消费其构建产物，native registration 或 ABI 兼容性不能由本仓库 Kotlin/Xposed 源码结论替代。
- 外部 Binder、provider、SDK ingress、intent、payload、package/UID ownership 和 telemetry boundary 均有源码合同保护。
- 未恢复 OneTrack、tiny-data、LNS/LNC、installed-app collection、log upload 或 notification-exposure telemetry。

## Code-only evidence summary

- 当前受影响模块验证使用 `:core:jvmTest`（KMP JVM target），以及 manager contract/client/UI、notification、XMSF shell 和 Xposed 的对应测试/编译任务；捕获日志无 Kotlin compiler warning。
- `./gradlew qualityGateKoverVerify --warning-mode=all --console=plain` 通过；目标为 `common/core/xposed/xmsf:shell`，门槛分别为 10%/10%/10%/7%，不再把仅用于打包的 `:xmsf` 根应用误作为 0% 实现模块。
- 当前工作树执行 `./gradlew check qualityGateKoverVerify :xmsf:assembleNormalDebug :mipush:assembleDebug --warning-mode=all --console=plain`，1362 actionable tasks 全部通过；Kover 只应用于四个明确门禁模块，不让零测试的 packaging/vendor 模块污染普通 `check`。
- `manager:port` JSON 序列化/default/未知字段兼容测试通过；CallMessage、PushCommonProvider、regSec 缺失目标偏好文件和 xmsf runtime regression tests 通过。
- `verifyGodFileLimits`、`verifyModuleBoundaries`、ShellCheck、CI shard 脚本测试和 `git diff --check` 通过。
- `.kiro` 下的历史规格保留原始任务上下文；当前架构归属、验证命令和开放项以本文、`xmsf-decomposition-roadmap.md` 及仓库根/模块 `AGENTS.md` 为准。

## Last device evidence (2026-08-31)

The following device evidence belongs to the 2026-08-31 installed APK. The 2026-09-01 policy and
manager refactor has passed local build/test gates but has not been installed or replayed on a
device, so it does not extend these runtime claims.

- 该次设备验证使用 `universal_normal_xmsf_v1.0.1-20260831_221345_debug.apk` 和 `universal_MiPush_v1.0.1-20260831_221345_debug.apk`；SHA-256 分别为 `22045fd002759817b9df0cd3a6d2e931b476813f9206def7dbf38509af8b23eb`、`8a0ba99c2a9e1fc8eb00d4ffa862b680fe135be90bf133634cde27247d0a0e30`，MD5 分别为 `e54b4e63dda990587fdfd13980a3164b`、`1a5f1a257edbd44280488da6b50634c4`。两包经本地/MBP SHA-256 和 MD5 一致性校验后使用 `adb install -r` 安装，未清除应用数据。
- 该次验证设备为 MBP 连接的 Xiaomi `pudding`，Android 17/API 37，current user 0。覆盖安装后执行受控冷启动，XMSF 主进程、`:services`、manager、`ServiceBoxService`、`XMPushServiceCore` 和 `ManagerRuntimeService` 均存活。
- manager Binder 连续读取 `connection_snapshot` 成功，运行时状态保持 `Connected`；当前最终日志窗口没有 XMSF/manager crash 或 ANR。
- Android 17 真机还暴露了 exported `XMPushService` facade 被外部 `startForegroundService` 调用时的 deadline crash；`stopSelfResult()` 单独不足以满足该 ROM。最终实现先通过 `ForegroundHelper` 瞬时 `startForeground`/`stopForeground` 确认系统契约，再完成分发和 `stopSelfResult()`。显式 `am start-foreground-service` 后等待超过 deadline，进程和核心服务保持存活，`ForegroundServiceDidNotStartInTimeException` 为 0。
- 首轮真机日志暴露出目标应用没有 `shared_prefs/mipush.xml` 时 `Utils.getRegSecs()` 触发 `ContextImpl` 目录创建警告洪泛。加入文件存在性保护并重装最终 APK 后，同一 manager event-page/调试解码入口中 XMSF `ContextImpl` 警告从约 9038 行降为 0；最终冷启动窗口的少量 framework `ContextImpl` 行均无项目调用栈，不构成项目 warning flood。
- `BackgroundActivityStartEnabler` 失败根因已确认：当前 XMSF 的 `POST_NOTIFICATIONS` runtime permission 为 denied，自有 `MPF.BAFE` 通知被 NMS 静默丢弃，而 `activeNotifications` 返回 XMSF 作为 `opPkg` 发布的 delegated records。修复后在权限不可用时优先选择含 PendingIntent 的 existing active donor（精确 `MPF.BAFE` 身份仍优先），且只清理自有 tag/channel，不取消 donor。最终 Android 17 专用窗口首次 capture 成功，日志为 `source=ExistingNotification`；选中的 Alipay donor 精确身份记录前后保持 `1 -> 1`（此前独立窗口的 notification records 总数为 `44 -> 44`）。
- 该窗口只证明当次安装 APK 的安装、冷启动、foreground-facade 契约、核心服务、manager Binder、BAFE active donor 和既有长连接；没有触发真实跨 UID 注册、通知点击、XSpace、网络切换、KeepAlive negative control 或 package cleanup。

## Historical device evidence

- 历史 APK：`universal_normal_xmsf_v1.0.1-20260830_070831_debug.apk`，versionCode `1003003000`，versionName `1.0.1-20260830_070831`；其 hash、安装链路和设备状态只对应 2026-08-30 的旧工作树。
- user 0 设备验证：历史窗口中 xmsf 主进程、`:services`、`ServiceBoxService`、`XMPushServiceCore` 和 `ManagerRuntimeService` 均运行。`MainProcBridgeService` 已按 stock 7.5.29-C 基线移除，不能再作为运行证据。manager handshake、connection snapshot 和 `RegistrationStateCompat` 均曾返回 primary-user 作用域结果；精确 PID、主机和设备路径不写入此持久文档。
- user 999 XSpace 验证：历史窗口中 user 999 的 xmsf 与 manager 均已安装，manager handshake、`ManagerRuntimeService` 绑定和 application list 均使用 `userId=999`，未把 primary-user 的 application list 数量投影到 XSpace。该用户的 registration-specific 查询未单独触发，不能宣称注册去重/registration record 已完成跨用户设备验证。
- 安装后历史 logcat 曾出现两次 user 0 启动期 framework `APP CRASH(EXCEPTION)`，堆栈止于 `ActivityThread.handleBindApplication` / `ConfigurationController.updateLocaleListFromAppContext`，未进入本项目 runtime 代码；结合 package-update kill 记录，判断为覆盖安装后的 ROM/LSPosed 旧 APK 资源路径竞态。随后新 APK 进程稳定运行并完成 Binder 验证；该启动竞态保留为设备异常记录，不能写成“无 crash”。
- 设备实时 dumpsys/logcat 未证明 heartbeat 学习、网络切换、KeepAlive negative control、真实注册去重、malformed/telemetry rejection、通知全链路或 package cleanup 的完整行为；这些仍需独立场景验证。

## Open source follow-up

- 请求入口、存储 facade 和 notification/registration 关键路径的非法 user 已在源码层 fail-closed；后续新增 user-scoped DTO 必须沿用 `-1` 缺省与显式校验规则。
- `RuntimeEventDeletionRepository` 的显式删除/恢复请求继续保持非法 requested user fail-closed；历史数据库迁移仍固定把未带 user 字段的 legacy rows 归入 user 0。

## Open device gates

最近一次设备 APK 已覆盖 user 0 安装、冷启动、核心服务、manager Binder 和稳定 connection snapshot；历史窗口另有旧 APK 的 user 999 application-list 证据。2026-09-01 重构后的 APK 尚未安装，以下仍未完成或仅有部分证据：

- heartbeat 学习、ping timeout、网络切换和 alarm 重注册。
- KeepAlive Binder/config、observer/polling、服务绑定和 anti-kill negative controls。
- 跨 UID SDK ingress、registration-specific 去重/控制动作、package clear/removal 和 malformed/telemetry rejection；user 999 的 registration-specific 查询尚未单独触发。
- delegated notification、HyperIsland、Top、Sweet、VoIP、click routing 和 self-update revival 的完整场景；`BackgroundActivityStartEnabler` 在当前 Android 17/通知权限拒绝条件下的 active donor capture 已完成，但这不替代真实后台 activity launch 的跨 UID 场景验证。
- provider caller authorization、工作资料用户和更复杂的同包跨用户隔离场景。
- 覆盖安装后的两次 framework 资源初始化 crash（旧 APK 路径竞态）尚未在第二台 ROM 或冷启动场景复现/修复；当前证据表明它发生在应用代码之前，不能归因于本轮 facade/runtime user-scope 修改。

历史 APK 的设备证据不能替代上述场景门禁；特别是“user 0 与 user 999 的 manager/runtime/application-list 已实际运行”不等于所有 registration、notification、cleanup 和 SDK ingress 行为均已跨用户完成验证。


## Manager background and framework identity registration audit (2026-08-30)

- `KeepAliveHook` 的 `desiredOomAdj`、standby/doze bypass、`killLocked` 和 package-kill guard 均严格匹配 `com.xiaomi.xmsf`；`io.github.magisk317.mipush` 不属于 anti-kill 保护目标，也不会被该 hook 主动杀死。对应 manager package negative-control 已加入 `KeepAlivePolicyTest`。
- manager 的 `finishAndRemoveTask()`/`Process.killProcess()` 仅存在于用户明确切换 launcher icon 的 `LauncherIconController.applyAndRelaunch()/exitOnly()` 流程；普通 Home、Binder reconnect、runtime recovery 均不调用它们。trampoline `ManagerLauncherActivity` 的 `noHistory`/`excludeFromRecents` 只作用于入口，实际 manager task 由 `WelcomeActivity`/`MainActivity` 持有。
- 受控普通后台复现中，user 0 与 user 999 的 manager 进程在启动后按 Home 仍存活，对应任务也保留；未观察到当前测试窗口的 `finishAndRemoveTask`、manager `force-stop` 或 manager kill。历史 exit-info 的 manager 记录来自 ROM/第三方策略：`LockScreenClean` force-stop，以及 `umms_selfcheck` 的 `OTHER KILLS BY SYSTEM`。因此不能把该问题归因于 xmsf anti-kill，也不应为 manager UI 扩大常驻 anti-kill；ROM 的后台/任务清理策略仍是设备侧开放项。
- application detail/diagnostics 入口在 user 0 触发了 `ManagerRuntime application_detail`、`application_diagnostics` 和 registration read；日志明确携带 `userId=0`。user 999 的 application list 使用 `userId=999`，仍为 `items=1 using=1 total=2`；本次 `com.xiaomi.xmsf` 详情没有形成独立 user 999 detail 调用，不能宣称 user 999 registration-specific detail 已完成。
- xmsf self-registration 与第三方 app force-register 已明确分离。`ProactiveMiPushRegistrar` 只扫描带 credentials/legacy service 的第三方 app；`FirstRegister`、`RetryRegister`、boot/network runtime dispatch 才是 xmsf 使用固定 `Constants.APP_ID/APP_KEY` 调用 `MiPushClient.registerPush()` 的路径。
- 新增 `KEY_ENABLE_FRAMEWORK_SELF_REGISTRATION`，默认 `false`。push service 启动、boot/network dispatch、FirstRegister、历史 RetryRegister 和 execution bridge 均遵守该 opt-in；关闭时仍启动 runtime connection，不调用 `MiPushClient.registerPush()`，也不写入虚假的 framework registration request record。已有 xmsf regId 只读显示，不会被自动清除。显式 opt-in API 为 per-user preference，非法 current user fail-closed。
- 回归覆盖：已注册不重复注册、未注册且 opt-in 时仍保留请求/retry 合同、关闭 opt-in 时不请求不排队、runtime host 禁止时 boot/network 不产生 framework registration record、manager package 不命中 KeepAlive policy、非法 user 的 Island click identity fail-closed。源码 targeted tests 已通过。
