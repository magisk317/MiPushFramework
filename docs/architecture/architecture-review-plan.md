# MiPushFramework Architecture Review Plan

Status: active architecture record. Last source cross-check: 2026-08-11 at `d8e4cf2d0`.

The former workspace-level implementation plan was consolidated here. This document records
architecture state and priorities; the long-running stock/device probe list lives in
[stock-parity-verification.md](stock-parity-verification.md).

Status: active review. This document is the working checklist for the runtime-only dual-APK
architecture. It does not authorize removing existing notification, island, HyperIsland, proxy, or
click-routing behavior.

## Invariants

- `:app` packages the installable `com.xiaomi.xmsf` runtime.
- `:mipush` owns manager UI and Xposed entrypoints.
- Manager runtime reads and writes cross the signed Binder contract.
- A failed remote read stays unavailable; it must not become an empty successful snapshot.
- Runtime notification behavior, including MiPush island payloads and HyperIsland compatibility,
  remains behaviorally preserved unless a separate, explicitly verified bug fix changes it.

## Review Tracks

### Completed or guarded

- Notification dump reads use the shared `--noredact`-first contract with a usable-output fallback.
- Notification channels cross the manager boundary as DTOs and domain snapshots; manager code does
  not reconstruct framework channel objects.
- Notification lifecycle state, runtime dedupe, registration records, and island package settings
  include Android user scope where the owning notification user is available.
- Manager application list/detail projections reject stored rows belonging to another runtime user.
- Manager event-list persistence is namespaced by Android user, so a cloned manager cannot restore
  the primary user's cached events.
- Manager event-list remote reads reject returned event summaries from another Android user instead
  of displaying them as valid results.
- Manager event-list queries now carry an explicit Android user through the DTO, Binder validation,
  runtime read model, and EventDb SQL predicate; a truncated legacy frame with no user identity is
  rejected instead of silently reading the runtime process user's event history.
- Standalone manager Usage Stats checks query the runtime app-op result instead of treating runtime
  root availability as equivalent to an authorization grant.
- Usage Stats shell fallback accepts the same `allow`, `foreground`, and `default` app-op modes as
  the manager-side platform check, avoiding ROM-dependent false negatives.
- Application list query/page, detail, diagnostics, and notification-channel query/page DTOs carry
  or verify runtime user identity; manager read sources fail closed on a mismatched response.
- Runtime application writes, event writes, and notification-channel deletes were audited: they
  resolve ownership from the current runtime user rather than trusting stale manager object ids.
- Notification channel-name enrichment filters effective notification records by the requested
  package UID's Android user, so a cloned-user channel cannot borrow a primary-user name when
  channel IDs overlap.
- KeepAlive preference reads preserve the caller's requested key scope; an unknown non-empty key
  selection returns no rows instead of widening into a full preference snapshot.
- KeepAlive preference refresh now clears the active flags when the complete provider snapshot
  cannot be read; a stale `ready=true` anti-kill or resource-bypass decision cannot survive a
  revoked or unavailable configuration.
- Zygisk configuration reads preserve unavailable state through manager convenience APIs; the
  application list and detail UI no longer interpret a failed remote read as a disabled setting.
  The Zygisk configuration page also preserves the last readable draft instead of replacing it
  with an empty config, and blocks all config-editing entrypoints while the read is unavailable;
  package scanning remains an independent read operation.
- SystemUI island package policy now fails closed when the notification user ID is missing or
  invalid; it cannot reuse a primary-user package cache entry under an ambiguous identity.
- SystemUI island preference reads now treat a missing, empty, or malformed provider cursor as
  unavailable instead of silently constructing enabled defaults.
- SystemUI island proxy post deduplication now includes explicit user and tag identity even when
  the platform status-bar key is unavailable, preventing short-window cross-user suppression.
- SystemUI island proxy ID helpers require an explicit notification user; omitted identity can no
  longer silently collapse a cloned-user proxy into user 0.
- SystemUI island ownership source keys include the notification user on both the platform
  status-bar-key path and the fallback package/id/tag path, so cancellation tracking cannot cross
  users when the platform key is unavailable.
- SystemUI island visual-state fallback keys include the notification user alongside package/id/tag;
  recording and removal therefore cannot overwrite or remove another user's visual snapshot when
  the platform status-bar key is unavailable.
- Runtime-generated island proxy notification IDs include the current Android user as well as the
  source package, matching the SystemUI-side user-scoped proxy identity for multi-user posts and
  cancellations.
