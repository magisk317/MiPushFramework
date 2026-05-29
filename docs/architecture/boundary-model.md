# MiPushFramework Boundary Model

## Summary

MiPushFramework is a system-package-compatible app split into explicit Gradle modules:

1. **xmsf**
   - Product-owned `com.xiaomi.xmsf` application, manifest entrypoints, UI, settings, stock
     compatibility surfaces, notification publish, runtime adapters, and Xposed-facing bridges.
   - Compatibility-sensitive package/component names are preserved here when external callers expect
     stock XMSF names.

2. **core**
   - Product-owned, platform-neutral runtime contracts and models (e.g. `PushRuntimeContract`,
     `PushRuntimeComponents`, `RegistrationThrottle`). It must stay free of Android framework
     dependencies so it can hold the shared routing/registration/notification-accounting types
     without depending on app UI code.
   - The Android-coupled runtime spine (`PushRuntime` and its stores) lives in
     `runtime-android-core`, which depends on `core` and uses `android.*` APIs. `core` and
     `runtime-android-core` use distinct packages (`...runtime.core` vs `...runtime.android`); do
     not reintroduce a shared package across the two modules.

3. **legacy**
   - Vendored Xiaomi push/runtime/network/telemetry stacks that are packaged into the app but are
     not the desired long-term feature layer.
   - Typical prefixes include `com.xiaomi.channel.*`, `com.xiaomi.network.*`,
     `com.xiaomi.smack.*`, `com.xiaomi.slim.*`, `com.xiaomi.clientreport.*`,
     `com.xiaomi.stats.*`, `com.xiaomi.tinyData.*`, and retained
     `com.xiaomi.push.service.*` runtime code.

4. **pinned / protocol**
   - Protocol and serialization layers that should be treated like generated or frozen source.
   - `pinned` is the dependency used by runtime modules for the currently packaged frozen protocol
     surface; `protocol` remains a parallel protocol module and should not grow business behavior.
   - Typical prefixes include `org.apache.thrift.*`, `com.google.protobuf.micro.*`,
     `com.xiaomi.xmpush.thrift.*`, `com.xiaomi.push.protobuf.*`, and
     `com.xiaomi.push.thrift.*`.

5. **common / mipush / xposed / uikit**
   - `common` holds shared app/runtime utilities and persistence models.
   - `mipush` holds client-facing SDK compatibility code.
   - `xposed` holds hook-side integration and must avoid depending on app-process-only state.
   - `uikit` holds reusable Compose UI building blocks.

Device dumps and platform jars are reference inputs only. They must not enter the Gradle source
graph.

## Compatibility Constraints

- The shipped package name remains `com.xiaomi.xmsf`.
- Compatibility is intentionally reduced to the minimum set that still preserves registration,
  long-lived connection, downstream dispatch, ACK/error feedback, notification publish/click/grouping
  behavior, and stock-facing provider/service authorities.
- Internal structure may change as long as external contracts remain stable: package/component
  names, manifest entrypoints, broadcast actions, intent extras, binder contracts, and wire behavior.

## Layering Rules

- Product UI/settings code should depend on `core`, `common`, and explicit xmsf adapters, not deep
  legacy/protocol packages.
- `xmsf/src/main/java/io/github/magisk317/mipush/service/runtime` and
  `xmsf/src/main/java/io/github/magisk317/mipush/bridge` are the allowed adapter areas for direct
  legacy/protocol interaction.
- `verifyModuleBoundaries` is wired into `check` and scans UI/settings/viewmodel source roots for
  new deep Xiaomi imports. Existing debt is listed in `scripts/module_boundary_baseline.txt`; new
  entries should be moved behind a runtime/bridge adapter unless the baseline update is a deliberate
  compatibility exception.
- `legacy` may depend on frozen protocol types, but new product behavior should not be added there
  unless it is preserving a stock runtime contract.
- `pinned` and `protocol` changes must be compatibility-preserving and non-creative.
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
- `common/.../utils/LogBundleExporter` is retained only for reusable private-file export helpers and
  does not attempt `su` fallback. Root-only collection belongs in the xmsf exporter.

## Public Interfaces

- **`RootAccessFacade`**: Query cached state, request authorization explicitly, refresh only when
  already authorized, execute root shell commands. Located at
  `xmsf/src/main/java/io/github/magisk317/mipush/platform/support/RootAccessFacade.kt`.
- **`BoundedShellRunner`**: Execute ordinary or root shell with unified timeout and result structure.
- **`RuntimeSettingsAdapter`**: Route UI/settings operations for XMPP host, forced registration,
  service foregrounding, and similar runtime actions through an adapter instead of calling legacy
  runtime directly.

## Current Architecture Debts

- The configuration stack still exists in both `common/.../configurations` and `xmsf/.../utils`.
  Treat the xmsf stack as the active notification/runtime path for now; do not change one side
  without checking whether the other side needs an equivalent fix. Runtime behavior must be covered
  by contract tests that load JSON through the active xmsf parser and then apply it to a
  `XmPushActionContainer`.
- `ConfigCenter.loadConfigurations()` remains asynchronous for UI callers. Code paths that need a
  deterministic reload can use `loadConfigurationsNow(...)`.
- `pinned` and `protocol` have overlapping packages. Keep using existing module dependencies unless
  a dedicated protocol consolidation is planned.
- `uikit` remains source-owned outside this repository. When embedded in a parent build, the desired
  next step is parent-version-catalog first with standalone fallback, but this repo does not change
  the `uikit` source checkout as part of the architecture boundary work.

## Build And Verification

```bash
./gradlew verifyModuleBoundaries   # Check import boundaries
./gradlew :xmsf:testNormalDebugUnitTest
./gradlew :common:check :core:testDebugUnitTest :mipush:testDebugUnitTest
./gradlew assembleDebug -PbuildSplits=true -PbuildTs=$(date +%Y%m%d%H%M%S)
```

Boundary baseline is maintained at `scripts/module_boundary_baseline.txt` and validated by
`scripts/verify_module_boundaries.sh`.

## Refactor Record

The package-by-package Java-to-Kotlin port and old `push/` split are complete. The retained history
and ownership notes are recorded in `docs/architecture/push-module-split.md`.
