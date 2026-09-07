# XMSF Module Decomposition Roadmap

> Created: 2026-08-23
> Updated: 2026-09-01
> Status: platform, notification, runtime, push, manager-contract, focus, manager-state, and logging policy slices are complete; further shell shrink remains incremental.
> Source evidence: package tree analysis + cross-package import audit at the current worktree.

## Current State

`:xmsf:shell` currently contains 192 Kotlin main files (measured 2026-08-31) after the
platform, notification, runtime, and push extraction slices:

| Namespace | Files | Role |
|-----------|-------|------|
| `com.xiaomi.*` | 60 | Stock XMSF compatibility surface (frozen ABI) |
| `io.github.magisk317.mipush.*` | 132 | Product code remaining in shell |

Extracted modules: `:xmsf:notification` (22 main), `:xmsf:push` (13 main),
`:xmsf:platform` (10 source files across its source sets), `:xmsf:runtime` (58 main), and
`:xmsf:runtime:store` (14 common/Android source files).

### Current closure

- `:manager:port` owns the platform-neutral `ManagerApplication`, `ManagerRuntimeEnvironmentSnapshot`, `ManagerConnectionSnapshot`, `MockReplayOutcome`, and pure application/event/diagnostic/log-result models. `:xmsf:runtime` consumes the port without a direct `:manager:application` edge, while shell/application adapters declare the port explicitly where their signatures use port values. The boundary verifier rejects Android/JVM/vendor leakage into the port, and the compatibility tests run in the CI pure-module shard.
- The quality gate now applies Kover only to `:common`, `:core`, `:xposed`, and `:xmsf:shell` at their reviewed floors. The current combined `check`, Kover, XMSF build, and manager build passed with 1362 actionable tasks; this is a gate alignment change, not an assertion that packaging-only modules have test coverage.
- Configuration DSL item 2 is now complete: `:core commonMain` owns the platform-neutral Lisp evaluator and match/placeholder replacement plans, while `common` retains only the JVM URI/Base64 codec facade. Thrift reflection, field access/conversion, mutation, and configuration loading remain in Android/JVM adapters. Core direct tests and active xmsf configuration regression tests pass.
- Last Android 17/API 37 device smoke (2026-08-31) covers verified `221345` APK transfer/install, cold startup, XMSF/manager processes, core services, Binder, and repeated `Connected` connection snapshots. It also verified the missing target `mipush.xml` warning fix. The separate BAFE gate captures an existing XMSF-posted active notification donor when XMSF's own `POST_NOTIFICATIONS` permission is denied; capture succeeds on attempt 1 and the selected donor's exact identity remains present (`1 -> 1`). The 2026-09-01 policy refactor is not included in that device evidence.
- The exported `XMPushService` facade now acknowledges external foreground starts with a transient `ForegroundHelper` notification, removes that foreground state, dispatches to the private core, and stops itself. The Android 17 deadline smoke completed without a crash/ANR; this remains source/runtime behavior already covered by the last installed APK, not evidence for the uninstalled 2026-09-01 refactor.
- **Completed source slice:** all remaining vendor-neutral connection status, client-change, channel open/rebind, reset, network-check, redirect, host/GSLB, HostManager, socket candidate/retry/sink-down, and SLIM handshake/payload/write decisions now execute canonical `:core` `commonMain` plans. Existing Android/JVM facades, action/reason strings, stock status codes, query order, throttle boundaries, and vendor ABI remain in place.
- Android/vendor intentionally retains URL encoding/parsing, sockets, network and file I/O, notification/service lifecycle, fallback persistence, Blob/RC4/CRC/protobuf handling, PendingIntent cloning, logging/telemetry, and observer/DI composition. These are execution adapters, not unfinished policy migrations.
- Direct core tests, affected runtime/vendor/shell regressions, production wiring tests/contracts, Detekt, module boundaries, and god-file limits pass for the current source slices. BAFE device evidence remains limited to the last installed APK and is not extended to the uninstalled policy refactor.
- `:core` now owns manager contract validation/size arithmetic, focus parsing/planning, page activation/request isolation, JSONL encoding/redaction, and daily route quota decisions. Android/Parcelable/Binder/Compose/file adapters remain in their owning modules.
- `PushHealthSnapshotLogger` keeps Android/runtime collection in `:xmsf:shell`; neutral snapshot values and deterministic formatting are owned by `:core` and covered by `:core:jvmTest`.

### Cross-Package Coupling Audit

Inbound dependency counts (files outside the package that import from it):

