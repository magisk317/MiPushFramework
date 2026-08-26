# XMSF Module Decomposition Roadmap

> Created: 2026-08-23
> Updated: 2026-08-26
> Status: runtime core, notification Phase 1, and push Phase 3 partially implemented; platform expansion and stock extraction remain.
> Source evidence: package tree analysis + cross-package import audit at current HEAD.

## Current State

`:xmsf:shell` currently contains 167 Kotlin main files after runtime-core,
notification, and push extractions:

| Namespace | Files | Role |
|-----------|-------|------|
| `com.xiaomi.*` | 54 | Stock XMSF compatibility surface (frozen ABI) |
| `io.github.magisk317.mipush.*` | 113 | Product code remaining in shell |

Extracted modules: `:xmsf:notification` (20 main), `:xmsf:push` (13 main),
`:xmsf:platform` (6 main), `:xmsf:runtime` (63 main including KMP store).

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

`:xmsf:runtime` now owns the independently compilable runtime core: runtime facade/android
state, pending queues, duplicate stores, connection/runtime helpers, selected lifecycle helpers,
KMP store database facade, and the keep-alive Binder bridge. `:xmsf:runtime:store` remains the
KMP child module. Source packages and external ABI remain unchanged.

The remaining shell-side runtime adapters intentionally stay in `:xmsf:shell`: notification
construction, Manager Binder read/write adapters, `AppDependencies`/Koin composition,
`PushRuntimeExecutionBridge`, and adapters that require the shell lifecycle bridge. This avoids
a `:xmsf:runtime` → `:xmsf:shell` reverse dependency.

- **Validation:** `:xmsf:runtime:compileDebugKotlin`, `:xmsf:runtime:testDebugUnitTest`,
  `:xmsf:runtime:store:compileAndroidMain`, and `:xmsf:shell:compileNormalDebugKotlin` pass.
- **Next:** move the push pipeline as the next cohesive large block, then revisit the remaining
  shell adapters that can follow it without introducing cycles.

### Phase 3: Extract `:xmsf:push`

Move `io.github.magisk317.mipush.push.*` (pipeline, connection management, hooks) into
a new library.

- **Why last among product splits:** Push pipeline depends on notification (Phase 1)
  and runtime (Phase 2). It must come after both.
- **Risk:** High. Connection lifecycle, reconnect logic, and packet handling are
  behavior-critical.
- **Validation:** Long-connection stability test on device; push delivery E2E.
- **Estimated effort:** Large.

### Phase 4: Shrink `:xmsf:shell` to shell

After Phases 0–3, remaining `:xmsf:shell` content should be limited to:
- Manifest entrypoints (services, receivers, providers)
- DI composition root (`app/`)
- Stock ABI surface (`com/xiaomi/xmsf/`) that cannot move due to external component names
- Bridge/compat glue

Target: ≤60 files in `:xmsf:shell` (down from 167 at current HEAD).

## Non-Goals

- Do NOT extract `com.xiaomi.*` vendor protocol classes into a separate module.
  They are deeply intertwined with xmsf's stock-compatible component names.
  The `:vendor` module already isolates them at the Gradle level.
- Do NOT split `:manager:ui` further. Its public boundaries are already explicit via
  `:manager:contract` and `:manager:client`.

## Success Metrics

| Metric | Before | After Phase 4 |
|--------|--------|---------------|
| xmsf shell Kotlin main count | 167 at current HEAD | ≤60 |
| xmsf shell direct project deps | reduced by runtime-core extraction | ≤5 (platform, stock, app, bridge, compat) |
| Incremental compile time (touch 1 file in runtime) | ~45s | ~15s |