- Island proxy requests preserve the source notification user through the SystemUI request Bundle;
- SystemUI island visual snapshots no longer re-read the process current-user preference after
  capture; keyed views fail closed when their notification key is unavailable, preventing a
  primary-user setting from leaking into a work-profile island;
- Notification channel-name probe notifications derive the target user from the package UID and
  pass it through temporary publish/cancel operations instead of defaulting to the XMSF process
  user;
- Island dispatcher lifecycle state uses a `(userId, notificationId)` key, so same-ID posts and
  cancellations from different Android users cannot overwrite one another in the hook process;
  payload construction uses that user for package-scoped visual and focus preferences instead of
  silently falling back to the SystemUI process's global scope.
- SystemUI proxy posting and cancellation resolve the request user's context, and cancellation
  broadcasts carry that user, so the proxy notification does not silently land in or cancel the
  current foreground user's notification space.
- SystemUI proxy posting and cancellation now fail closed when the requested user context cannot
  be resolved; the dispatcher records a posted/cancelled ID only after the underlying notification
  operation succeeds, so a cross-user failure cannot be reported as a successful local operation.
- XMSF's legacy direct island broadcast paths now carry the target package user ID and derive proxy
  IDs with the same user scope; user-ID lookup failure falls back to the current process user rather
  than the historical primary-user value.
- Island payload builders preserve an explicitly resolved caller option snapshot; a downstream
  package preference lookup cannot erase an already-authorized template payload when user scope is
  unavailable at the builder boundary.
- SystemUI island provider package-scoped reads now fail closed when the notification user is
  missing or malformed; unscoped global reads remain available for the authorized focus route.
- KeepAlive standby protection now resolves and hooks all three captured
  `AppStandbyController.setAppStandbyBucket` descriptors with descriptor-specific bucket indexes;
  a missing overload no longer silently bypasses the policy.
- KeepAlive anti-kill now has a conservative API 33 package-level pre-cleanup guard for the exact
  long `ProcessList.killPackageProcessesLSP` descriptor. It suppresses only XMSF automatic cleanup
  before MIUI SIGSTOP/process removal when lifecycle flags prove this is not uninstall, update,
  force-stop, crash/ANR, or an explicit user request.
- DeviceIdle whitelist requests no longer treat root authorization or unrelated AppOps grants as
  success. The manager write route now uses a dedicated `deviceidle` operation and the runtime
  verifies every target package with `PowerManager.isIgnoringBatteryOptimizations`; no automatic
  whitelist removal is attempted because ownership metadata is not available.
- KeepAlive binding and polling now require a resolved `KASwitch` value before using the persisted
  enabled state; a strategy arriving during ServiceBox startup cannot bind a target service before
  the online configuration gate is known.
- KeepAlive target bindings now treat service disconnect, binding death, and null binding as the
  same stale-binding transition, preserving retry behavior across service updates and empty binder
  responses.
- KeepAlive shutdown cleanup is generation-guarded; a queued cleanup from an old ServiceBox
  instance cannot unregister the observer or unbind targets after a rapid runtime restart.
- Notification click intent URIs now reject explicit components outside the target application,
  including selector components; package constraints alone are insufficient because explicit
  components take precedence during Android intent resolution.
- Event delete/restore and manager-side replay/content reads now bind the event ID to the requested
  package inside the runtime database transaction; a stale or forged manager DTO cannot operate on
  another application's event in the same Android user.
- Event-content reads now return unavailable when the runtime cannot resolve an owned event or its
  payload; remote and local manager gateways no longer echo the untrusted request content as a
  successful fallback.
- Application dispatch no longer reports a generic broadcast as delivered when no target receiver
  was discoverable; notification/replay telemetry can now distinguish an accepted send call from a
  delivery path that had no eligible consumer.
- Explicit server-side notification cancellation now clears the matching Top and Sweet lifecycle
  jobs synchronously in `NotificationController`, instead of relying only on an asynchronous
  `NotificationListenerService` removal callback; cancelled notifications cannot be reposted by
  stale bounded-lifecycle state.
- Package-data-cleared cleanup now derives the target application's Android user from its package
  UID and passes that scope into registration tasks, pending packets, runtime state, dedupe,
  notification coordinators, media sessions, conversation history, registration dedupe, KeepAlive,
  notification dispatch allowances, and extension state; cross-user KeepAlive cleanup fails closed,
  and clearing a work-profile package no longer defaults those user-scoped caches to XMSF's process
  user.
