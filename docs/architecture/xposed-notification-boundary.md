# Xposed Notification Boundary

This document records how the MiPushFramework notification code behaves when the companion Xposed
module is active. It replaces the old workspace-level review note.

## Runtime Ownership

When the Xposed module is active, notification identity and permission work is split across two
processes:

| Process | Owner | Responsibility |
|---|---|---|
| `com.xiaomi.xmsf` | `HookPushNC` | Sets hook flags and replaces the XMSF-side notification bridge methods. |
| `android` / system_server | `NmsPermissionHooker` | Resolves the delegated target package in the target user and permits the selected XMSF calls without rewriting the delegated operation package. |

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
4. A delegated post must keep `pkg=target package` and `opPkg=com.xiaomi.xmsf`. Only a true
   fallback path may use the old system-identity behavior; changing `opPkg` to the system package
   reaches different SystemUI icon/group branches and is not an equivalent authorization fix.
5. Configured `miui.focus.param`, locally generated island proxy, and shade visibility are three
   independent policies. Do not gate configured focus on the generated-focus preference, and do
   not use `showNotification=false` to suppress an otherwise eligible island proxy.
6. Register the private dispatcher even when an external HyperIsland implementation is present;
   that condition skips only the built-in rendering route. The receiver remains protected by the
   XMSF signature sender permission.
7. Keep hook signature checks and hook failure logs close to `HookPushNC`; silent partial hook
   failure makes the app process run fallback code with misleading state.
8. Removal tracking must hook the real `MiuiNotificationListener.onNotificationRemoved(...)`
   override and retain ownership per proxy notification ID. Removing an old source must not cancel
   a newer proxy that reused that ID.
9. Validate visible notification behavior with `dumpsys notification --noredact`, not only app logs.

## XSpace Identity Boundary

- The packaged static scope contains only hook-required platform/companion packages: system,
  SystemUI, SecurityCore, DocumentsUI, XMSF and explicitly supported system integrations. Ordinary
  target apps, including cloned or user-999 instances, are selected by the user in LSPosed rather
  than being hard-coded into `scope.list`.
- The SecurityCore package-info fallback only synthesizes the observed module result for the
  SecurityCore caller, required manifest-query flags, and query users `0` or `999`. It rejects all
  other users.
- Header large-icon correction is limited to a real XSpace fallback-identity mismatch. It must not
  overwrite correct delegated notification identity.
- `SecurityCoreAdd.apk` has not been re-captured in the curated archive. Its behavior is historical
  live-device evidence, not a reproducible raw-artifact claim.
- Static scope reachability is not full multi-user ownership. Current registration, event and UI
  models do not consistently carry `userId`; package-name-only state can therefore conflate owner
  and cloned instances until a dedicated identity migration lands.

## Verification

Useful checks for this boundary:

```bash
./gradlew :xposed:compileDebugKotlin
./gradlew :xposed:testDebugUnitTest
./gradlew :xmsf:testNormalDebugUnitTest
./gradlew :app:assembleNormalDebug
```

For device validation, also confirm hook installation logs and the actual posted notification state
with `adb shell dumpsys notification --noredact`.
