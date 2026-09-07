# MiPushFramework Boundary Model

## Summary

MiPushFramework is a system-package-compatible app split into explicit Gradle modules:

1. **xmsf**
   - Product-owned `:xmsf` Android application that produces the installable `com.xiaomi.xmsf`
     runtime. Its `:xmsf:shell` library owns manifest entrypoints, stock compatibility surfaces,
     notification publish, runtime adapters, and Xposed-facing bridges.
   - Compatibility-sensitive package/component names are preserved here when external callers expect
     stock XMSF names.

2. **core**
   - Product-owned, platform-neutral runtime contracts and models (e.g. `PushRuntimeContract`,
     `PushRuntimeComponents`, `RegistrationThrottle`). It must stay free of Android framework
     dependencies so it can hold the shared routing/registration/notification-accounting types
     without depending on app UI code.
   - The Android-coupled runtime spine (`PushRuntime` and its stores) lives in `:xmsf:runtime` under the
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
   - `common` is shrinking to shared Android infrastructure and cross-feature value types. It must not own Manager application ports, runtime-store facades, or feature policy. Persistence implementation belongs to `:xmsf:runtime:store`; neutral notification, reconnect, duplicate-message, and Zygisk configuration policies belong to `:core`.
   - `:xmsf:runtime:store` is persistence-only: Room/SQLite schemas, DAOs, migrations, row/value types, and persistence repositories. It must not become a home for runtime policy.
   - `settings` holds DataStore preference extensions and shared preference repository surfaces.
   - `diagnostics` holds the shared diagnostic archive / LogBundle export pipeline used by app and
     runtime; keep product-specific collection in parent modules, reuse archive/sanitize here or via
     `magisk-xposed-kit:diagnostics` where already wired.
   - `mipush` holds client-facing SDK compatibility code (separate package from `com.xiaomi.xmsf`).
   - `xposed` holds hook-side integration and must avoid depending on app-process-only state.
   - `magisk-ui-kit` holds reusable Compose UI building blocks.
   - The manager main-screen scroll chrome state is shared across several routes, but that shared
     state belongs to manager-level navigation behavior rather than ui-kit. Keep route-reset policy
     in `manager/ui/MainScreen` and keep ui-kit scaffolds defensive against transient negative offsets.

6. **xmsf / manager / mipush**
   - `:xmsf` is the thin application shell that produces the device-installable `com.xiaomi.xmsf`
     runtime APK. It deliberately depends on `:manager:ui`: after `MiPushFrameworkApp` starts runtime
     Koin, `MiPushHostApp.onAppDependenciesStarted()` loads manager definitions in the main process.
   - `mipush` is the standalone manager host package. Its `Application` owns manager UI process
     startup via `ManagerDependencies.startAsRemoteHost(...)`.
     Manager reaches XMSF only through signature-authenticated Binder (`:manager:contract` / `ManagerRuntimeClient`).
   - `:manager:contract` is the frozen Binder/AIDL/Parcelable wire boundary. `:manager:application` owns non-Binder Manager application ports, shared models, mock-replay result, runtime actions, and manager event-detail debug JSON formatting (`EventDebugJson`); generic JSONL encoding, redaction, and quota decisions are owned by `:core`. It directly exposes the neutral DTOs owned by `:core`, and must not depend on `common`, `vendor`, `pinned`, or `xmsf` implementations. Its ports are grouped by application/notification, configuration, events, diagnostics, permissions, and Zygisk domains. Diagnostics returns an archive path, while the Manager UI host validates that path and constructs the Android `FileProvider` share intent; `File` and `Intent` are not application-port API types. `:manager:ui` is a UI/library surface available to both hosts. Real manager Activities and the
     direct desktop launcher alias are declared by `:mipush`; `:xmsf` declares no manager activities,
     aliases, or redirect trampoline. Activity/launcher-alias/widget entrypoints never own bootstrap.
   - Do not move manager bindings into `xmsf` Koin modules. `xmsf` exposes runtime gateways and the
     post-dependency hook; the app shell chooses what to load through that hook.
   - `:xmsf:shell` remains the Android runtime library module. Use `:xmsf:assembleNormalDebug` for
     the installable runtime; `:xmsf:shell:assembleNormalDebug` only packages the library surface.

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
  code type, persistence effects, Binder transaction/flags, and the real runtime consumer. Matching
  a component or method name is not compatibility proof; contract tests and installed-device
  evidence must exercise the real ingress and downstream effect.
