# MiPushFramework Boundary Model

## Summary

MiPushFramework is a system-package-compatible app split into explicit Gradle modules:

1. **xmsf**
   - Product-owned `com.xiaomi.xmsf` runtime library, manifest entrypoints, UI, settings, stock
     compatibility surfaces, notification publish, runtime adapters, and Xposed-facing bridges.
     It is packaged into the device-installable application by `app`, not installed by itself.
   - Compatibility-sensitive package/component names are preserved here when external callers expect
     stock XMSF names.

2. **core**
   - Product-owned, platform-neutral runtime contracts and models (e.g. `PushRuntimeContract`,
     `PushRuntimeComponents`, `RegistrationThrottle`). It must stay free of Android framework
     dependencies so it can hold the shared routing/registration/notification-accounting types
     without depending on app UI code.
   - The Android-coupled runtime spine (`PushRuntime` and its stores) lives in `xmsf` under the
     `io.github.magisk317.mipush.runtime.android` package, depends on `core`, and uses `android.*`
     APIs. (It previously lived in a dedicated `runtime-android-core` module; that module had a
     single consumer — `xmsf` — and was folded back into `xmsf`. `core` keeps the
     `...runtime.core` package and the spine keeps `...runtime.android`; do not reintroduce a
     shared package between them.)

3. **vendor**
   - Vendored Xiaomi push/runtime/network/telemetry stacks that are packaged into the app but are
     not the desired long-term feature layer. The module is named `vendor` (not `legacy`) because
     this is active, load-bearing runtime (it carries the long-connection `XMPushService`); the
     name describes its provenance and frozen edit policy, not that it is dead.
   - Typical prefixes include `com.xiaomi.channel.*`, `com.xiaomi.network.*`,
     `com.xiaomi.smack.*`, `com.xiaomi.slim.*`, `com.xiaomi.clientreport.*`,
     `com.xiaomi.stats.*`, `com.xiaomi.tinyData.*`, and retained
     `com.xiaomi.push.service.*` runtime code.

4. **pinned**
   - Protocol and serialization layer that should be treated like generated or frozen source.
   - `pinned` is the single packaged frozen protocol surface consumed by runtime modules; it must
     not grow business behavior. (The previously parallel `protocol` module was removed once it was
     confirmed to be runtime-redundant with `pinned` and `vendor`.)
   - Typical prefixes include `org.apache.thrift.*`, `com.google.protobuf.micro.*`,
     `com.xiaomi.xmpush.thrift.*`, `com.xiaomi.push.protobuf.*`, and
     `com.xiaomi.push.thrift.*`.

5. **common / settings / diagnostics / mipush / xposed / magisk-ui-kit**
   - `common` holds shared app/runtime utilities and persistence models.
   - `settings` holds DataStore preference extensions and shared preference repository surfaces.
   - `diagnostics` holds the shared diagnostic archive / LogBundle export pipeline used by app and
     runtime; keep product-specific collection in parent modules, reuse archive/sanitize here or via
     `magisk-xposed-kit:diagnostics` where already wired.
   - `mipush` holds client-facing SDK compatibility code (separate package from `com.xiaomi.xmsf`).
   - `xposed` holds hook-side integration and must avoid depending on app-process-only state.
   - `magisk-ui-kit` holds reusable Compose UI building blocks.
   - The manager main-screen scroll chrome state is shared across several routes, but that shared
     state belongs to manager-level navigation behavior rather than ui-kit. Keep route-reset policy
     in `manager/MainScreen` and keep ui-kit scaffolds defensive against transient negative offsets.

