# XMSF Package Boundaries

`xmsf` is intentionally still one Android library because its manifest and stock-compatible
entrypoints are packaged by `app`. That does not mean every product concern may depend on every
other concern inside the library.

## Current layers

```text
common contracts and models
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

`common` contains manager DTOs, configuration primitives, and notification contracts, but it does
not contain Thrift types or depend on `pinned`. Protocol serialization, registration-secret
resolution, and payload decoding are XMSF runtime concerns under `platform/support` and `utils`.
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

This is deliberately the first small extraction rather than a speculative Gradle split. The next
extractions should follow the same pattern: identify a stable contract, move the implementation to
an adapter, add a forbidden-edge check, then consider a module boundary only after the dependency
graph is acyclic and the manifest aggregation remains unchanged.

## Rejected direction

Do not move `NotificationController`, stock providers, or manager Binder entrypoints into a new
module merely because their package names look separable. Those classes carry Android ABI and
manifest obligations. A Gradle split is justified only after the package contract is explicit and
the installable `app` artifact still owns the external surface.
