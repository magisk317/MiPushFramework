# Vendor/XMSF Boundary Compliance Audit

Status: active — source audit refreshed 2026-08-11 at `d8e4cf2d0`; residual platform and policy
work remains.

## Layering Contract

```
vendor 层（传输/平台适配）
  ├── 检测系统事件（网络变化、屏幕状态、连接关闭…）
  ├── 执行 xmsf 返回的 plan（调度 job、启停 alarm…）
  └── 不做任何"是否执行"的判断

xmsf 层（运行时策略）
  ├── 通过 IPushRuntimeObserver 接收事件
  ├── 通过 resolveXxxPlan() 返回决策 plan
  └── 拥有所有"是否/何时/如何"的判断权
```

## Compliant Paths (already using plan pattern)

| Path | Vendor calls | xmsf decides |
|------|-------------|-------------|
| Connection attempt | `resolveConnectionAttemptPlan()` | skip / how to connect |
| Check alive | `resolveCheckAlivePlan()` | ping / scheduleConnect / disconnect |
| Connection closed (falldown+fault) | `planConnectionClosed()` | force reconnect in falldown |
| Reconnect scheduling | `resolveReconnectAttemptPlan()` | backoff strategy / skip |
| Blob inbound | `planInboundBlob()` | ping/close/challenge classification |
| Exact alarm | `AlarmManagerTimer` override | canScheduleExactAlarms policy |

## Resolved Since The Original Audit

The original inventory is not a current violation list. The observer-plan refactor now makes the
vendor lifecycle delegate notify the runtime observer for `connectionClosed`, `reconnectionFailed`,
and `reconnectionSuccessful`; the observer bridge evaluates the corresponding plans and executes
the resulting reconnect/alarm action. The old Category A entries and the old "dead xmsf plan"
claims are therefore closed at source level.

The relevant source anchors are:

- `vendor/.../XMPushServiceLifecycleDelegate.kt`
- `xmsf/.../MiPushRuntimeObserverBridge.kt`
- `vendor/.../PushRuntimeModels.kt`

These are still source-level results. A connected device is required to prove timing, alarm,
falldown, and network behavior.

## Residual Inventory

### Category A: Vendor-owned compatibility policy remains

| # | Path | File | Impact |
|---|------|------|--------|
| 1 | `networkChanged()` / screen / timer / power observers | `vendor/.../XMPushService*` | Observer plans exist for several paths, but target-ROM behavior is not device-proven |
| 2 | `shouldReconnect()` and `shouldFalldown()` | `vendor/.../XMPushServiceStateSupport.kt`, `XMPushServiceCore.kt` | Compatibility gates remain vendor-facing inputs to runtime decisions |
| 3 | Connecting and ping timeout constants | `vendor/.../Connection.kt`, `SocketConnection.kt` | Frozen transport timing remains vendor-owned |

### Category B: Intentionally retained vendor implementation

| # | Path | File | Impact |
|---|------|------|--------|
| 4 | Heartbeat strategy | `heartbeat/StableIntelligentHeartbeatStrategy.kt` | Stock-derived transport strategy; focused tests exist, device learning/network evidence remains open |
| 5 | Ping timeout disconnect | `SocketConnection.kt` | Stock transport behavior; reason-specific runtime policy must stay in the adapter |

### Category C: Hardcoded stock compatibility constants

| # | Constant | File | Value |
|---|----------|------|-------|
| 6 | `PING_TIMEOUT_MS` | `SocketConnection.kt` | 10s |
| 7 | `CONNECTING_TIMEOUT` | `XMPushServiceConnectionDelegate.kt` | 15s |
| 8 | `SHORT_INTERVAL_MS` | `StableIntelligentHeartbeatStrategy.kt` | 235s |
| 9 | `DEFAULT_LONG_INTERVAL_MS` | `StableIntelligentHeartbeatStrategy.kt` | 600s |

### Category D: Explicit adapter calls into vendor actions

| # | Path | File | Impact |
|---|------|------|--------|
| 10 | `service.shouldFalldown()` | `MiPushRuntimeObserverBridge.kt` | Runtime needs a vendor compatibility predicate while translating observer events |
| 11 | `service.scheduleConnect(...)` | `MiPushRuntimeObserverBridge.kt`, `PushRuntimeExecutionBridge.kt` | Action execution remains an adapter responsibility; do not spread this call into product policy |

## Cross-Module Consistency Issues

| # | Issue | Severity |
|---|-------|----------|
| 1 | Three independent dedup mechanisms (common/xmsf/vendor) | High |
| 2 | `APP_ID`/`APP_KEY` hardcoded as mutable `var` in common/Constants | High |
| 3 | `"com.xiaomi.xmsf"` duplicated across 6+ modules, 21+ literal usages | Medium |
| 4 | `PushVersionInfo` vendor wrapper is pure delegation facade | Low |
| 5 | `PushRuntimeComponents` xmsf wrapper is pure re-export | Low |
| 6 | 7 deprecated typealiases in configuration module | Low |

## Fix Roadmap

### Batch 1: Preserve the resolved ownership boundary
- Keep vendor lifecycle callbacks notification-only for observer-owned decisions.
- Add regression coverage when a new callback or direct scheduling path is introduced.
- Keep deduplication changes separate from lifecycle ownership changes.

### Batch 2: Architecture convergence
- Add device evidence for network, screen, timer, power-mode, and client-count transitions.
- Decide separately whether remaining vendor gates need observer plans; do not infer that from
  the presence of a source callback alone.

### Batch 3: Code quality
- Unify `"com.xiaomi.xmsf"` constant to single definition in `common/Constant.kt`
- Convert PushVersionInfo/PushRuntimeComponents wrappers to typealiases
- Clean up deprecated typealiases and dead code