6. **app / manager / mipush**
   - `app` is the thin application shell that produces the device-installable `com.xiaomi.xmsf`
     runtime APK. `MiPushHostApp` must stay a no-manager shell: no `ManagerDependencies` bootstrap
     and no `:manager` dependency.
   - `mipush` is the standalone manager host package. It owns manager UI process startup via
     `ManagerDependencies.startAsRemoteHost()` / `ensureStarted()` (ensure always means remote host).
     Manager reaches XMSF only through signature-authenticated Binder (`manager-api` /
     `ManagerRuntimeClient`).
   - `manager` is a UI/library surface packaged by `:mipush`, not by `:app`. Do not move manager
     bootstrap into `xmsf` Koin modules or back into `MiPushHostApp`.
   - `xmsf` remains an Android library module. Prefer `:app:assembleNormalDebug` when validating the
     installable runtime; `:xmsf:assembleNormalDebug` only packages the library surface.

Device dumps and platform jars are reference inputs only. They must not enter the Gradle source
graph.

## Compatibility Constraints

- The shipped package name remains `com.xiaomi.xmsf`.
- Compatibility is intentionally reduced to the minimum set that still preserves registration,
  long-lived connection, downstream dispatch, ACK/error feedback, notification publish/click/grouping
  behavior, and stock-facing provider/service authorities.
- Internal structure may change as long as external contracts remain stable: package/component
  names, manifest entrypoints, broadcast actions, intent extras, binder contracts, and wire behavior.
- Stock-facing compatibility work must also preserve caller identity, Bundle value types, result
  code type, persistence effects, and the real runtime consumer. The evidence and current limits
  are maintained in `docs/architecture/stock-dump-contract-audit-2026-07.md`.

## Layering Rules

- Product UI/settings code should depend on `core`, `common`, and explicit xmsf adapters, not deep
  vendor/pinned packages.
- `xmsf/src/main/java/io/github/magisk317/mipush/service/runtime` and
  `xmsf/src/main/java/io/github/magisk317/mipush/bridge` are the allowed adapter areas for direct
  vendor/pinned interaction.
- Stock ABI and Provider adaptation belongs in product-owned `xmsf` surfaces such as
  `com.xiaomi.xmsf.stock` and named Binder facades. Keep raw dump sources out of the build graph,
  and do not move product behavior into `vendor` solely to mimic a stock package name.
- Restoring a stock `signatureOrSystem` declaration does not authorize an arbitrary caller. Keep
  caller package/UID checks on sensitive methods, including notification-broker and provider paths.
- The handwritten `com.xiaomi.micloudsdk` Binder types are a narrow compatibility boundary. Match
  their descriptor, transaction/reply ABI, and `Intent` in/out behavior without making the app
  depend on a broader proprietary MiCloud SDK.
- `verifyModuleBoundaries` is wired into `check` and now scans `manager`, `settings`, and the
  xmsf app-facing roots for new deep Xiaomi imports, direct non-string FQCN references, and
  class-like deep Xiaomi string references. In `manager` and `settings` it also flags
  direct imports from `io.github.magisk317.mipush.app.*`, because that namespace belongs to xmsf
  runtime ownership rather than the manager UI surface. The same scan also rejects string-literal
  references to deep `com.xiaomi.*` classes under `manager`/`settings`, so reflection cannot be
  used there to tunnel around the import boundary for runtime probes or compatibility checks.
  The baseline file at `scripts/module_boundary_baseline.txt` is currently empty of exceptions.
  New entries should be moved behind a runtime/bridge adapter or explicit shared contract unless
  the baseline update is a deliberate compatibility exception. The check also fails stale baseline
  entries, so resolved debt must be removed from the baseline in the same change. The same task also
  rejects direct `manager`/`settings` Gradle
  dependencies on `vendor`, `xmsf`, or `pinned`; those relationships must be expressed through
  shared contracts instead.
- `vendor` may depend on frozen protocol types from `pinned`, but new product behavior should not be
  added there unless it is preserving a stock runtime contract.
- `pinned` changes must be compatibility-preserving and non-creative.
- `manager` consumes `core` (`ConnectionStatus`) at runtime. Broadcast action strings that used to
  leak in through `vendor` are now exposed through `common` (`PushServiceBroadcastActions`) so the
  UI layer no longer needs a direct `vendor` import just to talk to the runtime messenger.