- The two public MiPush service facades have different caller-identity guarantees. The bound
  Messenger route must validate `Message.sendingUid` against the target package (or the package
  list supplied by the runtime); an unknown UID or mismatch fails closed. Android does not retain
  the originating UID for a `Service.onStartCommand` callback, so the legacy exported
  `startService` route can only apply action, package-installation, payload/container, size, and
  sanitized-extra validation. Do not describe that route as caller-authenticated or add a
  signature permission without first migrating and proving all stock SDK callers.

## Layering Rules

- Product UI/settings code should depend on `core`, `common`, and explicit xmsf adapters, not deep
  vendor/pinned packages.
- `xmsf/shell/src/main/java/io/github/magisk317/mipush/service/runtime` and
  `xmsf/shell/src/main/java/io/github/magisk317/mipush/bridge` are the allowed adapter areas for direct
  vendor/pinned interaction.
- `vendor` is frozen compatibility/runtime source. Existing product-owned imports under
  `vendor/src/main` are retained as migration debt and must not be expanded with new product
  behavior. New MiPush policy, notification handling, user configuration, and HyperIsland-related
  behavior belong in `:xmsf`, `xposed`, or an explicit adapter/bridge layer; the boundary
  verifier records the existing vendor imports and rejects additions.
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

- App-process root execution goes through `AppRootAccessFacade` and `BoundedShellRunner`. Callers must
  choose cached refresh versus explicit authorization request instead of letting incidental shell
  commands trigger a root prompt.
- Hook-process root execution stays independent and uses its own bounded runner in `xposed`; it must
  not depend on app-process singletons.
- `xmsf/shell/.../utils/LogBundleExporter` is the canonical app log exporter. It owns app-specific
  JSONL selection, old text-log cleanup, archive staging, and optional root-only LSPosed collection;
  shared JSONL encoding/redaction/quota policies remain in `:core` and sanitization of collected
  non-runtime files remains in the diagnostics adapter. It produces an archive for a caller-owned
  destination.
- The former `common/.../utils/LogBundleExporter` facade was removed. Reusable archive and
  sanitization primitives live in the shared diagnostics/Xposed kits; app-specific file selection
  and optional root-only LSPosed collection stay in the xmsf exporter. The Manager UI host opens a
  user-selected SAF destination and saves there directly; it does not construct a share intent or
  materialize a remote archive in manager cache first.

## Public Interfaces

- **`AppRootAccessFacade`**: Query cached state, request authorization explicitly, refresh only
  when already authorized, execute root shell commands. It is defined in
  `xmsf/platform/src/androidMain/kotlin/io/github/magisk317/mipush/platform/support/RootAccessFacade.kt`.
- **`BoundedShellRunner`**: Execute ordinary or root shell with unified timeout and result structure.
- **`RuntimeSettingsAdapter`**: Route UI/settings operations for XMPP host, forced registration,
  service foregrounding, manager environment snapshots, and similar runtime actions through an
  adapter instead of calling vendor runtime directly.

## Current Architecture Debts

- `SmackConfiguration.pingInterval` is configured through its generated public setter in the
  product-owned vendor surface. Keep this direct assignment; do not reintroduce reflective field
  mutation, which is brittle across Android hidden-field restrictions and vendor implementation
  changes.
- Android 17 memory limiting still needs a device baseline. Diagnose limiter exits through
  `ApplicationExitInfo` and validate the runtime under the platform memory limiter before making
  stability claims.
- `KeepAliveRuntimeAdapter` aligns stock 7.4.67-C foreground trigger ownership, target-process
  checks, calm-down timing, connection retries, and strategy environment gates. It attempts a
  runtime-resolved `IProcessObserver` registration and falls back to 60-second process polling when
  hidden API access or `SET_ACTIVITY_WATCHER` is unavailable. Strategy updates remain inert until
  ServiceBox resolves `KASwitch=142`; `OnetrackSwitch=140` and `need_stat` are observed/persisted but
  intentionally do not re-enable stock OneTrack behavior. Installed-device evidence is still
  required before claiming observer permission/registration and a real third-party service bind.
