# Connection Status Page - Current Implementation

## Purpose

The manager UI has a connection status page that displays the current XMPP runtime state without
adding IPC. The manager module is packaged into the `com.xiaomi.xmsf` process, so its ViewModel can
read the in-process manager runtime contract through `SettingsManager`.

## Architecture

```text
ConnectionStatusPage
  -> ConnectionStatusViewModel
  -> SettingsManager.getConnectionSnapshot()
  -> ManagerRuntimeActions.getConnectionSnapshot()
  -> XmsfManagerRuntimeActions
  -> RuntimeSettingsAdapter.getConnectionSnapshot()
  -> PushRuntime.connectionSnapshot()
```

The shared contract lives in `common`:

- `ManagerConnectionSnapshot`
- `ManagerRuntimeActions.getConnectionSnapshot()`

The xmsf implementation delegates from `XmsfManagerRuntimeActions` into `RuntimeSettingsAdapter`,
which aggregates:

- connection state, timestamps, counters, and server information from
  `PushRuntime.connectionSnapshot()`
- heartbeat values from the Xiaomi runtime configuration
- the configured XMPP host from the xmsf runtime configuration path

## Runtime State

`AndroidPushRuntime` owns the connection counters and timestamp state behind its existing
`lock`. It records:

- `connectedAtMs`
- `lastDisconnectedAtMs`
- `connectionSessionCount`
- resolved server IP when available
- downstream, delivery, duplicate, ack, registration, and channel counters

`PushRuntime` remains the public facade for runtime callers. The connection status adapter now
asks `RuntimeSettingsAdapter` for the manager snapshot; that adapter reads the facade snapshot and
adds Android-coupled live diagnostics such as heartbeat intervals and current socket IP fallback.

## UI Surface

The page is implemented under the manager module:

- `ConnectionStatusViewModel` exposes `StateFlow<ManagerConnectionSnapshot?>`, refreshes on demand,
  and polls periodically so session duration remains current.
- `ConnectionStatusPage` renders connection state, server host/IP, timing, heartbeat, message
  statistics, and channel counters.
- `AppNavHost` registers the connection-status route, and manager overview/settings entrypoints can
  navigate to it.

## Files

| Module | File | Role |
|--------|------|------|
| common | `common/.../manager/ManagerRuntimeActions.kt` | Shared snapshot data class and runtime action contract |
| xmsf | `xmsf/.../runtime/android/AndroidPushRuntime.kt` | Connection-state tracking and `connectionSnapshot()` |
| xmsf | `xmsf/.../service/runtime/RuntimeSettingsAdapter.kt` | Runtime-side assembly for manager connection snapshots |
| xmsf | `xmsf/.../app/di/ManagerRuntimeAdapters.kt` | Manager gateway delegate to the runtime adapter |
| manager | `manager/.../SettingsManager.kt` | Manager-facing pass-through |
| manager | `manager/.../ConnectionStatusViewModel.kt` | Snapshot loading and periodic refresh |
| manager | `manager/.../ConnectionStatusPage.kt` | Compose page |
| manager | `manager/.../feature/navigation/AppNavHost.kt` | Route registration |

## Verification

Relevant checks:

```bash
./gradlew :manager:compileDebugKotlin
./gradlew :xmsf:testNormalDebugUnitTest
./gradlew verifyModuleBoundaries
```

For device validation, compare the page state with runtime logs and, when notification behavior is
part of the investigation, `adb shell dumpsys notification --noredact`.
