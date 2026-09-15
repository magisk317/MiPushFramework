# MiPushFramework Stock Parity Verification

## Final status

源码和本地构建结果已验证；设备和跨 UID parity 仍为部分完成。任何没有当前 APK、当前 PID、时间窗口和实时设备输出的项目均不视为设备完成。

## Source results

- `com.xiaomi.xmsf` runtime 与 manager/client 保持双 APK 分层。
- 应用、事件和注册数据使用 `(user_id, package)` ownership。
- notification channel 使用 `--noredact` 优先的共享读取和 DTO/domain 边界。
- active notification、delegated identity、channel/group、local fallback、Island proxy、Top、Sweet、VoIP、extension、click intent 和 cancellation 已完成源码级用户/包隔离。
- KeepAlive、heartbeat、registration ingress、package cleanup、provider、Binder、manifest、telemetry boundary 和 configuration read 均有源码合同或 focused tests。
- 失败读取保持 unavailable；非法包、UID、user、payload、intent、event 或 provider caller 按 fail-closed 处理。
- connection/socket/SLIM/host 的 vendor-neutral plans 已统一到 `:core commonMain`，真实 vendor transport/host/stream 路径已接线；Android/vendor 保留网络、I/O、协议、持久化和 side effects。
- manager contract validation/size arithmetic, focus parsing/planning, page activation/request isolation, snapshot validity/sorting, JSONL encoding/redaction, daily route quota decisions, and manager-client availability/backoff are core-owned; Android/Parcelable/Binder/Compose/filesystem remain adapters.
- Android 17 optional reflection 已统一使用可区分缺失与真实失败的调用边界；notification、DeviceInfo、可选 push-manager 探测和 XSpace 同步均有 fail-safe/source contracts。缺失 capability 只降级为 unavailable/fallback，不把权限或调用异常伪装成成功。
- 未恢复 OneTrack、tiny-data、LNS/LNC、installed-app collection、log upload 或通知曝光采集。

## Island 与派发一致性审计（2026-09-12）

对照 stock XMSF 7.5.29（combined-jadx）与 7.4.67-C baseline dump 的专项审计，修复以提交为准（详见 CHANGELOG v1.0.3）：