- Platform/system reference artifacts remain outside the build graph.

## Root, Shell, And Logs

- App-process root execution goes through `RootAccessFacade` and `BoundedShellRunner`. Callers must
  choose cached refresh versus explicit authorization request instead of letting incidental shell
  commands trigger a root prompt.
- Hook-process root execution stays independent and uses its own bounded runner in `xposed`; it must
  not depend on app-process singletons.
- `xmsf/.../utils/LogBundleExporter` is the canonical app log exporter. It owns app-specific JSONL
  selection, old text-log cleanup, redaction, optional root-only LSPosed collection, and share intent
  creation.
- The former `common/.../utils/LogBundleExporter` facade was removed. Reusable archive and
  sanitization primitives live in the shared diagnostics/Xposed kits; app-specific file selection,
  optional root-only LSPosed collection, and share-intent creation stay in the xmsf exporter.

## Public Interfaces

- **`RootAccessFacade`**: Query cached state, request authorization explicitly, refresh only when
  already authorized, execute root shell commands. Located at
  `xmsf/src/main/java/io/github/magisk317/mipush/platform/support/RootAccessFacade.kt`.
- **`BoundedShellRunner`**: Execute ordinary or root shell with unified timeout and result structure.
- **`RuntimeSettingsAdapter`**: Route UI/settings operations for XMPP host, forced registration,
  service foregrounding, manager environment snapshots, and similar runtime actions through an
  adapter instead of calling vendor runtime directly.

## Current Architecture Debts

- `KeepAliveRuntimeAdapter` deliberately implements a reduced polling binder rather than stock's
  process-observer runtime. Strategy updates remain inert until ServiceBox resolves
  `KASwitch=142`; `OnetrackSwitch=140` is observed/persisted but intentionally does not re-enable
  stock OneTrack behavior while telemetry is disabled. Its JSON parser and bind/unbind behavior
  have deterministic tests, but cadence and lifecycle behavior still require device evidence. Do
  not describe it as a full stock keep-alive implementation.
- System-only permissions and notification/XSpace behavior are similarly bounded by platform
  policy. Unit/build evidence proves our adapters; installed-device `dumpsys` and hook evidence are
  required for final visible-UI claims.

- The configuration stack lives only in `xmsf/.../utils` (`Configurations`, `ConfigurationsLoader`,
  `ConfigValueConverter`, `IconConfigurations`, `PackageConfig`). The duplicate, unused copies that
  previously sat under `common/.../configurations` were removed. `common/.../configurations` now
  keeps only the genuinely shared primitives consumed across modules (`ConfigJson*`, `Lisp`,
  `RegSecUtils`, `XMPushUtils`); do not reintroduce a second copy of the runtime config stack there.
  Runtime behavior must be covered by contract tests that load JSON through the active xmsf parser
  and then apply it to an `XmPushActionContainer`.
- `ConfigCenter.loadConfigurations()` remains asynchronous for UI callers. Code paths that need a
  deterministic reload can use `loadConfigurationsNow(...)`.
- Manager UI is package-hosted by `:mipush`, not by `:app`/`xmsf`. XMSF still owns runtime
  gateways and Binder service implementations inside the `com.xiaomi.xmsf` process; manager-side
  code consumes them through `manager-api` / `ManagerRuntimeClient` rather than sharing an in-process
  Koin container with XMSF. Runtime environment diagnostics flow through
  `ManagerRuntimeEnvironmentSnapshot` instead of reflective `com.xiaomi.*` lookups.
- Production manager bootstrap is remote-host only: `:mipush` Application / launcher / widget call
  `ManagerDependencies.startAsRemoteHost(...)` / `ensureStarted(...)`. Manager UI
  entrypoints must not own a second bootstrap world, must not start the XMSF Koin host, and must
  not reintroduce bundled-era host-Koin `ManagerDependencies.start()` as a second bootstrap world.
  UI entrypoints call `ensureStarted()`; host Application keeps `startAsRemoteHost()`.