- KeepAlive's standby hook uses exact descriptors for all captured
  `AppStandbyController.setAppStandbyBucket` overloads and applies the bucket index belonging to
  each descriptor. Anti-kill also has a conservative package-level pre-cleanup guard for the exact
  captured `ProcessList.killPackageProcessesLSP` long descriptor; the captured MIUI cleaner's
  short reason-13/subreason-0 path remains deliberately outside the final-sink fallback.
- DeviceIdle whitelist authorization is verified from the framework's `PowerManager` state for
  every requested framework package. Root availability and AppOps success are not substitutes for
  that state, and disabling the feature does not remove whitelist entries without ownership
  metadata.
- KeepAlive treats the persisted enabled preference as inert until ServiceBox resolves `KASwitch`;
  both reconciliation and the polling fallback require `onlineConfigKnown` so startup ordering
  cannot activate third-party service binding early.
- Runtime event writes and replay reads verify the stored event package against the requested
  package inside the runtime user scope before using an event ID; manager-provided metadata is not
  treated as ownership proof.
- Self-update notification revival is restricted to untagged notifications owned by XMSF in the
  current Android user. Notifications delegated to a target package are excluded by the
  `xmsf_target_package` marker; the vendor revival implementation remains unchanged.
- System-only permissions and notification/XSpace behavior are similarly bounded by platform
  policy. Unit/build evidence proves our adapters; installed-device `dumpsys` and hook evidence are
  required for final visible-UI claims.
- SystemUI package-scoped island policy requires a valid notification user ID. An absent or invalid
  ID must not normalize to user 0 or reuse a primary-user cache entry; the package policy fails
  closed until the identity is known.
- SystemUI island proxy IDs and deduplication keys require the source notification user explicitly;
  helper defaults must not turn an omitted user into user 0.
- SystemUI island ownership fallback keys also include that user, even when the platform
  status-bar key is missing; cancellation ownership is therefore scoped consistently with proxy
  IDs and deduplication.
- SystemUI island visual-state fallback keys also include that user, even when the platform
  status-bar key is missing; visual recording and removal remain scoped consistently with the
  notification lifecycle.
- Runtime-generated island proxy IDs also include the current Android user, so the broadcast
  posting/cancellation path cannot reuse one package's proxy identity across users.
- Island proxy request Bundles carry the source notification user through SystemUI; package-scoped
  focus and visual payload options are resolved for that user rather than the process default.
- Island proxy notification posting and cancellation also use the request user's SystemUI context;
  the cancellation broadcast retains the same user identity.
- Template payload construction may pass its already-resolved option snapshot explicitly; the
  builder must not replace that authorization with an unscoped package read.
- SystemUI island preference provider reads also fail closed on a missing, empty, or malformed
  cursor; default options are only used before the refresh loop has a provider result.
- Full cloned/999-user support remains a data-model migration: notification preference,
  diagnostics, and parts of the UI identity are still package-name based. Top/Sweet notification
  lifecycle jobs and their local state keys now include the owning notification user, while the
  application, registration, and event data paths already carry user-scoped identities. Do not
  describe static LSPosed scope support as independent per-user registration/configuration.

- The configuration stack lives only in `xmsf/shell/.../utils` (`Configurations`, `ConfigurationsLoader`, `ConfigValueConverter`, `IconConfigurations`, `PackageConfig`). The duplicate, unused copies that previously sat under `common/.../configurations` were removed. The shared `ConfigJson*`, platform-neutral Lisp evaluator, and match/placeholder replacement plans now live in `:core` `commonMain` under compatible packages; Android/JVM consumers keep source compatibility through `common`'s public `:core` dependency. `common` retains only the JVM URI/Base64 codec facade. Thrift reflection, field access/conversion, mutation, and configuration loading remain in Android/JVM adapters. Island renderer, visual, options, and preference wire contracts follow the same `:core` ownership model. Do not reintroduce a second copy of either parser or wire policy in Android modules.
  Runtime behavior must be covered by contract tests that load JSON through the active xmsf parser
  and then apply it to an `XmPushActionContainer`.
- Configuration activation is a suspend gateway operation for UI callers; runtime-owned code uses
  `ConfigCenter.loadConfigurationsNow(...)` when it needs deterministic activation without creating
  an unowned coroutine scope.
