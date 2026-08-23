# Storage Ownership Matrix

> 创建日期：2026-08-22
> 对应文档：modernization_and_architecture_recommendations_refined.md § 阶段 2
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
| Event list cache | `EventListCacheStore` | manager | current | DataStore | DataStore edit | N/A | app data clear | No |

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
| `last_receive_time` | common Utils | xmsf | per-package | getSharedPreferences | SharedPreferences.Editor | Evaluate migration to Room | No (internal) |
| `KeepAlive` prefs | KeepAliveRuntimeAdapter | xmsf | current | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `SweetNotificationCoordinator` | notification | xmsf | N/A | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `PREF_MILEPOST_STATUS` | notification | xmsf | N/A | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `PushControllerUtils` prefs | xmsf control | xmsf | current | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `ModuleLogProvider` prefs | logging | xmsf | N/A | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `AnonymousInstallationId` | logging | xposed | current | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `WelcomeIslandNotifier` | manager | manager | current | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
| `LauncherIconController` | manager | manager | current | getSharedPreferences | SharedPreferences.Editor | Migrate to DataStore | No (internal) |
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

## 6. Migration Priorities

### Immediate（已完成迁移）
- `SweetNotificationCoordinator` → DataStore / Memory Cache（已完成迁移与 SP 兼容导入）
- `PREF_MILEPOST_STATUS` → DataStore / Memory Cache（已完成迁移）
- `WelcomeIslandNotifier` → DataStore / PreferenceRepository（已完成迁移）
- `LauncherIconController` → DataStore / Memory State（已完成）

### Medium（需兼容性验证）
- `KeepAliveRuntimeAdapter` → DataStore（需验证 Xposed 读取路径）
- `PushControllerUtils` prefs → DataStore（需验证 stock 行为）
- `last_receive_time` → Room（需验证跨包读取）

### Frozen（不可迁移）
- `mipush_profile_id` — stock key
- `pref_registered_pkg_names` — stock key
- `stock_surface` — stock key
- `LEGACY_SETTINGS_FILE_NAME` — 只读迁移状态