| Package | Inbound | Extraction Difficulty |
|---------|---------|----------------------|
| platform/support | 44 | High — shared foundation |
| runtime | 26 | High — core state spine |
| utils | 26 | High — general utilities |
| service | 24 | High — lifecycle entrypoints |
| app package | 19 | Medium — DI composition root |
| notification | 9 | **Low** — clear boundary |
| control | 5 | Low |
| config/diagnostics/network/compat | 3–4 each | Low |
| manager/runtime | 1 | **Trivial** |
| hook/receiver/telemetry | 1 each | Trivial |

### Vendor Dependency

Product code still imports retained `com.xiaomi.*` vendor classes through reviewed adapter and
compatibility paths. This remains the main coupling factor preventing a mechanical shell split.

## Proposed Target Architecture

```text
:xmsf:platform     ← shared contracts, logging, DTOs, support utilities
    ↑
:core              ← existing (already extracted)
    ↑
:xmsf:shell stock ABI ← com.xiaomi.* frozen compatibility surface (~60 files)
    ↑
:xmsf:notification ← notification policy, adapters, construction
    ↑
:xmsf:runtime      ← runtime state, stores, data access
    ↑
:xmsf:push         ← push pipeline, connection management, hooks
    ↑
:xmsf:shell              ← manifest entrypoints, services, receivers, DI composition
```

## Phased Migration Plan

### Phase 0: Extract `:xmsf:platform` — completed

`io.github.magisk317.mipush.platform.support` and the reusable platform contracts now live in
`:xmsf:platform`, with Android implementations kept in its Android source set.

- **Ownership:** the module now contains shared platform contracts and Android-only support
  implementations. Direct vendor access remains behind reviewed adapters; it is not a reason to
  introduce a new reverse dependency.
- **Validation:** `verifyModuleBoundaries` and the full `check --warning-mode=all` pass.
- **Follow-up:** direct vendor coupling remains an explicitly reviewed adapter concern; it is not
  a reason to move stock/runtime behavior back into the platform module.

### Phase 1: Extract `:xmsf:notification` — completed

`io.github.magisk317.mipush.notification` now lives in the dedicated library module. Shell code
keeps only the Android/runtime adapters that need shell lifecycle and stock resources.

- **Why second:** clear domain boundary, already documented in `xposed-notification-boundary.md`.
- **Validation:** notification unit tests, shell regression tests, lint, detekt, and full checks pass.
- **Device gate:** notification publish and SystemUI fallback behavior still require a later device
  run and are not implied by this source extraction.

### Phase 2: Extract `:xmsf:runtime` — core completed

`:xmsf:runtime` now owns the independently compilable Android runtime facade/state, pending queues,
connection/runtime helpers, selected lifecycle helpers, and the keep-alive Binder bridge.
`:xmsf:runtime:store` is its KMP persistence child only: database schemas, DAOs, migrations, rows,
and persistence repositories. Neutral duplicate-message and reconnect policies, notification policy/contracts,
and the cross-host Zygisk configuration DSL moved to `:core`; the neutral `ManagerApplication`,
runtime snapshot values, mock-replay result, and pure application/event/diagnostic/log-result
models are in `:manager:port`, while the remaining non-Binder Manager ports/models stay in
`:manager:application`. Its configuration DTO signatures are directly owned by
`:core`, not re-exported through `:common`. AIDL/Binder/Parcelable wire ABI stays in
`:manager:contract`. Source packages at the stock boundary and external ABI remain unchanged.

The remaining shell-side runtime adapters intentionally stay in `:xmsf:shell`: notification
construction, Android-backed Manager Binder sources/writers, `AppDependencies`/Koin composition,
`PushRuntimeExecutionBridge`, and adapters that require the shell lifecycle bridge. This avoids
a `:xmsf:runtime` → `:xmsf:shell` reverse dependency.

- **Validation:** `:xmsf:runtime:compileDebugKotlin`, `:xmsf:runtime:testDebugUnitTest`,
  `:xmsf:runtime:store:compileAndroidMain`, and `:xmsf:shell:compileNormalDebugKotlin` pass.
- **Next:** continue shrinking shell-side adapters that can move without reverse dependencies,
  and keep extracting reusable pure policies into KMP `commonMain` with compatibility facades.

### Phase 3: Extract `:xmsf:push` — core completed

`:xmsf:push` now owns the independently compilable push pipeline, connection management,
and hook-facing bridges that do not require shell lifecycle state. Shell retains DI composition,
service entrypoints, and adapters whose dependencies would otherwise create a reverse edge.

- **Risk:** High for further moves. Connection lifecycle, reconnect logic, and packet handling are
  behavior-critical.
- **Validation:** `:xmsf:push:compileDebugKotlin`, `:xmsf:shell:testNormalDebugUnitTest`, and
  `:xmsf:shell:compileNormalDebugKotlin`; add long-connection and push-delivery device checks for
  behavior changes.

### Cross-cutting ownership-first policy extraction sequence