- Modern notification active-state reads now query the target package through the platform identity
  bridge before falling back to XMSF-local marker records; lifecycle timeout and cancellation logic
  can therefore find notifications posted through framework/delegated identity.
- Manager write requests now enforce operation-specific target-package and event-ID requirements at
  the Binder protocol boundary; malformed scoped requests are rejected instead of being converted
  into runtime-side no-op or idempotent-success results.
- Event delete, restore, content, JSON, and mock-replay manager writes now carry the event's
  explicit Android user through the write parcel, remote gateway, idempotency fingerprint,
  runtime executor, repository, and EventDb lookup/mutation paths; cross-user event operations no
  longer fall back to XMSF's process user.
- The in-process XMSF manager event adapter now preserves `Event.userId` in both directions;
  local manager event lists and their subsequent detail/delete/restore/mock operations no longer
  silently convert a non-primary-user event to `ManagerEvent.userId = 0`.
- Package-data-cleared and package-removed cleanup now pass the resolved target user into the
  product-owned `RegisteredApplicationDb.markUnregistered` path; a work-profile cleanup no longer
  marks the same package unregistered only in XMSF's current-user row.
- The XMSF application adapter preserves `ManagerApplication.userId` when converting back to the
  Room registration entity, keeping user identity intact across manager update projections.
- Registration secrets now use user-scoped preference keys; primary-user legacy package keys are
  migrated and retained for compatibility, while non-primary users cannot read or clear them.
  Package-data-cleared and stale-package cleanup pass the resolved user into secret removal.
- Event-record decryption and registration-state reconstruction now use the event's persisted
  Android user when resolving a registration secret; lazy event fields and local-secret recovery
  no longer borrow the XMSF process user's secret for a cloned-user event.
- Last-receive timestamps now use the same user-scoped preference key contract; event writes,
  manager reads, package-data clearing, and stale-package cleanup no longer read or erase another
  Android user's receive-time projection.
- Notification group deletion now keeps target-package operations in the target package context;
  the XMSF notification manager is only used for XMSF-owned or explicitly MiPush-managed groups,
  preventing a same-named foreign group from deleting local XMSF state.
- Notification channel/group creation no longer mirrors arbitrary target resources into XMSF after
  a target-identity operation succeeds; local mirroring is limited to XMSF-owned or explicitly
  MiPush-managed resources.
- Active-notification reads on legacy and modern compatibility paths apply the same target-package
  marker fence; a foreign package cannot inspect unmarked or another package's local XMSF posts.
- Notification-enabled state reads no longer substitute the XMSF switch when a foreign target
  package manager cannot be resolved; the target state fails closed instead of being misattributed.
- Extension notification pending state requires an explicit Android user on every internal registry
  operation; the coordinator propagates one user identity through registration, service connection,
  callback, timeout, failure fallback, and package cleanup, preventing a future omitted argument
  from silently targeting user 0.
- Stock payload deduplication requires an explicit Android user on its internal overloads; the
  external Intent entry derives the process user once, and package cleanup keeps its current-user
  compatibility boundary without allowing internal callers to omit identity.
- Registration task cleanup now requires a package name and therefore the current runtime user;
  the previous nullable default that could clear every user's pending task is available only through
  an explicitly named test reset helper.
- User-scoped manager DTO wire readers preserve a missing trailing `userId` as `-1` instead of
  silently mapping truncated or older frames to user `0`; existing protocol validation then fails
  the request/response closed while explicitly serialized user `0` remains unchanged.
- Xposed notification takeover flags are now published only after the corresponding manager and
  identity-bridge hook surfaces have been installed successfully; an absent or incompatible
  identity bridge no longer reports a complete takeover to XMSF fallback code.
- Package removal now clears the same product-owned transient notification, media-session,
  extension, KeepAlive, dedupe, conversation, runtime, and dispatch-allowance state as package-data
  clearing. The uninstall receiver carries the removed package UID's Android user into that cleanup
  without changing the frozen vendor callback contract.
- Stock Provider, Binder, manifest, and notification contracts have been re-audited against the
  curated source/dump matrix. Manifest authorities, export state, permissions, caller gates,
  sanitized external ingress, profile ownership, channel broker ownership, and notification
  metadata/lifecycle projections are covered by source-level contract tests; the remaining gaps are
  target-ROM and multi-user runtime evidence rather than unverified source assumptions.
