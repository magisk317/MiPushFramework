# Xposed Notification Boundary

This document records how the MiPushFramework notification code behaves when the companion Xposed
module is active. It replaces the old workspace-level review note.

## Runtime Ownership

When the Xposed module is active, notification identity and permission work is split across two
processes:

| Process | Owner | Responsibility |
|---|---|---|
| `com.xiaomi.xmsf` | `HookPushNC` | Sets hook flags and replaces the XMSF-side notification bridge methods. |
| `android` / system_server | `NmsPermissionHooker` | Runs selected notification-manager calls under cleared identity and permits XMSF calls that would otherwise fail. |

The practical result is that many `NotificationManagerEx` and `NotificationIdentityBridge` methods
inside the app process are fallback code while the hook is installed.

## Covered Paths

Treat edits to these methods as fallback-only unless runtime evidence proves the hook is absent or
disabled:

- `NotificationIdentityBridge.resolveStrategy(...)`
- `NotificationIdentityBridge.canNotifyAsPackage(...)`
- `NotificationIdentityBridge.notifyAsTargetPackage(...)`
- channel and group operations on `NotificationIdentityBridge`
- `NotificationManagerEx.notify(...)`
- `NotificationManagerEx.areNotificationsEnabled(...)`
- `NotificationManagerEx.shouldNotifyAsPackage(...)`
- channel and group operations on `NotificationManagerEx`

Non-covered support code can still matter, including diagnostics, configuration, package-context
selection, and app/runtime logging.

## Rules

1. Before changing notification identity or publish code, check whether `HookPushNC` fully replaces
   the target method.
2. If the method is hook-covered, document whether the change is for fallback behavior, diagnostics,
   or no-Xposed operation.
3. Do not rely on optimistic `DELEGATED` notification identity without system-side permission
   support. Without the system_server hook, `notifyAsPackage`-style calls can fail.
4. Keep hook signature checks and hook failure logs close to `HookPushNC`; silent partial hook
   failure makes the app process run fallback code with misleading state.
5. Validate visible notification behavior with `dumpsys notification --noredact`, not only app logs.

## Verification

Useful checks for this boundary:

```bash
./gradlew :xposed:compileDebugKotlin
./gradlew :xmsf:testNormalDebugUnitTest
./gradlew :app:assembleNormalDebug
```

For device validation, also confirm hook installation logs and the actual posted notification state
with `adb shell dumpsys notification --noredact`.
