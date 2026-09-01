# XMSF Package Boundaries

`:xmsf` is the installable Android application for `com.xiaomi.xmsf`; `:xmsf:shell` is its runtime
library surface, while `:xmsf:runtime` owns the independently compilable runtime core. The
application module packages the shell manifest and stock-compatible entrypoints. That does not
mean every product concern may depend on every other concern inside the application.

## Current layers

```text
core policies and contracts  <-  common compatibility/adapters
        ^
runtime/data and runtime/store  <-  push/pipeline/runtime execution
        ^                         \
notification policy adapters  <-  notification construction
        ^
manager/runtime read/write adapters  <-  ManagerRuntimeService
```

The arrows describe dependency direction. `manager/runtime` may consume notification and runtime
adapters because it is the Binder-facing composition layer. Persistence and replay data code must
not import concrete notification classes.

## Protocol ownership

`common` contains shared infrastructure, compatibility facades, and notification contracts;
platform-neutral configuration and notification policies live in `:core`. Manager application values
live in `:manager:port` and Binder DTOs live in `:manager:contract`.
`common` does not contain Thrift types or depend on `pinned`. Protocol serialization,
registration-secret resolution, and payload decoding are XMSF runtime concerns under
`platform/support` and `utils`.
The manager debug formatter emits payload metadata only as a local fallback. When the runtime
gateway is available, manager requests decoded JSON through the Binder write protocol; the XMSF
runtime remains the authoritative detail decoder.

The boundary script rejects both a `common -> pinned` Gradle dependency and Thrift imports under
`common/src`. This keeps manager/settings consumers independent of the frozen wire protocol while
preserving the existing XMSF runtime behavior.

## User-scoped identity

Runtime storage now carries `user_id` on both `EVENT` and `REGISTERED_APPLICATION`, with a
composite `(user_id, pkg)` identity for registered applications. Existing call sites use the
current Android user through the storage facade, so package-only callers do not silently read a
different profile. Version 7 assigns legacy rows to the owning user when that user's database is
first opened. Manager application and event read models, Binder DTOs, and their wire schema
versions now carry the user identity as well. XSpace/root authorization and device-level
cross-user behavior remain runtime verification work; protocol fields alone are not evidence that
the two Android users have been validated on a phone.

## Enforced rule

`runtime/data` exposes notification state through the shared
`NotificationAvailabilityReader` contract in `common`. The concrete
`XmsfNotificationAvailabilityReader` is bound in `xmsf` Koin and is the only layer that knows how
to combine installed-package checks with channel state. `scripts/verify_module_boundaries.sh`
rejects a direct `runtime/data -> notification` import.

The notification boundary was the first small extraction; the runtime-core boundary is now the first
large-block extraction. Future splits should follow the same rule: move a cohesive domain, keep the
dependency graph acyclic, add a forbidden-edge check, and verify that manifest aggregation remains
unchanged.

## Rejected direction

Do not move `NotificationController`, stock providers, or manager Binder entrypoints into a new
module merely because their package names look separable. Those classes carry Android ABI and
manifest obligations. A Gradle split is justified only after the package contract is explicit and
the installable `:xmsf` artifact still owns the external surface.
