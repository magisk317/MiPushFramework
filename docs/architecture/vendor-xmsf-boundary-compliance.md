# Vendor–XMSF Boundary Compliance

## Final status

源码边界合规。`vendor` 保持冻结的兼容/runtime 层，`:core` 负责可复用的中立策略，`xmsf`
负责运行时组合和副作用；代码、
边界检查和构建检查已通过，仍有必须在真机上触发的行为门禁。

## Ownership result

- `vendor` 负责系统事件采集、传输实现和 plan 执行。
- `:core` 负责可复用的连接、重连、断线、入站 blob 和精确 alarm 决策；`xmsf` 通过 observer
  组合这些 plan 并执行 Android/runtime 副作用。
- 上述路径均已通过 observer/plan 边界；vendor 不再直接持有产品策略。
- `vendor` 内保留 stock transport、heartbeat、timeout 和兼容谓词，属于冻结实现，不等同于新增产品策略。

## Notification boundary result

- `NotificationManagerPlatformSupport` 提供显式的
  `getActiveNotifications(packageName, userId)` 接口。
- 负 `userId` fail-closed；无效列表元素不会进入产品层结果。
- 无用户参数的旧接口继续保留，仅用于 stock/runtime 兼容，并委托当前空间。
- `NotificationVendorAdapter` 直接向 vendor 传递目标 `userId`，不会先读取隐式当前用户再过滤。
- active notification、channel、group、local fallback 和 delegated identity 均保持目标包/用户隔离。
- 通知反射和 ROM 兼容异常处理有明确失败日志、fallback 和安全异常传播；没有使用 detekt 文件级抑制掩盖问题。

## Duplicate ownership

去重入口按调用时机分层，不能把不同层的窗口合并成一个全局缓存：

- `StockMiPushPayloadDeduper` 位于 `:xmsf:runtime`，由 `MiPushFacadeService` 在外部 SDK intent 转发前调用。它只处理 `SEND_MESSAGE`/`UNREGISTER_APP` 的完整 payload digest，并保持 stock 的过期命中顺序。
- `AndroidPushRuntimeWindowSupport` 位于 `:xmsf:runtime`，由 `PushRuntime.observeInboundMessage` 调用。它只记录 runtime 入站观测、user/package/message identity 和 action burst 窗口，同时更新诊断计数。
- `DuplicateMessagePolicy` 位于 `:core`，由 `xmsf/push` 的 `ExplicitHookBridge` 在 hook duplicate 回调中调用，按 user/package/message ID 处理实际 hook 点的重复消息。
- `com.xiaomi.mipush.sdk.PushMessageProcessor` 保留 stock SDK 的 25-ID app-facing cache；`RegistrationRecordDeduper` 只合并本地注册历史，不能阻断注册 intent 到 core。
- `PushRuntimeDuplicateStore` 和 `MiPushMessageDuplicate` 仅保留兼容 facade/adapter；前者不再是当前入站 delivery gate，后者不拥有独立 cache。

## Retained compatibility

以下内容是有意保留的冻结兼容面：

- `PING_TIMEOUT_MS = 10s`
- `CONNECTING_TIMEOUT = 15s`
- stable heartbeat 的 `235s` 短间隔和 `600s` 默认长间隔
- `shouldReconnect`、`shouldFalldown` 等 vendor 兼容谓词
- observer adapter 对 vendor 连接动作的最小调用

现有 `io.github.magisk317.*` product imports 已记录在
`scripts/vendor_boundary_baseline.txt`；本次没有新增边界债务。

## Evidence

- shell Kotlin 编译通过。
- `./gradlew check --warning-mode=all --console=plain` 通过，未产生 Kotlin 编译警告或 lint findings。
- 受影响模块的 vendor、shell、manager:port 测试通过。
- `verifyModuleBoundaries` 通过。
- `device_dumps` 索引覆盖率为 `87 raw / 86 unique / 0 errors`。

## Open gates

- 真机 heartbeat 学习、网络切换、timeout 和 alarm 重注册。
- 真机 KeepAlive observer/polling、绑定、转移和解绑。
- 真机跨 UID/跨用户 active notification、delegated lifecycle、provider、XSpace、self-update 和 SDK ingress。
- 兼容 wrapper/typealias 保留在明确的 ABI 适配边界，不是可删除的死代码。`StockMiPushPayloadDeduper`、
  `AndroidPushRuntimeWindowSupport`、`DuplicateMessagePolicy`、stock SDK 的 25-ID cache 和注册历史
  coalescer 各自服务不同调用时机；后续变更必须先证明范围重叠。
