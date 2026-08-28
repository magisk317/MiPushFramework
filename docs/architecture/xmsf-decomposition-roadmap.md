# XMSF Module Decomposition Roadmap

> Created: 2026-08-23
> Updated: 2026-08-26
> Status: runtime core, notification Phase 1, and push Phase 3 partially implemented; platform expansion and stock extraction remain.
> Source evidence: package tree analysis + cross-package import audit at current HEAD.

## Current State

`:xmsf:shell` currently contains 162 Kotlin main files after runtime-core,
notification, and push extractions:

| Namespace | Files | Role |
|-----------|-------|------|
| `com.xiaomi.*` | 54 | Stock XMSF compatibility surface (frozen ABI) |
| `io.github.magisk317.mipush.*` | 108 | Product code remaining in shell |

Extracted modules: `:xmsf:notification` (21 main), `:xmsf:push` (13 main),
`:xmsf:platform` (6 main), `:xmsf:runtime` (74 main including KMP store).

### Cross-Package Coupling Audit

Inbound dependency counts (files outside the package that import from it):

| Package | Inbound | Extraction Difficulty |
|---------|---------|----------------------|
| platform/support | 44 | High — shared foundation |
| runtime | 26 | High — core state spine |
| utils | 26 | High — general utilities |
| service | 24 | High — lifecycle entrypoints |
| app | 19 | Medium — DI composition root |
| notification | 9 | **Low** — clear boundary |
| control | 5 | Low |
| config/diagnostics/network/compat | 3–4 each | Low |
| manager/runtime | 1 | **Trivial** |
| hook/receiver/telemetry | 1 each | Trivial |

### Vendor Dependency

91 product-code files import from `com.xiaomi.*` vendor classes. This is the single
largest coupling factor preventing clean module separation.

## Proposed Target Architecture

```text
:xmsf:platform     ← shared contracts, logging, DTOs, support utilities
    ↑
:core              ← existing (already extracted)
    ↑
:xmsf:stock        ← com.xiaomi.* frozen ABI surface (~55 files)
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

### Phase 0: Extract `:xmsf:platform`

Move `io.github.magisk317.mipush.platform.support` and `io.github.magisk317.mipush.utils`
into a new `:xmsf:platform` Android library.

- **Why first:** 44 + 26 = 70 files depend on these packages. Extracting them removes
  the largest source of transitive coupling.
- **Risk:** Medium-High. Detailed audit (2026-08-23) found 11 of 24 files have direct
  vendor imports (`com.xiaomi.*`). Two files (`PermissionUtils.kt`, `LogUtils.kt`)
  also reference `com.xiaomi.xmsf.R`, creating a circular dependency risk.
  Clean extraction requires either: (a) accepting a `platform → vendor` Gradle
  dependency, or (b) introducing interface layers to decouple protocol access.
  Recommend option (a) as an interim step; revisit option (b) after Phases 1–3.
- **Prerequisite:** Commit current lint/build/test batch first.
- **Validation:** `verifyModuleBoundaries` passes; all modules compile.
- **Estimated effort:** Medium (24 file moves + ~70 import updates + build config).

### Phase 1: Extract `:xmsf:notification`

Move `io.github.magisk317.mipush.notification` into a new library module.

- **Why second:** Only 9 external consumers, clear domain boundary, already documented
  in `xposed-notification-boundary.md`.
- **Risk:** Medium. Notification code interacts with Xposed hooks and stock bridges;
  must verify fallback paths still work without the hook present.
- **Validation:** Existing notification unit tests pass; device smoke test for
  notification publish path.
- **Estimated effort:** Medium.

### Phase 2: Extract `:xmsf:runtime` — core completed

`:xmsf:runtime` now owns the independently compilable Android runtime facade/state, pending queues,
connection/runtime helpers, selected lifecycle helpers, and the keep-alive Binder bridge.
`:xmsf:runtime:store` is its KMP persistence child only: database schemas, DAOs, migrations, rows,
and persistence repositories. Neutral duplicate-message and reconnect policies, notification policy/contracts,
and the cross-host Zygisk configuration DSL moved to `:core`; non-Binder Manager ports/models and the
mock-replay result moved to `:manager:application`. Its configuration DTO signatures are directly owned
by `:core`, not re-exported through `:common`. AIDL/Binder/Parcelable wire ABI stays in
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

1. **Push runtime plans:** move vendor-neutral connection, socket, SLIM, and host decisions into `:core` `commonMain`; retain stock/vendor constants and execution in Android adapters.
2. **Configuration DSL:** move Lisp evaluation and neutral match/replace decisions after introducing codec and field-accessor boundaries; keep Thrift reflection and configuration loading on JVM/Android.
3. **Manager contract core:** isolate handshake, validation, size estimation, availability, and reconnect policy from Parcelable/AIDL DTOs.
4. **Focus semantics:** isolate JSON parsing, inference, labels, and planning from Android notification capability mapping.
5. **Manager state policies:** extract page activation, snapshot validation/generation, and neutral read sorting/filtering.
6. **Logging core:** extract JSON-line encoding, redaction, and limiter state while injecting clock and synchronization.
7. **Optional platform-specific policies:** Xposed process/keepalive rules and vendor fallback weighting follow only after higher-value slices are stable.

Every slice must keep the existing Android/JVM facade or source-compatible aliases, preserve machine-readable action strings and stock/wire behavior, add direct tests for the shared implementation, and pass both the shared tests and the affected Android-module regression suite before the next slice starts. Core policy code must not import vendor, Android, Java I/O, reflection, Parcelable, or Thrift types.

### Phase 4: Shrink `:xmsf:shell` to shell

After Phases 0–3, remaining `:xmsf:shell` content should be limited to:
- Manifest entrypoints (services, receivers, providers)
- DI composition root (`app/`)
- Stock ABI surface (`com/xiaomi/xmsf/`) that cannot move due to external component names
- Bridge/compat glue

No file-count release gate applies to `:xmsf:shell`. File count is recorded only as a maintenance
signal: move code only when its responsibility can leave shell without adding a reverse dependency
or weakening stock/manifest/lifecycle compatibility.

The current shell audit intentionally leaves `NetworkPolicyCompat`, `ConfigNavigationHelper`,
`PushHealthSnapshotLogger`, `SdkNotificationCompat`, and `NotificationController` in shell: they
respectively depend on vendor/hidden-API/global config, application UI routing, service lifecycle,
stock service/Thrift, or stock resources and service internals. None is an independently extractable
feature adapter. `MockNotificationKind`, which was only notification-feature state, moved to
`:xmsf:notification`; no move is made merely to meet a file-count target.

## Non-Goals

- Do NOT extract `com.xiaomi.*` vendor protocol classes into a separate module.
  They are deeply intertwined with xmsf's stock-compatible component names.
  The `:vendor` module already isolates them at the Gradle level.
- Do NOT split `:manager:ui` further. Its public boundaries are already explicit via
  `:manager:contract` and `:manager:client`.

## Success Metrics

| Metric | Baseline | Desired direction |
|--------|----------|-------------------|
| xmsf shell Kotlin main count | 162 at roadmap baseline | Observation only; reduce only after ownership/dependency proof |
| xmsf shell direct project deps | reduced by runtime-core extraction | No reverse `runtime`/`notification`/`push` → `shell` edge |
| Incremental compile time (touch 1 file in runtime) | ~45s | Measure after each independently justified extraction; no architecture gate |