- 焦点鉴权：`com.xiaomi.xms.auth` 服务改为 allow-all，等价 stock 国际版在 `fetchAuthResult` 同一位置的无条件成功；stock 的本地白名单实为云端商务注册表（`AuthApiFetcher` + `AuthDB`），在替换 XMSF 上不可复现，逐包硬编码必然遗漏（京东/高德实测被静默丢弃）。Path B/C 缺席兜底随之删除，Path A 收窄语义保持。pudding 真机验证：三方原生 focus 通知 `authorized (allow-all)` → `onAuthSuccess` → 上岛（高德步行导航端到端）。
- `MESSAGE_ARRIVED` 一致性五修：app 侧 arrived 处理器返回消息（修复 `onNotificationMessageArrived` 断点）；到达回调不再按容器动作过滤；非可显示 Notification 载荷 handoff 给目标应用（stock `m0.f→a()` 无动作门）；chid 10/11 Android 14+ `FLAG_RECEIVER_EXPORTED` 补偿；注册唤醒广播真正发送。
- 注册与 ack：`autoMarkPkgs`（thrift 字段 21）恢复透传，激活 `MiuiPushMessageReceiver` 既有消费端；unregistered/push-closed 按消息丢弃接 `absent-target` ack；`empty_package_name` 与 `app_no_receiver` 接通 wrong-message 错误 ack（stock `l()`/`s0`/`n0` 语义）。
- 设计确认（非缺陷）：SendMessage 透传的展示分类与 6 小时 replay-drop 为产品决策；FCM assemble-push 解密分支在本架构零影响（无 FCM receiver、token 从不上传，服务器不会对 chid-5 会话包装 FCM），仅当未来为无自带 SDK 的托管应用引入 FCM 回退通道时才需要移植 `b0:49-52` 等价逻辑。
- 上游同步原则（2026-09-12 确立）：stock 沿 3.7.9→7.4.67-C→7.5.29 移除的功能，我们同步移除，不留历史债务。据此删除 `FocusNotificationCollectionFilter`（stock 7.4.67-C `com.xiaomi.push.sort.b/c/d` 的忠实移植；原件与消费端 `local.k` 曝光统计已在 `2026-04-13-stock-baseline` dump 定位，7.5.29 已整体移除，消费端我们从未实现）；`NotificationListener` 保留（Sweet/Top 协调与事件观测仍在使用）。30 秒非显示去重已改为空 messageId 旁路（stock 对这些动作无时间窗去重）。
- 全仓上游移除扫描（2026-09-12，子智能体三方对照）：OnlineConfig 键全表 diff（7.4 消费 104 真键 → 7.5.29 `ae.j` 115 真键，9 个 7.4 键在 7.5 消失）+ manifest 组件 diff（332→449，仅 `MainProcBridgeService` 与 `CollectRunningTasksService` 被上游删除）。结论：唯一真残留 = `OnetrackSwitch(140)` 门控链，已删（`refactor: drop OnetrackSwitch(140)`，入站 140 按未知键解码与现行 stock 一致）；LNS/LNC、CollectRunningTasks、MainProcBridge 我们从未镜像或已提前移除（absence 测试守卫）；ads q0/r0 ack、tiny-data、subscribe-remind、hyper_type/TTS/CallKit、OcVersion/DailyCheck、PushControl、MiCloud provider 均验证 7.5.29 仍在发布，KEEP（其中 ads ack 构造器在我们的树里是死镜像，属接线缺口而非上游移除债，勿以本原则为由删除）；`island.prefs`/IconPack 为纯产品自有面（stock 从未存在）；pinned 3.7.9 字典（含 fake-slot 与 StatData/CollectionPlugin 名）为客户端冻结保真，KEEP。观察项已处置（2026-09-12）：xposed 伪装常量与 core `PushVersionInfo.STOCK_XMSF_APP_VERSION_*` 已由 `7.4.67-C / 70004067` 升为 `7.5.29-C / 70005029`（fakeXmsfPackageInfo 与 service 包 self-report 两条链，含服务端可见的 account-registration `appversion`/sync-info 参数）；vendor 的「Stock reference: 7.4.67-C」出处注释记录反混淆来源历史，保持不动。扫描范围限制：仅 pudding 具备 7.4+7.5 配对 dump；无 7.5 时代客户端 SDK dump；混淆字母靠协议字符串/键名回溯；UI 文案未审。
- 上游新增对齐扫描（2026-09-13，与「移除扫描」方向相反的专项，四维度：OnlineConfig 键消费端 / manifest 组件 / 入站控制分支 / 线程字典与 SDK 演进，两路 agent + 串行第二波）：
  - 已补齐：ingress collection 回退语义与 thirdparty hint；pinned 注册字段 103/104 与结果字段 21；服务侧 `clear_push_message` 消费与 `e1.b` 全语义 ack；7.5 订阅通道同步栈（结构体/协议/消费/`SUB_GROUP_RESULT_REPORT`）；callkit 路由与去重 ack；`setting_app_notification_permission` 校验+回声 ack；`awake_system_app` 探针；LBS 命令观察与 recover 响应消费；heads-up 接入与 `ext_clicked_button` 归因；`PACKAGE_ADD/REPLACED` 刷新链。
  - ignore-with-note（确认不对齐是决定而非遗漏）：`APP_NOTIFY_MSG` 接收时延遥测、`SDK_START_ACTIVITY_EVENT`/`SDK_LBS_PUSH_EVENT` 采集、`TRIGGER_PING`（无 HwKa 路径）、`SEND_TINYDATA`（隐私立场，ingress 拒收，维持）、FCM assemble-push 解密分支（本架构 token 从不上传、chid-5 不会被 FCM 包装；仅当为无自带 SDK 的托管应用引入 FCM 回退时才需要移植 `b0:49-52` 等价逻辑）。
  - 已知限制（均有代码 KDoc 锚点）：msg-id→已发通知 id 注册表（wc.b/wc.d 只能按 miss ack）；AppOps 通知权限写（需系统权限，如实回 errorCode 5）；`hc/` callkit 监视器（按无监听者形态回失败）；`provider.g` 应用档案缓存刷新；scenepush 模块（场景结果记录后丢弃、不伪 ack）；GroupBindCommand Room 账本（保留按包拉取效果）；服务器白名单/定时全量同步调度；`h/h.c` 点击统计环；加密 LBS 命令载荷（缺 `mipush_apps_scrt` 存储）。
  - 组件级对账：7.4→7.5 新增 push 相关 manifest 组件已全部核对——订阅通知 provider/dialog 归入订阅栈移植（provider 本体未建，频率配置走 SP），`UploadLogSDKService`+`LogUploadFileProvider` 按决定维持缺席，`BootAliveReceiver` stock 实现为空故跳过。
- 设备证据缺口：本审计轮全部构建仅本地测试通过；`1.0.3-20260913_003656` 双包（含 island 批次、ingress/pinned/订阅栈/两波控制面全部改动）待装机复验（京东实推、个税 wake-up/arrived、Android 14+ chid 广播、clear_push_message/订阅同步/callkit 实链路）。

## Code-only evidence (2026-09-01)

| 项目 | 结果 |
|---|---|
| `./gradlew check --warning-mode=all --console=plain` | 通过 |
| `./gradlew qualityGateKoverVerify --warning-mode=all --console=plain` | 通过（common/core/xposed/xmsf:shell = 10%/10%/10%/7%） |
| Kotlin compiler warnings | 0 |
| lint findings | 0 |
| vendor / xmsf:shell / manager:port 回归测试 | 通过 |
| 模块边界检查 | 通过 |
| device dump index | 87 raw / 86 unique / 0 errors |

当前工作树执行 `./gradlew check qualityGateKoverVerify :xmsf:assembleNormalDebug :mipush:assembleGithubDebug --warning-mode=all --console=plain`，1362 actionable tasks 通过。此前两份 APK 已传输到 MBP、校验并安装；以下设备证据只覆盖明确列出的 smoke 范围，不覆盖 2026-09-01 尚未安装的 policy refactor。