- Real manager Activities are package-hosted by `:mipush`. XMSF still owns runtime gateways and
  Binder service implementations inside the `com.xiaomi.xmsf` process; the standalone manager data
  plane consumes them through :manager:contract / `ManagerRuntimeClient`. Runtime environment diagnostics flow through
  `ManagerRuntimeEnvironmentSnapshot` instead of reflective `com.xiaomi.*` lookups.
- Bootstrap has exactly two Application-owned modes. `MiPushHostApp` calls
  `startFromAppShell()` after XMSF Koin exists; standalone `:mipush` `App` calls
  `startAsRemoteHost(...)`. The modes reject same-process mixing. Manager Activities, launcher aliases, and widgets only consume their package host and do not contain fallback startup.
- Manager data plane **is** remote-primary (`Remote*Source` → ViewModel). `Comparing*` /
  fake `InProcess*` wrappers are migration scaffolding and should not be re-expanded.
- Notification-channel reads cross Binder as wire DTOs and are mapped once into manager-owned
  `NotificationChannelSnapshot` domain state. Manager UI code must not rebuild Android
  `NotificationChannel` / `NotificationChannelGroup` objects; channel deletion is an explicit
  `(packageName, channelId)` command, while framework objects stay inside the xmsf platform reader.
- Full notification-service dumps use the shared `NotificationDumpCommandContract`: try
  `dumpsys notification --noredact` first and run plain `dumpsys notification` only when the
  first result is unusable for that caller. Xmsf keeps strict channel-block validation; the
  Xposed reader also preserves legacy simple-channel and group-only formats. The optional silent
  post/cancel name probe remains explicit and is disabled by default.
- Manager main chrome collapse/expand is intentionally shared across `EventList`, `ApplicationList`,
  `Configurations`, and `Settings`, while `Overview` keeps its own always-visible treatment. A June
  2026 regression showed that route switches during half-expanded animation can leak a negative
  `headerOffsetY` into the next page: if the shared state is only shown and not reset to the top,
  short pages such as Settings can render with clipped top content, and `OverlayHeaderScaffold`
  can even crash Compose with `Padding must be non-negative`. Future refactors must preserve both
  route-time state reset (`animateToTop()`) and non-negative padding guards in the scaffold.
- The following routes are treated as resolved traps and should not be reintroduced:
  - do not copy manager bindings (`SettingsManager`, manager ViewModels, manager Koin module
    contents) into `xmsfCoreKoinModule`; only the `:xmsf` application shell may depend on
    `:manager:ui` for the post-runtime bootstrap hook, while `:xmsf:shell` remains an adapter layer
  - do not make `MainActivity` or other manager UI entrypoints create a second Koin host or own
    manager bootstrap; startup belongs to the package `Application`
  - do not remove or bypass the `MiPushHostApp` post-runtime startup hook
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
when `-PenableKover=true` is supplied. The coverage gate measures `:common`, `:core`, `:xposed`,
and `:xmsf:shell` at 10%, 10%, 10%, and 7% line coverage respectively. The packaging-only
`:xmsf` application is excluded because it contains only entrypoint wrappers and has no independent
unit-test surface; its implementation is tested through `:xmsf:shell` and the installable APK build.
The ordinary `check` path stays on Detekt, unit tests, Android checks, and boundary checks, while
the explicit Kover gate remains available to CI without a stale zero-coverage packaging target.

## Data-plane idiom (authoritative)

Chosen production shape after the app split:

| Concern | API | Notes |
| --- | --- | --- |
| Read | `Remote*Source` + `ManagerRuntimeClient` | Suspend; map missing/binding/denied to typed statuses |
| Write | `RemoteWriteSupport.execute` (suspend) | Preferred from ViewModels / coroutines; allowlisted keys |
| Write bridge | None | All production manager writes use suspend `RemoteWriteSupport.execute`; do not reintroduce a blocking Binder bridge |
| Legacy façade | `Manager*Gateway` → `RemoteManager*Gateway` | Same Binder underneath; prefer Source/Client in new code |
| Test harness | `Comparing*` + `Gateway*` | **Test source set only**; not registered in production Koin |

Do not reintroduce in-process primary reads in `:mipush`. Do not bootstrap manager from UI,
launcher, widget, or `xmsfCoreKoinModule`; keep the app-shell call in `MiPushHostApp`.
