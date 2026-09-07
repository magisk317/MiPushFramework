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