- MessagingStyle conversation history now keys and seeds active notifications with the effective
  MiPush target package and the target package UID-derived Android user, so MIUI-routed container
  packages cannot borrow or clear another user's conversation history.
- Native media-session keys and all notification publish/cancel cleanup paths now carry the target
  package's Android user; work-profile media sessions are no longer released through XMSF's
  current-user fallback.
- Sweet reminder suppression, timeout lifecycle, Top notification repost lifecycle, and VoIP
  sequence filtering now receive the same target package user at the notification ingress boundary;
  these business-state maps no longer silently fall back to XMSF's process user for cloned apps.
- Sweet screen-on keyguard restoration preserves the user encoded in persisted milepost keys and
  matches active notifications by package, user, and notification ID; non-primary-user reminders
  are no longer discarded during restoration.
- Notification cancellation now scopes local fallback records by Android user and target marker;
  the focus lifecycle no longer issues an unscoped second host-manager cancel that could remove an
  unrelated notification sharing the same tag and ID.
- Notification cancellation now fails closed when the requested user differs from the XMSF process
  user; a foreign-user request cannot reach a current-user `NotificationManager.cancel()` fallback.
- Notification publication now applies the same process-user boundary; Top initial/repost jobs and
  Sweet restoration pass their target user, while foreign-user posts fail closed instead of being
  silently published into the current user's notification space.
- Mock replay receipt publication now applies the same foreign-user fail-closed rule; a rejected
  target-identity post cannot fall through to a current-user local notification manager.
- MiPush Island preference reads in target notification and mock paths now pass the package UID
  derived user, preventing cloned-user settings from borrowing the primary-user configuration.
- Application-detail Zygisk state preserves `null` when root/configuration access is unavailable;
  the UI no longer collapses an unreadable state into the valid "disabled" value `false`.

### Next implementation priorities

1. Deferred by request: the diagnostic export now captures the latest 12 `ApplicationExitInfo` records in
   `system/application_exit_history.txt`, with memory-related exits marked and the existing
   sanitizer applied. Android 17 stability and limiter-cause evidence is intentionally postponed.
2. KeepAlive now emits structured observer registration/fallback and service connected/disconnected
   events, while retaining the polling fallback when hidden API or permission access is unavailable.
   Add device evidence for observer registration and a real third-party service bind before claiming
   the path works on the target ROM.
3. Collect installed-device before/after evidence for the audited stock contracts: provider caller
   identity, user/channel/intent preservation, delegated notification lifecycle, and self-update
   notification revival. The Island provider intentionally keeps its framework-level permission
   empty because its code guard supports SystemUI and AMap's restricted focus-only route separately.
4. External MiPush ingress is split by transport: bound Messenger calls are caller-package
   authenticated through `sendingUid`, while the stock legacy `startService` route remains
   payload-gated because `onStartCommand` has no originating UID. This leaves package-scoped
   control actions such as disable/clear vulnerable to a forged installed-package name on the
   legacy route. Keep this as an explicit residual risk and do not add a permission or registration
   prerequisite until a separate authenticated transport is proven compatible with stock
   first-registration and send flows.
5. KeepAlive anti-kill remains partial on the captured API 33 ROM: MIUI `ProcessKiller` also reaches
   the short `ProcessRecord.killLocked(reason, 13, true)` overload, which becomes subreason `0` and
   is intentionally outside the current final-sink allowlist. Device evidence must verify the new
   package-level guard and its negative controls before any further expansion; do not widen
   subreason `0` at the final sink alone.
6. Validate package-data-cleared cleanup on a multi-user device, including a work-profile package
   clear and a primary-user package with the same name. The stock callback still exposes only the
   package name, so the implementation derives the target user from the installed package UID
   without changing the frozen vendor contract.
7. Validate modern Android 10+ delegated/framework notification lifecycle behavior on device:
   target active records must be visible to timeout/cancel paths, while local fallback records must
   remain discoverable when target identity posting is unavailable.

## Verification Gates

For source changes, run the narrow module test first, then the affected boundary gates:

```text
:common:testDebugUnitTest
:xmsf:testNormalDebugUnitTest
:xposed:compileDebugKotlin
:app:assembleNormalDebug
:mipush:assembleDebug
verifyModuleBoundaries
```

Device-only claims require fresh phone-side logs or `dumpsys` output. A green JVM test or build is
not evidence of visible island rendering, notification click delivery, memory-limiter behavior, or
KeepAlive service binding on a device.
