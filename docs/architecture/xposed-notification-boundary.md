# Xposed Notification Boundary

This document records how the MiPushFramework notification code behaves when the companion Xposed
module is active. It replaces the old workspace-level review note.

## Runtime Ownership

When the Xposed module is active, notification identity and permission work is split across three
runtime surfaces. The Xposed APK is only the adaptation-plane carrier; it must not own the privileged
notification implementation.

| Process | Owner | Responsibility |
|---|---|---|
| `com.xiaomi.xmsf` | `NotificationHookBackend` / `NotificationHookBridge` | Owns NMS hidden-API access, icon mutation, channel/group operations, root fallback, active-notification reads, and notification owner decisions. |
| `com.xiaomi.xmsf` | `HookPushNC` (loaded from the Xposed APK) | Installs hooks and reflectively dispatches to `NotificationHookBridge` through the target classloader. It contains no notification backend implementation. |
| `android` / system_server | `NmsPermissionHooker` | Resolves the delegated target package in the target user and permits the selected XMSF calls without rewriting the delegated operation package. |

The target-side bridge has its own `HOOK_API_VERSION = 1`. The existing `NotificationManagerEx` and
`NotificationIdentityBridge` hook contract remains at version `2`; these versions are checked
independently so a stale manager/Xposed APK cannot silently call a mismatched runtime backend.

`hiddenapibypass` is intentionally a dependency of `:xmsf:shell`, not `:xposed`. Therefore the
unpublished `com.xiaomi.xmsf` runtime contains the privileged implementation while the Play manager
AAB does not contain the backend or the HiddenApiBypass class.

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
   a newer proxy that reused that ID; the proxy ID must include the notification user as well as the
   source package so owner and cloned users cannot reuse one lifecycle slot.
9. Validate visible notification behavior with `dumpsys notification --noredact`, not only app logs.
10. Package-scoped island policy is asynchronous in SystemUI. Until that package snapshot is
    loaded, `IslandPreferences.current(packageName)` must fail closed for both focus injection and
    visual rendering; inheriting the global visual switch can leak a package opt-out on its first
    notification.
11. A known broken-click launcher fallback must resolve and create its `PendingIntent` for the
    notification user. If a user context cannot be obtained, preserve the original click route
    rather than creating a user-0 launcher intent.
12. Centralized XMSF message deduplication must retain the target package in its key. The stock
    cache is app-scoped; a bare global `messageId` key can discard valid messages when different
    applications reuse an ID. User identity must also remain part of the boundary when one
    process can observe more than one Android user.
13. Island proxy post deduplication must include user and tag identity even when the platform
    status-bar key is blank; a package/id-only fallback can suppress a cloned-user notification.
14. SDK click intent URIs must reject explicit components outside the target package, including
    selector components; `Intent.setPackage` alone does not constrain an explicit component.
15. Legacy XMSF direct island broadcasts must carry the target package user ID and use that same
    user when deriving proxy IDs; missing user identity is not equivalent to primary user `0`.
16. External MiPush ingress authentication is transport-specific: Messenger requests must bind the
    declared target package to `Message.sendingUid`, while legacy `startService` requests cannot
    recover a caller UID from `onStartCommand` and therefore remain payload-gated. Do not describe
    the legacy route as caller-authenticated; its package-scoped control actions remain a residual
    risk until a compatible authenticated transport exists.

## XSpace Identity Boundary

- The packaged static scope contains only hook-required platform/companion packages: system,
  SystemUI, SecurityCore, DocumentsUI, XMSF and explicitly supported system integrations. Ordinary
  target apps, including cloned or user-999 instances, are selected by the user in LSPosed rather
  than being hard-coded into `scope.list`.
- The SecurityCore package-info fallback only synthesizes the observed module result for the
  SecurityCore caller, required manifest-query flags, and query users `0` or `999`. It rejects all
  other users.
- Header large-icon correction is limited to a real XSpace fallback-identity mismatch. It must not
  overwrite correct delegated notification identity. When resolving the target app icon, the hook
  uses the notification's user context; if that context cannot be created for a non-primary user,
  it fails closed instead of looking up a primary-user icon.
- `SecurityCoreAdd.apk` has not been re-captured in the curated archive. Its behavior is historical
  live-device evidence, not a reproducible raw-artifact claim.
- `MiPushZygisk` native hook remains an external source/ref/NDK ABI dependency. Native registration
  or ABI compatibility must be validated in that source and release pipeline; Xposed/Kotlin source
  inspection cannot establish it.
- Static scope reachability is not full multi-user device proof. Runtime registration, event,
  notification, and manager contracts now carry an explicit `userId`; package-scoped caches and
  provider handoffs validate that identity before reading or writing. Island package policy also
  receives the SystemUI notification user ID and keys its package cache by `(userId, packageName)`.
  Cross-profile registration, cleanup, and delegated-notification behavior still require live
  device evidence and are not established by static scope inspection alone.

## Verification

Useful checks for this boundary:

```bash
./gradlew :xposed:compileDebugKotlin
./gradlew :xposed:testDebugUnitTest
./gradlew :xmsf:shell:testNormalDebugUnitTest
./gradlew :xmsf:assembleNormalDebug
```

For device validation, also confirm hook installation logs and the actual posted notification state
with `adb shell dumpsys notification --noredact`.
