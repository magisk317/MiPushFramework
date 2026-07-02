# Connection Status Page — Implementation Plan

## Background

Manager UI 需要一个**连接状态详情页**，展示 XMPP 连接的运行时信息。目前 `PushRuntime.snapshot()` 已经
追踪了 `connectionState`（Idle/Connecting/Connected/Disconnected），但缺少以下关键数据：
- 连接 IP 地址
- 连接建立时间 (`connectedAtMs`)
- 上次断线时间 (`lastDisconnectedAtMs`)
- 当前会话持续时间
- 心跳配置（keepAlive / ping interval）
- 下行消息统计

## Architecture Summary

```
┌──────────────────────────────────────────────────────────┐
│  Manager UI (Compose)                                     │
│  ┌────────────────────────────────────────────────────┐  │
│  │ ConnectionStatusPage                                │  │
│  │  ← ConnectionStatusViewModel                       │  │
│  │     ← ManagerRuntimeActions.getConnectionSnapshot() │  │
│  └────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│  Common Module (interface contract)                       │
│  ┌────────────────────────────────────────────────────┐  │
│  │ ManagerConnectionSnapshot (data class)              │  │
│  │ ManagerRuntimeActions + getConnectionSnapshot()     │  │
│  └────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│  XMSF Module (runtime implementation)                    │
│  ┌────────────────────────────────────────────────────┐  │
│  │ AndroidPushRuntime                                  │  │
│  │  - connectionRecord (already tracks state/host)     │  │
│  │  + connectedAtMs, lastDisconnectedAtMs,             │  │
│  │    connectionSessionCount, resolvedIp               │  │
│  │                                                     │  │
│  │ XmsfManagerRuntimeActions.getConnectionSnapshot()   │  │
│  │  → reads PushRuntime fields + SmackConfiguration    │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

Manager UI 编译到 `com.xiaomi.xmsf` 进程中，直接调用 `PushRuntime.snapshot()` 无需 IPC。

## Implementation Tasks

### Task 1: Extend runtime tracking (core + xmsf)

1. **`PushConnectionRecord`** — 在 `core/…/PushRuntimeContract.kt` 中新增字段不可行（太多依赖），
   改为在 `AndroidPushRuntime` 增加私有字段追踪连接时间。

2. **`AndroidPushRuntime`** — 新增私有字段：
   - `connectedAtMs: Long = 0` — Connected 状态进入时记录
   - `lastDisconnectedAtMs: Long = 0` — Disconnected 状态进入时记录
   - `connectionSessionCount: Long = 0` — 每次进入 Connected 递增
   
3. **`observeConnectionState()`** — 在状态转换时更新时间戳：
   - `→ Connected`: `connectedAtMs = nowMs`, `connectionSessionCount++`
   - `→ Disconnected`: `lastDisconnectedAtMs = nowMs`

4. **新增 `connectionSnapshot()` 方法** 返回连接详情供 Manager 使用。

### Task 2: Define shared contract (common module)

在 `common/.../manager/ManagerRuntimeActions.kt` 中：

1. 新增 `ManagerConnectionSnapshot` data class：
   ```kotlin
   data class ManagerConnectionSnapshot(
       val connectionState: String,
       val connectedAtMs: Long,
       val lastDisconnectedAtMs: Long,
       val connectionSessionCount: Long,
       val serverHost: String?,
       val serverIp: String?,
       val keepAliveIntervalMs: Int,
       val pingIntervalMs: Int,
       val downstreamMessageCount: Long,
       val deliveredToAppCount: Long,
       val duplicateMessageCount: Long,
       val ackMessageCount: Long,
       val registeredPackageCount: Int,
       val trackedChannelCount: Int,
       val boundChannelCount: Int,
   )
   ```

2. 在 `ManagerRuntimeActions` 接口新增方法：
   ```kotlin
   fun getConnectionSnapshot(): ManagerConnectionSnapshot
   ```

### Task 3: Implement in xmsf adapter

在 `xmsf/.../di/ManagerRuntimeAdapters.kt` 的 `XmsfManagerRuntimeActions` 中实现
`getConnectionSnapshot()`，聚合：
- `AndroidPushRuntime` 连接时间字段
- `PushRuntime.snapshot()` 消息计数
- `SmackConfiguration.keepAliveInterval` / `pingInterval`
- `ConnectionConfiguration.getXmppServerHost()`

### Task 4: SettingsManager 透传

在 `manager/.../SettingsManager.kt` 新增：
```kotlin
fun getConnectionSnapshot(): ManagerConnectionSnapshot {
    return runtimeActions.getConnectionSnapshot()
}
```

### Task 5: ViewModel

新建 `ConnectionStatusViewModel`：
- 暴露 `StateFlow<ManagerConnectionSnapshot?>`
- 提供 `refresh()` 方法，从 IO 线程拉取快照
- 自动定时刷新（5秒间隔，用于更新 session duration 显示）

### Task 6: Compose UI Page

新建 `ConnectionStatusPage` Composable：
- 连接状态指示灯（绿=Connected, 黄=Connecting, 红=Disconnected, 灰=Idle）
- 服务器信息（host + IP）
- 时间信息：连接时间、会话持续时长、上次断线时间
- 心跳配置
- 消息统计卡片
- 下拉刷新

### Task 7: Navigation 注册

在 `AppNavHost` 中注册新路由，从 Settings 页面或 Overview 页面提供入口。

## File Changes Summary

| Module | File | Change |
|--------|------|--------|
| core | `PushRuntimeContract.kt` | 无修改 |
| xmsf | `AndroidPushRuntime.kt` | +连接时间字段, +connectionSnapshot() |
| common | `ManagerRuntimeActions.kt` | +ManagerConnectionSnapshot, +getConnectionSnapshot() |
| xmsf | `ManagerRuntimeAdapters.kt` | impl getConnectionSnapshot() |
| manager | `SettingsManager.kt` | +getConnectionSnapshot() 透传 |
| manager | `ConnectionStatusViewModel.kt` | 新建 |
| manager | `ConnectionStatusPage.kt` | 新建 |
| manager | `AppNavHost.kt` | +注册路由 |
| manager | `SettingsPage.kt` | +入口 item |

## Priority / Risk

- 所有改动在 manager 进程内，无 IPC 风险
- `AndroidPushRuntime` 加字段是线程安全的（already synchronized on `lock`）
- UI 层纯新增页面，不影响现有功能
