# Storage Ownership Matrix

> 创建日期：2026-08-22
> 更新日期：2026-08-31
> 对应文档：`docs/architecture/boundary-model.md`、`docs/architecture/current-runtime-call-flow.md`
> 用途：记录每个存储键/表的所有者、进程、用户、读写 API、迁移版本和清除条件

## 1. DataStore Preferences（产品设置）

| Key / Pref | Owner | Process | User | Read API | Write API | Migration | Clear Condition | Stock Contract |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `mipush_framework_settings` (全局) | `PreferenceRepository` | xmsf + manager | current | `dataStore.data` Flow | `dataStore.edit {}` | N/A | app data clear | No |
| Island settings (ISLAND_PREF_*) | `PreferenceRepository` | xmsf + manager | current | `readIslandSettingsSnapshot()` | setters in PreferenceRepository | N/A | app data clear | No |
| KeepAlive settings (KEEPALIVE_PREF_*) | `PreferenceRepository` | xmsf + manager | current | Flow exposure | setters in PreferenceRepository | N/A | app data clear | No |
| Debug mode | `PreferenceRepository` | xmsf | current | `isDebugMode` Flow | `setDebugMode()` | N/A | app data clear | No |
| Log sanitization | `PreferenceRepository` | xmsf | current | `isLogSanitizationEnabled` Flow | `setLogSanitizationEnabled()` | N/A | app data clear | No |
| Analytics | `PreferenceRepository` | xmsf | current | `isAnalyticsEnabled` Flow | `setAnalyticsEnabled()` | N/A | app data clear | No |
| Config directory (SAF) | `PreferenceRepository` | manager | current | `configDirectory` Flow | setters | N/A | app data clear | No |
| Config sync state | `ConfigSyncRepository` | xmsf | current | DataStore | DataStore edit | N/A | app data clear | No |
| Application list cache | `ApplicationListCacheStore` | manager | current | DataStore | DataStore edit | N/A | app data clear | No |
| Event list cache | `EventListCacheStore` | manager | current user-scoped key | DataStore cache-first read | DataStore edit/merge | N/A | app data clear | No |

事件列表缓存是 manager 侧的显示镜像，不是 XMSF `EVENT` 表的第二个事实来源。页面打开时
先恢复缓存；XMSF maintenance/background refresh 读取 runtime 第一页后与原始缓存合并；显式
刷新仍通过 `RemoteEventListSource` 查询 runtime。成功但为空的 runtime 页和临时 unavailable
读取不会自动删除已有缓存，因此 UI 可能继续显示较早的历史事件，而同一时刻的
`getEventPage items=0` 只代表本次 live query 没有返回行。

## 2. Room Database（运行时存储）

| Table | Owner | Process | User | Read API | Write API | Schema Version | Clear Condition | Stock Contract |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| EVENT | `EventRepository` | xmsf | (user_id, pkg) | EventDao queries | EventDao.insert | v9 | retention policy (7 days) | No |
| DELETED_EVENT | `EventRepository` | xmsf | (user_id, pkg) | DeletedEventDao queries | DeletedEventDao.insert | v9 | retention policy (bounded) | No |
| REGISTERED_APPLICATION | `RegisteredApplicationDb` | xmsf | (user_id, pkg) | RegisteredApplicationDao | RegisteredApplicationDao | v9 | package uninstall | Partial (stock registration) |

## 3. SharedPreferences（vendor / 兼容）

| File | Owner | Process | User | Read API | Write API | Migration Path | Stock Contract |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `mipush_profile_id` | stock SDK | xmsf | per-package | getSharedPreferences | SharedPreferences.Editor | Cannot migrate (stock key) | **Yes** |
| `pref_registered_pkg_names` | stock SDK | xmsf | N/A | getSharedPreferences | SharedPreferences.Editor | Cannot migrate (stock key) | **Yes** |
| `stock_surface` | stock SDK | xmsf | N/A | getSharedPreferences | SharedPreferences.Editor | Cannot migrate (stock key) | **Yes** |
| `last_receive_time` | common Utils | xmsf | per-package | getSharedPreferences | SharedPreferences.Editor | Retain until a Room schema/reader migration is proven | No (internal) |
| `stock_keepalive_runtime` | KeepAliveRuntimeAdapter | xmsf | current | getSharedPreferences | SharedPreferences.Editor | Retain: subprocess/strategy compatibility state | No (internal) |
| `SweetNotificationCoordinator` state | notification | xmsf | current package/user key | getSharedPreferences + memory mirror | SharedPreferences.Editor | Retain: stock-compatible lifecycle key format | No (internal) |
| `PREF_MILEPOST_STATUS` | notification | xmsf | current package/user key | getSharedPreferences + memory mirror | SharedPreferences.Editor | Retain with Sweet state; no unproven rewrite | No (internal) |
| `PushControllerUtils` prefs | xmsf control | xmsf | current | getSharedPreferences | SharedPreferences.Editor | Retain: legacy control keys and explicit opt-in | No (internal) |
| `push_message_ids` | `PushRuntimeDuplicateStore` | xmsf runtime | `(user_id, pkg)` | SharedPreferences legacy facade | SharedPreferences.Editor | Retain only for compatibility; not the active inbound delivery gate | No (internal) |
| `ModuleLogProvider` prefs | logging | xmsf | current | getSharedPreferences | SharedPreferences.Editor | Retain: provider compatibility state | No (internal) |
| `AnonymousInstallationId` | logging | xposed | current | getSharedPreferences | SharedPreferences.Editor | Retain: hook-process identity boundary | No (internal) |
| `WelcomeIslandNotifier` | manager | manager | current | PreferenceRepository/DataStore | DataStore edit | Completed | No (internal) |
| `LauncherIconController` | manager | manager | current | PreferenceRepository/DataStore + memory | DataStore edit | Completed | No (internal) |
| `LEGACY_SETTINGS_FILE_NAME` | MiCloudSettings | xmsf | current | getSharedPreferences | SharedPreferences.Editor | Read-only migration | **Yes** (read) |

