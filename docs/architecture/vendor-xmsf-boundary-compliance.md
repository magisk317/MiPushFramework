# Vendor/XMSF Boundary Compliance Audit

Status: active — audit complete, phased fixes in progress.

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

## Violation Inventory

### Category A: Vendor decides after notifying observer (dual decision)

| # | Path | File | Risk |
|---|------|------|------|
| 1 | `connectionClosed` | `XMPushServiceLifecycleDelegate.kt:151-158` | Vendor + bridge both schedule reconnect |
| 2 | `reconnectionFailed` | `XMPushServiceLifecycleDelegate.kt:168-176` | Same pattern |

### Category B: Vendor has full policy, no observer callback (policy not delegated)

| # | Path | File | Impact |
|---|------|------|--------|
| 3 | `networkChanged()` | `XMPushServiceStockLifecycle.kt:71-106` | xmsf cannot influence network transition behavior |
| 4 | `handleScreenState()` | `XMPushServiceIntentDelegate.kt:206-222` | xmsf cannot influence screen-on reconnect |
| 5 | `handleTimer()` | `XMPushServiceIntentDelegate.kt:224-238` | xmsf cannot influence timer-triggered reconnect |
| 6 | `installPowerModeObservers` | `XMPushServiceLifecycleInfrastructure.kt:57-100` | xmsf cannot influence power-mode disconnect |
| 7 | `configureClientChangeListener` | `XMPushServiceStockLifecycle.kt:59-69` | xmsf cannot influence zero-client disconnect |
| 8 | `reconnectionSuccessful` | `XMPushServiceLifecycleDelegate.kt:178-191` | xmsf cannot influence alarm/reactivation |

### Category C: Policy locked inside vendor (no observer interface)

| # | Path | File | Impact |
|---|------|------|--------|
| 9 | `shouldReconnect()` | `XMPushServiceStateSupport.kt:23-29` | xmsf completely cannot override reconnect gate |
| 10 | Heartbeat strategy | `heartbeat/StableIntelligentHeartbeatStrategy.kt` | xmsf cannot adjust interval/learning/timeout |
| 11 | Ping timeout disconnect | `SocketConnection.kt:136-161` | xmsf cannot decide timeout/retry policy |

### Category D: Hardcoded policy constants in vendor

| # | Constant | File | Value |
|---|----------|------|-------|
| 12 | `PING_TIMEOUT_MS` | `SocketConnection.kt` | 10s |
| 13 | `CONNECTING_TIMEOUT` | `XMPushServiceConnectionDelegate.kt` | 15s |
| 14 | `SHORT_INTERVAL_MS` | `StableIntelligentHeartbeatStrategy.kt` | 235s |
| 15 | `DEFAULT_LONG_INTERVAL_MS` | `StableIntelligentHeartbeatStrategy.kt` | 600s |

### Category E: xmsf reaches into vendor internals

| # | Path | File | Impact |
|---|------|------|--------|
| 16 | `service.shouldFalldown()` | `MiPushRuntimeObserverBridge.kt:277` | xmsf calls vendor policy method |
| 17 | `service.scheduleConnect(true)` | `PushRuntimeExecutionBridge.kt:299` | xmsf calls vendor method directly |

### Dead xmsf plan methods (defined but not called by vendor)

| Plan method | Status |
|------------|--------|
| `planReconnectionFailure()` | Not called by vendor's `reconnectionFailed` |
| `planReconnectionSuccess()` | Not called by vendor's `reconnectionSuccessful` |
| `planConnectionClosed()` | Only partially used (falldown+fault case) |

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

### Batch 1: Eliminate competition / risk
- Remove dual decisions in `connectionClosed`/`reconnectionFailed` (vendor only notifies, bridge owns all decisions)
- Unify dedup mechanism

### Batch 2: Architecture convergence
- Delegate `shouldReconnect()` to xmsf via observer plan
- Connect `networkChanged`/`handleScreenState`/`handleTimer`/`powerModeObservers`/`clientChangeListener`/`reconnectionSuccessful` to observer plans
- Activate dead plan methods

### Batch 3: Code quality
- Unify `"com.xiaomi.xmsf"` constant to single definition in `common/Constant.kt`
- Convert PushVersionInfo/PushRuntimeComponents wrappers to typealiases
- Clean up deprecated typealiases and dead code
