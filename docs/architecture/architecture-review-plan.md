# MiPushFramework Architecture Review

## Current Status

The dual-APK runtime architecture, notification ownership boundaries, and the vendor–XMSF split are settled at the source level. Source, JVM, static checks, boundary checks, and builds all pass; items requiring a real ROM, cross-process, or cross-user triggers are not yet claimed as complete (see "Open Gates").

## Architectural Invariants

- `:xmsf` packages only the installable `com.xiaomi.xmsf` runtime; `:mipush` owns the manager UI and the Xposed entry point.
- The manager and the runtime communicate through a signature-protected Binder contract.
- Failed remote reads remain unavailable and are never converted into empty success results.
- Notification, Island, HyperIsland, proxy, click routing, and lifecycle behavior retain existing compatibility targets.
- All available notification, app, event, registration, media, KeepAlive, Island, and cleanup states use an explicit Android user scope; missing or invalid identities fail closed.
- `vendor` is responsible only for event detection, transport implementation, and plan execution; `:core` holds reusable neutral policies; `xmsf` owns runtime composition and Android side effects. `vendor` must not introduce product policies.

## Ownership and Layering

### Runtime and Data

- Apps, events, registrations, notification channels, media sessions, dedup, extensions, KeepAlive, and package cleanup are user-isolated using `(user_id, package)` ownership.
- Large-file splitting is complete without raising gates: `ManagerRuntimeClient.kt` / `AndroidPushRuntime.kt` have been split into `ManagerRuntimeBindSession.kt`, `ManagerRuntimeDiagnostics.kt`, and `AndroidPushRuntimeWindowSupport.kt`; connection state transitions and session merges are centralized in `RuntimeConnectionObservationCoordinator`. The public facade, single runtime state owner, and lock boundaries are unchanged. (Line counts are intentionally not pinned to avoid rotting as code evolves.)
- `PushRuntimeRegistrationChannelObservationSink` carries `androidUserId` explicitly; shell execution and channel adapters pass the resolved user; `MiPushRuntimeBridge` dedup and `RuntimeRegistrationCoordinator.replayPending()` no longer drop the user.
- Manager DTOs, Binder validation, runtime reads/writes, database queries, local adapters, and dedup helpers all use an explicit user scope; production paths no longer use `coerceAtLeast(0)` or exceptional fallback to user 0. Legacy DB row migration and the `0` default in output DTOs serve only protocol compatibility and are not used for caller authorization.
- Platform-neutral decisions for connection, client change, channel, reset, network, redirect, host, GSLB, HostManager, socket candidate, retry, sink-down, and SLIM handshake, payload, and write are unified in `:core commonMain`; real I/O, URLs, fallback persistence, Blob/RC4/CRC/protobuf, Service/Intent, and telemetry side effects remain in Android/vendor.
- Manager contract validation and size arithmetic, focus parsing and planning, page activation and request isolation, snapshot validity and sorting, JSONL encoding and redaction, daily route quota, and availability/backoff are held by `:core`; Parcelable/AIDL, Compose/NavController, Binder scheduling, and filesystem operations remain in their owning modules.

### Notification and Island

- Active notification queries are keyed by the target package's UID and user identity; local fallback accepts only the target marker and target user; `getActiveNotifications(packageName, userId)` is the explicit interface, a negative `userId` fails closed, and the legacy interface without a user argument is kept only for compatibility and delegates to the current space.
- Channel/group creation, deletion, name completion, and delegated notification lifecycle retain target-package ownership.
- Island proxy post, cancel, dedup, ID, visual snapshot, preference, and broadcast all carry user identity; Top, Sweet, VoIP, extension, focus, progress, click intent, and cancellation have product-side user/package isolation.
- When the target user cannot be resolved, publish, cancel, provider reads, and configuration reads all fail closed.
- Notification reflection and ROM compatibility exceptions have explicit failure logs, fallback, and safe exception propagation, with no detekt file-level suppression.

### Vendor–XMSF Boundary

- `vendor` owns stock transport, heartbeat, timeout, compatibility predicates, and frozen implementations; it delegates product policy decisions to `xmsf` through observers and plans, and no longer holds product policies directly.
- Retained frozen compatibility surface: `PING_TIMEOUT_MS=10s`, `CONNECTING_TIMEOUT=15s`, heartbeat `235s`/`600s`, `shouldReconnect`/`shouldFalldown`, minimal observer adapter calls; product imports are recorded in `scripts/vendor_boundary_baseline.txt` with no new boundary debt.
- Dedup entry points are layered by call timing and cannot be merged into a global cache:
  - `StockMiPushPayloadDeduper` (`:xmsf:runtime`): full payload digest before `MiPushFacadeService` forwards external SDK intents, preserving the stock stale-hit ordering.
  - `AndroidPushRuntimeWindowSupport` (`:xmsf:runtime`): inbound observation for `PushRuntime.observeInboundMessage`, user/package/message identity, and action burst windows.
  - `DuplicateMessagePolicy` (`:core`): the hook duplicate callback of `xmsf/push` `ExplicitHookBridge`, keyed by user/package/message ID.
  - The stock SDK's 25-ID app-facing cache is merged with `RegistrationRecordDeduper` local registration history; each serves a different call timing.
  - `PushRuntimeDuplicateStore` / `MiPushMessageDuplicate` remain only as compatibility facades/adapters and are not the current delivery gate.