## Last build and device evidence

以下设备记录属于 2026-08-31 安装包；2026-09-01 的最新构建仅完成本地构建和测试，尚未安装到设备。

```text
xmsf/build/outputs/apk/normal/debug/universal_normal_xmsf_v1.0.1-20260831_221345_debug.apk
SHA-256: 22045fd002759817b9df0cd3a6d2e931b476813f9206def7dbf38509af8b23eb
MD5: e54b4e63dda990587fdfd13980a3164b

mipush/build/outputs/apk/github/debug/universal_MiPush_v1.0.1-20260831_221345_debug.apk
SHA-256: 8a0ba99c2a9e1fc8eb00d4ffa862b680fe135be90bf133634cde27247d0a0e30
MD5: 1a5f1a257edbd44280488da6b50634c4
```

- 两包经本地/MBP SHA-256 和 MD5 一致性校验后通过 `adb install -r` 安装，设备为 Xiaomi `pudding`、Android 17/API 37、user 0；未清数据。
- 冷启动后 XMSF、`:services`、manager、`ServiceBoxService`、`XMPushServiceCore` 和 `ManagerRuntimeService` 存活；manager Binder 连续返回 `Connected` connection snapshots，当前最终窗口无项目 crash/ANR。
- exported `XMPushService` facade 现在先用瞬时 `startForeground`/`stopForeground` 满足外部 `startForegroundService` 契约，再分发并停止自身；Android 17 上显式调用并等待超过 deadline 后没有 `ForegroundServiceDidNotStartInTimeException`。
- `Utils.getRegSecs()` 在目标 `mipush.xml` 不存在时先检查文件；重装后同一 event/debug 解码入口的 XMSF `ContextImpl` 警告由约 9038 行降为 0，最终窗口没有项目调用栈的 `ContextImpl` warning flood。
- BAFE 根因是当前 XMSF `POST_NOTIFICATIONS` runtime permission denied，自有临时通知被系统静默丢弃。修复后改用 XMSF 可见且含 PendingIntent 的 existing active notification donor，精确 BAFE 记录仍优先且 donor 不被取消；最终 Android 17 专用窗口首次 capture 成功（`source=ExistingNotification`），选中 donor 的精确身份记录保持 `1 -> 1`，此前独立窗口的 notification records 总数保持 `44 -> 44`。

## Historical build and device evidence

以下产物和设备窗口属于此前的验证记录，不代表当前工作树：

```text
xmsf/build/outputs/apk/normal/debug/universal_normal_xmsf_v1.0.1-20260829_193644_debug.apk
SHA-256: 45c8eb17a711ec31adc7351e273fdd9770cdf8f7385d1099891e93c575a71a22
```

该 APK 未传输、未安装，不构成当前设备证据。

## Historical device result

旧版设备窗口的有效结论：

- 普通通知发送、接收、`NotificationManagerEx`、`SystemNotificationManager` 和 `enqueue` 路径正常。
- 目标包 active records、channel、channel group 和 `opPkg=com.xiaomi.xmsf` 可见。
- 稳定 XMSF PID 窗口内未发现新的 XMSF crash/ANR。

该证据对应旧版安装包 `1.0.1-20260829_180821`，不能归因于当前本地 APK，也不能证明跨用户或跨 UID 行为。

## Open parity gates

### Device and cross-process

- 长期 background 存活、manifest/component 的外部调用面，以及重启/待机后的 long connection 恢复；当前只完成短窗口冷启动与已连接快照。
- heartbeat 学习、ping timeout、网络切换、OC 更新和 alarm 重注册。
- KeepAlive Binder/config、observer/polling、绑定转移、解绑、retry 和 anti-kill negative controls。
- real cross-UID SDK ingress、registration、dedup、control actions、payload validation 和 telemetry rejection。
- package data clear/removal、用户隔离和同包跨 profile cleanup。

### Notification and platform

- delegated/framework notification lifecycle、target identity、channel/permission/group/click preservation；BAFE active donor capture 已在当前 Android 17 权限拒绝条件下通过，但真实跨 UID background-activity launch 仍需单独场景验证。
- HyperIsland、Top、Sweet、VoIP、extension callback/fallback、focus/progress 和 self-update revival。
- provider/Binder caller authorization、cloud/account compatibility、XSpace 和 privileged platform helpers。
- AOSP/non-MIUI downgrade、Android 16+ progress/promoted ongoing behavior。

### Archive and external dependencies

- `SecurityCoreAdd.apk` 等外部平台 helper 的版本化证据仍缺失。
- `MiPushZygisk` native source/ref/NDK ABI 属于外部依赖；其 native registration 与 Android 17 兼容性仍需在独立 source/release gate 验证。
- XMSFKeeper 和其他 privileged helper 继续视为外部依赖，不在 XMSF 内伪造实现。

## Non-claims

源码测试、静态检查、APK 可构建、manifest 存在、短时无 crash 或单次 `dumpsys notification` 快照，均不能单独证明可见 UI、跨 UID 调用、跨用户隔离、KeepAlive 绑定、heartbeat 学习或完整 stock parity。