KMP source sets and targets are build/test partitions, not architecture boundaries. Move code only when its responsibility and dependency direction justify a new owner; an Android-only adapter may be correctly layered without becoming KMP. Reusable policies still hosted by Android/JVM modules are extracted in small compatibility-preserving slices:

1. **Push runtime plans — completed 2026-08-31:** vendor-neutral connection, socket, SLIM, and host decisions live in `:core` `commonMain`; stock/vendor constants, Android state mapping, URL/transport/protocol execution, persistence, and side effects remain in Android/vendor adapters. Production `Connection`, `SocketConnection`, `HostManager`, `BlobReader`, and `BlobWriter` paths execute those plans rather than test-only facades.
2. **Configuration DSL — completed 2026-08-31:** platform-neutral Lisp evaluation and match/placeholder replacement decisions live in `:core` `commonMain`; `common` keeps only the JVM URI/Base64 codec facade, while Thrift reflection, field access/conversion, mutation, and configuration loading remain on JVM/Android.
3. **Manager contract core — completed:** handshake validation, DTO validation inputs, wire-size arithmetic, availability signals, and reconnect policy are platform-neutral; Parcelable/AIDL remains in `:manager:contract` and scheduling remains in `:manager:client`. Shared runtime snapshot values are owned by `:manager:port`.
4. **Focus semantics — completed:** JSON parsing, inference, labels, progress extraction, semantic style, and routing planning execute in `:core`; `:xmsf:notification` only adapts Android/protocol values and capabilities.
5. **Manager state policies — completed:** page activation/request generation, snapshot validity, and neutral read sorting live in `:core`; manager UI retains Compose/NavController and persistence adapters.
6. **Logging core — completed:** JSONL encoding, escaping, redaction, naming, and quota deletion decisions live in `:core`; `common`, manager, and XMSF retain filesystem enumeration/deletion/write adapters.
7. **Optional platform-specific policies — completed for reviewed keep-alive slice:** Xposed process/keepalive decisions call `KeepAlivePolicyCore`; system mutation and reflection remain in Xposed.

Every slice must keep the existing Android/JVM facade or source-compatible aliases, preserve machine-readable action strings and stock/wire behavior, add direct tests for the shared implementation, and pass both the shared tests and the affected Android-module regression suite before the next slice starts. Core policy code must not import vendor, Android, Java I/O, reflection, Parcelable, or Thrift types.

### Phase 4: Shrink `:xmsf:shell` to shell

After Phases 0–3, remaining `:xmsf:shell` content should be limited to:
- Manifest entrypoints (services, receivers, providers)
- DI composition root (`xmsf/`)
- Stock ABI surface (`com/xiaomi/xmsf/`) that cannot move due to external component names
- Bridge/compat glue

No file-count release gate applies to `:xmsf:shell`. File count is recorded only as a maintenance
signal: move code only when its responsibility can leave shell without adding a reverse dependency
or weakening stock/manifest/lifecycle compatibility.

The current shell audit intentionally leaves `NetworkPolicyCompat`, `ConfigNavigationHelper`,
`SdkNotificationCompat`, and `NotificationController` in shell: they respectively depend on
vendor/hidden-API/global config, application UI routing, stock service/Thrift, or stock resources
and service internals. `PushHealthSnapshotLogger` still owns Android/runtime collection in shell,
while its neutral snapshot values and formatter live in `:core`. None of the remaining collectors
is an independently extractable feature adapter. `MockNotificationKind`, which was only
notification-feature state, moved to
`:xmsf:notification`; no move is made merely to meet a file-count target.

## Current verification

The current worktree passes `check`, `qualityGateKoverVerify`, `:xmsf:assembleNormalDebug`, and
`:mipush:assembleGithubDebug` together, plus `verify_module_boundaries.sh`, ShellCheck, and
`git diff --check`. The navigation performance specification's device checkpoint remains a separate
unclosed benchmark gate; code refactoring completion does not claim a new device performance result.

## Non-Goals

- Do NOT extract `com.xiaomi.*` vendor protocol classes into a separate module.
  They are deeply intertwined with xmsf's stock-compatible component names.
  The `:vendor` module already isolates them at the Gradle level.
- Do NOT split `:manager:ui` further. Its public boundaries are already explicit via
  `:manager:contract` and `:manager:client`.

## Success Metrics

| Metric | Baseline | Desired direction |
|--------|----------|-------------------|
| xmsf shell Kotlin main count | 192 on 2026-08-31 | Observation only; reduce only after ownership/dependency proof |
| xmsf shell direct project deps | reduced by runtime-core extraction | No reverse `runtime`/`notification`/`push` → `shell` edge |
| Incremental compile time (touch 1 file in runtime) | ~45s | Measure after each independently justified extraction; no architecture gate |
