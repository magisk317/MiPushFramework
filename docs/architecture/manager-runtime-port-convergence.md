# Manager–runtime port convergence

## Status and scope

This is a design and inventory checkpoint. It intentionally makes **no** source move, Gradle
edge change, Binder change, or AIDL/wire-schema change. The current direct edge is:

```text
xmsf:runtime -> manager:application
```

The target is to let the runtime consume the small, platform-neutral manager-domain value model
without depending on the Android-aware manager application ports. This is not a proposal to make
the XMSF shell independent of manager application adapters in the same change.

## Inventory (reviewed 2026-04-02)

`xmsf/runtime/build.gradle.kts` directly depends on `:manager:application`. Its production import
surface is exactly one type, `ManagerApplication`, at these three paths:

| Runtime path | Use |
| --- | --- |
| `xmsf/runtime/.../ManagerApplicationReadModels.kt` | Maps stored/package-manager snapshots to the value model. |
| `xmsf/runtime/.../ManagerApplicationRuntimeReader.kt` | Read-only page/detail projection, filtering, pagination, and byte budgeting. |
| `xmsf/runtime/.../ManagerApplicationReadPolicy.kt` | Comparator, query/filter predicates, and diagnostic inference. |

`ManagerApplication` itself is a serialized data value with primitive/string/collection fields and
nested integer constants. It has no `Context`, `Uri`, `Intent`, `Parcel`, Binder, filesystem, or
other Android API dependency.

Do **not** include `xmsf:shell` in this first migration. The shell has a much broader
`manager:application` import surface (Koin bindings, write execution, permissions, root, config,
logging, event replay, and runtime adapters). Those types include intentional Android-aware ports,
such as `Context`/`Uri` arguments in `ManagerApplicationGateway`, `ManagerConfigSyncGateway`,
`ManagerLogGateway`, and `ManagerPermissionGateway`. Moving them together would combine a simple
value-model extraction with Binder, UI, root, storage, and side-effect ownership changes.

## Chosen destination and compatibility rule

Create a small `:manager:port` module for manager-domain values that are shared by manager
application and XMSF runtime but are not AIDL contracts. Initially it should own only the existing
`io.github.magisk317.mipush.manager.application.ManagerApplication` source.

The package and class name must remain unchanged when the source is moved. This preserves Kotlin
and Java binary identity for consumers, generated serialization identity, nested constant owner
names, and the existing mapping/Binder behavior. A Kotlin `typealias` is not an acceptable
compatibility bridge because it does not retain the old JVM class. No field, default, annotation,
or constant change is allowed in the extraction commit.

`manager:port` must contain no Android framework, AndroidX, AIDL, Parcel/Parcelable, Binder,
filesystem, UI, root, or transport implementation. It may use the already-reviewed serialization
runtime required by `ManagerApplication`.

## Isolated migration sequence

1. **Characterize before moving.** Preserve and run the existing runtime reader, page-token, and
   policy tests. Add a focused serialization/default-value compatibility test only if one is absent;
   it must compare the current and moved class behavior without changing the data schema.
2. **Create `:manager:port`.** Add it to `settings.gradle.kts` and give it only the Kotlin/
   serialization dependencies needed by `ManagerApplication`. Move the source verbatim, retaining
   its FQCN.
3. **Rewire compile edges as one batch.** `manager:application` exposes `:manager:port` as `api`
   because its public gateways use `ManagerApplication`; `xmsf:runtime` replaces its
   `:manager:application` implementation dependency with `:manager:port`. Add a direct port
   dependency only to any consumer whose compile classpath requires the exposed type. Do not rely
   on accidental transitive visibility.
4. **Do not touch runtime behavior.** Leave `ManagerApplicationRuntimeReader`, page tokens,
   filtering/sorting, package-manager probes, current-user filtering, payload budgeting, service
   Binder execution, and DTO mapping unchanged. Leave `ManagerRuntimeClient` timing/session
   behavior unchanged.
5. **Add the post-migration guard.** Extend `verify_module_boundaries.sh` only after the edge is
   gone: reject `manager.application` imports and the `:manager:application` Gradle dependency in
   `xmsf:runtime`; require `:manager:port`; scan `manager:port` for Android/Binder/AIDL leakage.
   The guard must not be enabled with a baseline exception, otherwise it cannot prove convergence.

## Follow-up candidates (separate review)

After the first edge is removed and validated, review these independently rather than bulk-moving
models:

- `ManagerConnectionSnapshot` and `ManagerRuntimeEnvironmentSnapshot`, which are currently
  consumed by shell service/settings paths rather than `xmsf:runtime`.
- `MockReplayOutcome`, which crosses shell runtime-data/notification paths and needs stock replay
  ordering review.
- Any gateway interface only after splitting its Android host arguments from its pure command/value
  contract.

AIDL DTOs, `ManagerProtocol`, `WireParcel`, generated Stub ownership, and append-only transaction
semantics remain in `:manager:contract`; they are not candidates for `:manager:port`.

## Required validation for the future migration commit

```bash
./gradlew --no-build-cache :xmsf:runtime:testDebugUnitTest :manager:application:test \
  :manager:port:test verifyModuleBoundaries
./gradlew --no-build-cache reportGodFiles verifyGodFileLimits verifyModuleBoundaries
git diff --check
```

Additionally inspect the Gradle dependency graph to prove there is no
`xmsf:runtime -> manager:application` path. If the port module is intentionally non-Android/KMP,
use its corresponding JVM/common test task instead of `:manager:port:test`.

## Deferred independent work: Koin host identity

`ManagerDependencies` currently rejects an APP_SHELL/REMOTE_HOST mode switch after its own
bootstrap state is set, but accepts any non-null Koin `GlobalContext` as a host. Do not alter this
in the port migration or any Binder/wire change. A separate change should introduce a host-owned
marker/token, characterize foreign/partially initialized Koin hosts, and add both-direction
contamination tests. It must retain the existing Application-owned entry points and startup order:
`MiPushHostApp.onAppDependenciesStarted()` for APP_SHELL and `mipush/App.onCreate()` for
REMOTE_HOST.