- Manager data plane **is** remote-primary (`Remote*Source` → ViewModel). `Comparing*` /
  fake `InProcess*` wrappers are migration scaffolding and should not be re-expanded.
- Manager main chrome collapse/expand is intentionally shared across `EventList`, `ApplicationList`,
  `Configurations`, and `Settings`, while `Overview` keeps its own always-visible treatment. A June
  2026 regression showed that route switches during half-expanded animation can leak a negative
  `headerOffsetY` into the next page: if the shared state is only shown and not reset to the top,
  short pages such as Settings can render with clipped top content, and `OverlayHeaderScaffold`
  can even crash Compose with `Padding must be non-negative`. Future refactors must preserve both
  route-time state reset (`animateToTop()`) and non-negative padding guards in the scaffold.
- The following routes are treated as resolved traps and should not be reintroduced:
  - do not copy manager bindings (`SettingsManager`, manager ViewModels, manager Koin module
    contents) into `xmsfCoreKoinModule`; `xmsf` must not depend on `manager`
  - do not make `MainActivity` or other manager UI entrypoints create a second Koin host or own
    manager bootstrap; production startup belongs to `:mipush` remote-host bootstrap only
  - do not reintroduce manager UI bootstrap into `MiPushHostApp` / `:app`
  - do not load manager UI modules from the XMSF `:services` subprocess
  - do not use reflective `com.xiaomi.*` lookups in manager/settings as a substitute for runtime
    adapter contracts
- The previously parallel `protocol` module (a compile-only superset that duplicated `pinned`'s
  thrift/protobuf types and `vendor`'s `com.xiaomi.channel.commonutils.*`) was removed. Runtime
  modules now compile against `pinned` for wire types and `vendor` for retained runtime utilities.
- `magisk-ui-kit` remains source-owned outside this repository. When embedded in a parent build, the desired
  next step is parent-version-catalog first with standalone fallback, but this repo does not change
  the `magisk-ui-kit` source checkout as part of the architecture boundary work.

## Build And Verification

```bash
./gradlew verifyModuleBoundaries   # Check import boundaries
./gradlew check --warning-mode=all
./gradlew :xposed:detekt --console=plain
./gradlew assembleDebug -PbuildSplits=true -PbuildTs=$(date +%Y%m%d%H%M%S)
./gradlew qualityGateKoverVerify   # Explicit coverage gate
```

The boundary baseline file is maintained at `scripts/module_boundary_baseline.txt` and validated by
`scripts/verify_module_boundaries.sh`; it is allowed to contain only comments when no exceptions
remain.

Kover is intentionally loaded only for explicit `qualityGateKover*` tasks, direct Kover tasks, or
when `-PenableKover=true` is supplied. The ordinary `check` path stays on Detekt, unit tests, Android
checks, and `verifyModuleBoundaries`; this keeps Kover's current Gradle 10 deprecation warning out of
the default verification path while still preserving an opt-in coverage gate.

## Refactor Record

The package-by-package Java-to-Kotlin port and old `push/` split are complete. The retained history
and ownership notes are recorded in `docs/architecture/push-module-split.md`.


## Data-plane idiom (authoritative)

Chosen production shape after the app split:

| Concern | API | Notes |
| --- | --- | --- |
| Read | `Remote*Source` + `ManagerRuntimeClient` | Suspend; map missing/binding/denied to typed statuses |
| Write | `RemoteWriteSupport.execute` (suspend) | Preferred from ViewModels / coroutines; allowlisted keys |
| Write bridge | `RemoteWriteSupport.executeBlocking` | Only for remaining sync `Manager*Gateway` façades |
| Legacy façade | `Manager*Gateway` → `RemoteManager*Gateway` | Same Binder underneath; prefer Source/Client in new code |
| Test harness | `Comparing*` + `Gateway*` | **Test source set only**; not registered in production Koin |

Do not reintroduce in-process primary reads in `:mipush`. Do not bootstrap manager from `:app`.