- Android 17 optional reflection treats only explicitly missing classes/methods as unavailable and enters a restricted fallback; real failures such as `SecurityException` / `InvocationTargetException` retain failure semantics.
- `XSpaceXmsfInstallKeeper` keeps unavailable on dual-app read failures and performs no root or package mutation; user 999 sync uses only explicit fixed-package commands.
- `MiPushZygisk` native hooks belong to an external source/ref/NDK ABI; this repository consumes only their build artifacts.

## Stock Parity Decisions

Source and build layers are verified; cross-UID and cross-device parity still has open gates. Conclusions from the manifest diff against stock 3.7.9 → 7.4.67-C → 7.5.29 (see `docs/CHANGELOG.md` v1.0.3):

- Upstream sync principle: features removed upstream are removed here too, with no historical debt. `FocusNotificationCollectionFilter` and the `OnetrackSwitch(140)` gate chain have been removed; LNS/LNC, CollectRunningTasks, and MainProcBridge were never mirrored or were removed early (guarded by absence tests).
- Retained decisions: ads q0/r0 ack, tiny-data ingress rejection, subscribe-remind, hyper_type/TTS/CallKit, OcVersion/DailyCheck, PushControl, and the MiCloud provider are still shipped in 7.5.29; `island.prefs`/IconPack are product-owned surfaces; the pinned 3.7.9 dictionary is client-side frozen fidelity.
- The spoofed version has been raised to `7.5.29-C / 70005029` (`PushVersionInfo.STOCK_XMSF_APP_VERSION_*`, plus the fakeXmsfPackageInfo and service-package self-report chains).
- Five Island/dispatch consistency fixes: focus authentication allow-all, the five `MESSAGE_ARRIVED` fixes, `autoMarkPkgs` pass-through and ack alignment, and 30-second non-display dedup changed to an empty-messageId bypass.
- SendMessage pass-through display classification and the 6-hour replay drop are product decisions; the FCM assemble-push decryption branch has zero impact in this architecture (no FCM receiver, tokens are never uploaded).
- Known limitations (all with code KDoc anchors): the msg-id → notification ID registry, AppOps notification permission writes, the CallKit watcher, `provider.g` profile cache refresh, scenepush, the GroupBindCommand Room ledger, server allowlist/scheduled sync, the `h/h.c` click-statistics ring, and encrypted LBS command payloads.
- OneTrack, tiny-data, LNS/LNC, installed-app collection, log upload, and notification exposure collection have not been restored.

## Source-Level Verification

- `./gradlew check qualityGateKoverVerify :xmsf:assembleNormalDebug :mipush:assembleGithubDebug --warning-mode=all --console=plain` is green; Kover applies only to `common/core/xposed/xmsf:shell` (10%/10%/10%/7%), and zero-test packaging/vendor modules are excluded.
- Kotlin compiler warnings 0, lint findings 0; `verifyModuleBoundaries`, ShellCheck, CI shard script tests, and `git diff --check` pass.
- `manager:port` JSON compatibility, CallMessage, PushCommonProvider, regSec missing-target preference file, and XMSF runtime regression tests pass.
- The historical device dump index is 87 raw / 86 unique / 0 errors, kept only as an audit record and not representative of the current working tree.

## Open Gates

### Device and Cross-Process

- Long-term background survival, manifest/component external call surface, and long-connection recovery after restart/standby; currently only short-window cold start and connected snapshots are covered.
- Heartbeat learning, ping timeout, network switching, OC updates, and alarm re-registration.
- KeepAlive Binder/config, observer/polling, bind transfer, unbind, retry, and anti-kill negative controls.
- Real cross-UID SDK entry, registration, dedup, control actions, payload validation, and telemetry rejection; the registration-specific query for user 999 has not been triggered separately.
- Package data clear/removal, user isolation, and same-package cross-profile cleanup.

### Notification and Platform

- Delegated/framework notification lifecycle, target identity, channel/permission/group/click preservation; BAFE active donor capture passes under Android 17 permission denial, but real cross-UID background-activity launch still needs separate verification.
- HyperIsland, Top, Sweet, VoIP, extension callback/fallback, focus/progress, and self-update revival.
- Provider/Binder caller authorization, cloud/account compatibility, XSpace, and privileged platform helpers.
- AOSP/non-MIUI downgrade, Android 16+ progress and promoted ongoing notification behavior.

### External Dependencies

- Versioned evidence for external platform helpers such as `SecurityCoreAdd.apk` is missing.
- `MiPushZygisk` native source/ref/NDK ABI native registration and Android 17 compatibility need verification in a separate source/release gate.
- XMSFKeeper and other privileged helpers are treated as external dependencies and are not faked inside XMSF.

## Non-Claims

Source tests, static checks, buildable APKs, manifest presence, short no-crash windows, or a single `dumpsys notification` snapshot cannot by themselves prove visible UI, cross-UID calls, cross-user isolation, KeepAlive binding, heartbeat learning, or full stock parity. Historical APK/device window evidence cannot replace the gates above.

## References

- Current boundary ownership and verification status are defined by this document, `xmsf-decomposition-roadmap.md`, `xmsf-package-boundaries.md`, `current-runtime-call-flow.md`, and the repository/module `AGENTS.md`.
- Historical specs in `.kiro/specs/` retain only original task context and are not a source of current status.
