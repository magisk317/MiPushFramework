# Manager–runtime port convergence

## Status and scope

The `:manager:port` boundary is complete as of 2026-09-01. The runtime and shell consume the
small, platform-neutral manager value model without depending on the Android-aware manager
application ports for those values. This boundary makes no manager Binder or AIDL/wire-schema
change; separate stock ABI compatibility additions remain outside this port extraction. The
current direct edges are:

```text
xmsf:runtime -> manager:port
xmsf:shell -> manager:port
manager:application -> manager:port
```

The XMSF shell still depends on manager application adapters where its composition root requires
them; that boundary was not widened by this migration.

## Inventory (reviewed 2026-09-01)

The runtime, shell, and manager application declare `:manager:port` directly where their
production signatures use port-owned values. The runtime import surface remains
`ManagerApplication` at these three paths:

| Runtime path | Use |
| --- | --- |
| `xmsf/runtime/.../ManagerApplicationReadModels.kt` | Maps stored/package-manager snapshots to the value model. |
| `xmsf/runtime/.../ManagerApplicationRuntimeReader.kt` | Read-only page/detail projection, filtering, pagination, and byte budgeting. |
| `xmsf/runtime/.../ManagerApplicationReadPolicy.kt` | Comparator, query/filter predicates, and diagnostic inference. |

The shell and manager application also consume `ManagerRuntimeEnvironmentSnapshot` and
`ManagerConnectionSnapshot` through runtime settings and manager action adapters. They remain
value-only types even though their package stays under `manager.application` for compatibility.

`ManagerApplication` itself remains a serialized data value with primitive/string/collection
fields and nested integer constants. It has no `Context`, `Uri`, `Intent`, `Parcel`, Binder,
filesystem, or other Android API dependency. The port test covers JSON round-trip behavior,
defaults, and forward-compatible unknown fields.

The port also owns the pure application, event, diagnostic, and log-result models from
`ManagerModels.kt`. `ManagerEvent` keeps its transient payload out of JSON while preserving
content-based byte-array equality; event replay behavior remains in the XMSF shell adapter.

The XMSF shell still has a much broader
`manager:application` import surface (Koin bindings, write execution, permissions, root, config,
logging, event replay, and runtime adapters). Those types include intentional Android-aware ports,
such as `Context`/`Uri` arguments in `ManagerApplicationGateway`, `ManagerConfigSyncGateway`,
`ManagerLogGateway`, and `ManagerPermissionGateway`; those remain in `:manager:application`.
Only independently pure values are owned by `:manager:port`; replay outcome values do not pull
notification or Android behavior into the port.

## Ownership and compatibility rule

The small `:manager:port` module owns manager-domain values shared by manager application and XMSF
runtime but not AIDL contracts. It owns `ManagerApplication`, `ManagerRuntimeEnvironmentSnapshot`,
`ManagerConnectionSnapshot`, `MockReplayOutcome`, and the pure models from `ManagerModels.kt`.

The package and class name remains unchanged. This preserves Kotlin
and Java binary identity for consumers, generated serialization identity, nested constant owner
names, and the existing mapping/Binder behavior. A Kotlin `typealias` is not an acceptable
compatibility bridge because it does not retain the old JVM class. No field, default, annotation,
or constant change is allowed in the extraction commit.

`manager:port` must contain no Android framework, AndroidX, AIDL, Parcel/Parcelable, Binder,
filesystem, UI, root, or transport implementation. It may use the already-reviewed serialization
runtime required by `ManagerApplication`.

## Current boundary

`:manager:port` owns the serialized `ManagerApplication`, runtime snapshot values, and the pure
`MockReplayOutcome` terminal values.
`:xmsf:runtime`, `:xmsf:shell`, and `:manager:application` declare the direct dependency where
their adapters or gateway signatures use those values. The boundary guard rejects a direct
`xmsf:runtime -> manager:application` edge and rejects Android, Binder, AIDL, filesystem, or
transport references in `:manager:port`.

## Follow-up candidates (separate review)

The remaining independent candidate is any gateway interface, and it must be reviewed separately
after splitting its Android host arguments from its pure command/value contract.

AIDL DTOs, `ManagerProtocol`, `WireParcel`, generated Stub ownership, and append-only transaction
semantics remain in `:manager:contract`; they are not candidates for `:manager:port`.

## Validation

```bash
./gradlew --no-build-cache :xmsf:runtime:testDebugUnitTest :xmsf:shell:compileNormalDebugKotlin \
  :manager:application:test :manager:port:test verifyModuleBoundaries
./gradlew --no-build-cache reportGodFiles verifyGodFileLimits verifyModuleBoundaries
git diff --check
```

The dependency graph must contain `xmsf:runtime -> manager:port` and no direct
`xmsf:runtime -> manager:application` edge. `:manager:port:test` is the JVM compatibility test;
the Android runtime reader regression remains in `:xmsf:runtime:testDebugUnitTest`. For KMP core
policy checks, use `:core:jvmTest`; `:core:testDebugUnitTest` is not a valid task.

## Deferred independent work: Koin host identity

`ManagerDependencies` currently rejects an APP_SHELL/REMOTE_HOST mode switch after its own
bootstrap state is set, but accepts any non-null Koin `GlobalContext` as a host. Do not alter this
in the port migration or any Binder/wire change. A separate change should introduce a host-owned
marker/token, characterize foreign/partially initialized Koin hosts, and add both-direction
contamination tests. It must retain the existing Application-owned entry points and startup order:
`MiPushHostApp.onAppDependenciesStarted()` for APP_SHELL and `mipush/App.onCreate()` for
REMOTE_HOST.