## 4. ContentProvider（跨进程边界）

| Provider | Owner | Process | User | Consumer | Permission | Stock Contract |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `IslandPreferenceProvider` | xmsf | xmsf | current | SystemUI, Xposed | ISLAND_PREF_READ_PERMISSION | No (internal contract) |
| `KeepAlivePreferenceProvider` | xmsf | xmsf | current | Xposed KeepAliveHook | KEEPALIVE_PREF_READ_PERMISSION | No (internal contract) |
| `PushProfileIdProvider` | xmsf | xmsf | per-package | External SDK | signature | **Yes** |
| `ChannelProvider` | xmsf | xmsf | N/A | External | signature | **Yes** |
| `PushCommonProvider` | xmsf | xmsf | N/A | External SDK | signature | **Yes** |
| `PushSupportProvider` | xmsf | xmsf | N/A | External SDK | signature | **Yes** |
| `IconPackProvider` | xmsf | xmsf | N/A | External | signature | **Yes** |
| `PushControlProvider` | xmsf | xmsf | N/A | External | signature | **Yes** |
| `ModuleLogProvider` | xmsf | xmsf | N/A | Internal | signature | No |
| `MiCloudSettingsProvider` | xmsf | xmsf | current | Internal | signature | **Yes** (read legacy) |
| `BaseXposedLogProvider` | xposed | xposed | N/A | Internal | signature | No |
| `BillingProvider` | manager | manager | N/A | Google Play | signature | **Yes** |

## 5. In-Memory Cache / Flow

| Cache | Owner | Scope | Invalidation Trigger | Thread Safety |
| :--- | :--- | :--- | :--- | :--- |
| `IslandOptionsSnapshotReader` | notification | xmsf app process | DataStore Flow emission | AtomicReference |
| `EventListCacheStore` | manager | manager process | DataStore Flow emission | DataStore transactional |
| `ApplicationListCacheStore` | manager | manager process | DataStore Flow emission | DataStore transactional |
| `ConnectionSnapshotSources` | manager | manager process | Binder callback | Mutex |
| `StockMiPushPayloadDeduper` | xmsf runtime | `(user_id, package, payload_digest)` | package data clear / process reset / 60s stock window | synchronized map |
| `AndroidPushRuntimeWindowSupport` | xmsf runtime | `(user_id, package, message/action)` | runtime state reset / bounded TTL window | runtime state lock |
| `DuplicateMessagePolicy` | core via xmsf push hook | `(user_id, package, message_id)` | class-loader/process reset / 60s policy window | policy lock |
| `RegistrationRecordDeduper` | xmsf runtime | `(user_id, package)` | package data clear / process reset / 30s record window | concurrent map |

## 6. Migration Status

### Completed
- `SweetNotificationCoordinator` → DataStore / Memory Cache（已完成迁移与 SP 兼容导入）
- `PREF_MILEPOST_STATUS` → DataStore / Memory Cache（已完成迁移）
- `WelcomeIslandNotifier` → DataStore / PreferenceRepository（已完成迁移）
- `LauncherIconController` → DataStore / Memory State（已完成）

### Intentionally retained
- `stock_keepalive_runtime` — ServiceBox/strategy subprocess state and Xposed-compatible runtime reads
- `SweetNotificationCoordinator` / `PREF_MILEPOST_STATUS` — stock-compatible package/user key lifecycle state
- `PushControllerUtils` prefs — legacy control keys and explicit framework-registration opt-in
- `last_receive_time` — legacy reader remains in `common`; a Room migration needs a separate schema and cross-package read proof

### Frozen（不可迁移）
- `mipush_profile_id` — stock key
- `pref_registered_pkg_names` — stock key
- `stock_surface` — stock key
- `LEGACY_SETTINGS_FILE_NAME` — 只读迁移状态
