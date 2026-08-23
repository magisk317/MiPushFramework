# XMSF Module Decomposition Roadmap

> Created: 2026-08-23
> Status: planning document; no code changes authorized by this file alone.
> Source evidence: package tree analysis + cross-package import audit at current HEAD.

## Current State

`:xmsf` contains 239 Kotlin files across two namespace families:

| Namespace | Files | Role |
|-----------|-------|------|
| `com.xiaomi.*` | 55 | Stock XMSF compatibility surface (frozen ABI) |
| `io.github.magisk317.mipush.*` | 184 | Product code |

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
:platform          ← shared contracts, logging, DTOs, support utilities
    ↑
:core              ← existing (already extracted)
    ↑
:xmsf-stock        ← com.xiaomi.* frozen ABI surface (~55 files)
    ↑
:xmsf-notification ← notification policy, adapters, construction
    ↑
:xmsf-runtime      ← runtime state, stores, data access
    ↑
:xmsf-push         ← push pipeline, connection management, hooks
    ↑
:xmsf              ← manifest entrypoints, services, receivers, DI composition
```

## Phased Migration Plan

### Phase 0: Extract `:xmsf-platform`

Move `io.github.magisk317.mipush.platform.support` and `io.github.magisk317.mipush.utils`
into a new `:xmsf-platform` Android library.

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

### Phase 1: Extract `:xmsf-notification`

Move `io.github.magisk317.mipush.notification` into a new library module.

- **Why second:** Only 9 external consumers, clear domain boundary, already documented
  in `xposed-notification-boundary.md`.
- **Risk:** Medium. Notification code interacts with Xposed hooks and stock bridges;
  must verify fallback paths still work without the hook present.
- **Validation:** Existing notification unit tests pass; device smoke test for
  notification publish path.
- **Estimated effort:** Medium.

### Phase 2: Extract `:xmsf-runtime`

Move `io.github.magisk317.mipush.runtime.*` (state, stores, data access) into a new
library. This includes Room entities, DAOs, and the runtime store facade.

- **Why third:** 26 external consumers, but they are primarily service and push-pipeline
  code that will eventually move into `:xmsf-push`. Extracting runtime first creates
  a stable storage API for both.
- **Risk:** High. Runtime state is the most sensitive area; schema migrations and
  user-scoped identity must be preserved exactly.
- **Prerequisite:** KMP shadow database migration plan validated on device.
- **Estimated effort:** Large.

### Phase 3: Extract `:xmsf-push`

Move `io.github.magisk317.mipush.push.*` (pipeline, connection management, hooks) into
a new library.

- **Why last among product splits:** Push pipeline depends on notification (Phase 1)
  and runtime (Phase 2). It must come after both.
- **Risk:** High. Connection lifecycle, reconnect logic, and packet handling are
  behavior-critical.
- **Validation:** Long-connection stability test on device; push delivery E2E.
- **Estimated effort:** Large.

### Phase 4: Shrink `:xmsf` to shell

After Phases 0–3, remaining `:xmsf` content should be limited to:
- Manifest entrypoints (services, receivers, providers)
- DI composition root (`app/`)
- Stock ABI surface (`com/xiaomi/xmsf/`) that cannot move due to external component names
- Bridge/compat glue

Target: ≤60 files in `:xmsf` (down from 239).

## Non-Goals

- Do NOT extract `com.xiaomi.*` vendor protocol classes into a separate module.
  They are deeply intertwined with xmsf's stock-compatible component names.
  The `:vendor` module already isolates them at the Gradle level.
- Do NOT split `:manager` further. It already has clean boundaries via
  `manager-api` / `manager-client`.

## Success Metrics

| Metric | Before | After Phase 4 |
|--------|--------|---------------|
| xmsf file count | 239 | ≤60 |
| xmsf direct project deps | 10 | ≤5 (platform, stock, app, bridge, compat) |
| Incremental compile time (touch 1 file in runtime) | ~45s | ~15s |
