# MiPushFramework Refactor And Island Integration Plan

Dates:

- Initial refactor plan: 2026-05-16
- Refactor update: 2026-05-25
- Consolidated in-repo record: 2026-06-02

This document consolidates the former workspace-level planning notes into the repository:

- `/home/lzc/wqk/mipushframework-refactor-plan.md`
- `/home/lzc/wqk/wqk-mipushframework-xmsf-build-outputs-a-quiet-knuth.md`
- `/home/lzc/wqk/mipush-island-styles-plan.md`

## Summary

MiPushFramework is being refactored in staged, compatibility-preserving slices. The goal is not a
one-shot rewrite, but a gradual alignment toward clearer module boundaries, modern dependency
injection, explicit quality gates, and native notification semantics while keeping stock-facing XMSF
contracts stable.

The notification side now includes three related tracks:

1. Framework-side Live Updates for progress-like push notifications.
2. MIUI/HyperOS focus notification and HyperIsland proxy integration.
3. Multi-template island routing based on notification semantics.

## Current State

### Module Snapshot

- `app/` - application shell.
- `manager/` - management UI and settings surface.
- `xmsf/` - product-owned `com.xiaomi.xmsf` application, manifest entrypoints, notification
  publish, runtime adapters, settings providers, and app-process bridges.
- `xposed/` - hook-side integration, SystemUI/XMSF process hooks, and island proxy dispatch.
- `core/` - platform-neutral contracts and stateless runtime/notification logic.
- `common/` - shared app/runtime utilities and reusable models.
- `vendor/` - retained Xiaomi runtime and network stacks that remain load-bearing.
- `pinned/` - frozen protocol and serialization surface.
- `mipush/` - client-facing MiPush SDK compatibility code.
- `magisk-ui-kit/` - reusable Compose UI components.
- `build-logic/` - Gradle convention plugins.

Device dumps and platform jars are reference inputs only. They must not enter the Gradle source
graph.

### Technology Snapshot

| Area | Current Direction | Status |
|------|-------------------|--------|
| Xposed API | libxposed `101.0.1` | Entry, metadata, and old-name business hook cleanup complete |
| DI | Koin `4.2.1` | Hilt and `javax.inject` removed from main source/build scripts |
| Gradle | 9.x line | Aligned with sibling projects where practical |
| Kotlin | RC/aggressive line | Kept aggressive with rollback awareness |
| AGP | Alpha line | Kept aggressive with rollback awareness |
| DataStore | Alpha line | Kept because current API usage is stable enough |
| Quality | Detekt + Kover | xposed Detekt blocks new findings; Kover reports/verifies key modules |

## Refactor Problem Areas

### P1: Xposed API Migration Closeout

`xposed/` uses `io.github.libxposed:api:101.0.1`.

Completed state:

- `LibXposedEntry : XposedModule` is the unified entrypoint.
- `META-INF/xposed/{module.prop,java_init.list,scope.list}` declares modern metadata.
- Old Xposed manifest metadata and `assets/xposed_init` were removed from `mipush`.
- Hook helpers such as `hook`, `hookMethod`, `hookAllMethods`, and `invokeOriginalMethod` are
  centralized in `xposed/XPosedX.kt`.
- `settings.gradle.kts` no longer needs `https://api.xposed.info/`.
- `HookSystemService`, `SystemNotificationManager`, and `KeepAliveHook` moved to neutral
  hook/reflection helpers.
- Business hooks no longer directly use the project compatibility facade named
  `io.github.magisk317.mipush.xposed.XposedHelpers`.
- The old `XposedHelpers` name remains only inside the compatibility layer where it preserves
  exception and best-match reflection semantics.

Verification:

```bash
rg "de\\.robv.*api|XposedBridge|XC_Method|api\\.xposed|xposed_init" \
  settings.gradle.kts gradle xposed mipush/src/main
./gradlew :xposed:compileDebugKotlin :mipush:assembleDebug :xmsf:assembleNormalDebug
```

### P2: DI Migration

The `xmsf/` module has moved from Hilt to Koin.

Completed state:

- `MiPushFrameworkApp` starts Koin and `AppDependencies.start(context)` remains idempotent for
  non-standard entrypoints.
- Former Hilt modules were replaced by `KoinModules.kt`.
- Activity, service, runtime bridge, and Compose ViewModel acquisition paths use Koin-backed
  retrieval.
- Hilt plugins, Hilt runtime/compiler dependencies, Hilt Compose navigation, `EntryPointAccessors`,
  and `hiltViewModel()` were removed from main build/source surfaces.
- `javax.inject` annotations/dependencies were removed from `common` and `xmsf` main source/build
  scripts.

Verification:

```bash
rg "hilt|Hilt|dagger|javax-inject|javax\\.inject|androidx-hilt|@Inject|@Singleton|@JavaxSingleton|EntryPointAccessors|hiltViewModel|HiltViewModel|AndroidEntryPoint|HiltAndroidApp" \
  build.gradle.kts gradle/libs.versions.toml common/build.gradle.kts xmsf/build.gradle.kts \
  common/src/main/java xmsf/src/main/java xmsf/src/test/java
git diff --check
./gradlew :xmsf:compileNormalDebugKotlin
./gradlew :xmsf:testNormalDebugUnitTest
./gradlew :xmsf:assembleNormalDebug
```

### P3: Quality Gates

Current state:

- Detekt is installed.
- Historical debt is not used to block every module at once.
- `xposed` has a Detekt baseline and blocks new findings.
- `qualityGateDetekt` aggregates the blocking Detekt targets.
- Kover has module reports and verification for `common`, `core`, `xposed`, and `xmsf`.
- Global Kover threshold remains `minBound(0)` until a realistic baseline is chosen.
- `check` includes `qualityGateDetekt` and `qualityGateKoverVerify`.

Remaining:

- Pick the first non-zero Kover threshold from real reports.
- Continue moving report-only Detekt modules toward blocking mode once noisy historical findings are
  either fixed or baselined intentionally.

### P4: Aggressive Toolchain Risk

The project intentionally keeps aggressive AGP/Kotlin/DataStore versions. This is acceptable as long
as each upgrade has a narrow rollback path and build failures are triaged against dependency drift
before broad refactors.

### P5: Facade And Compatibility Bridge Cleanup

After DI migration, continue removing facades and bridges that only exist for old Hilt/static lookup
paths.

Targets:

- `XPosedX` compatibility layer.
- `AppDependencies` static service-locator paths.
- Legacy singleton fallbacks.
- Runtime bridge surfaces that can become explicit adapters.
- Notification/configuration compatibility wrappers.

Rules:

- Keep bridges that are required by cross-process, cross-API, or stock compatibility behavior.
- Prefer Koin native injection or explicit construction inside app-process code.
- Treat Xposed/libxposed, notification publish, configuration loading, and push processing as
  high-risk paths that need focused verification after cleanup.

## Notification Rule Chain

`MyMIPushNotificationHelper.notifyPushMessage()` currently passes through these major gates before
or during publish:

| Stage | Gate | Mechanism | User Configurable |
|-------|------|-----------|-------------------|
| 1 | `StalePackagePushGuard.shouldDropNotification()` | Missing/stale target package guard | No |
| 2 | `shouldPublishNotification()` | Only handles `ActionType.SendMessage` | No |
| 3 | `RegisteredApplicationDb.isBlocked()` | App-level database blacklist | Yes |
| 4 | `shouldDropReplayNotification()` | Replay window filter | No |
| 5 | `MiPushRuntimeBridge.onNotificationDispatch()` | Runtime duplicate/accounting hook | No |
| 6 | `AppInfoUtils.getAppNotificationOp()` | System notification setting | Yes |
| 7 | `Configurations` rules | `PackageConfig` regex -> ignore/wake/open/notify | Yes, config file |
| 8 | `VoipNotificationHelper` | VoIP stale/end-event filtering | No |
| 9 | `NotificationSortFilter` | Focus delete cache | No |
| 10 | `LiveUpdateDetector` / `ProgressStyleBuilder` | Live Updates and fallback progress style | Heuristic |
| 11 | `FocusNotificationRegistry` | Focus notification secure registration | No |
| 12 | `NotificationContentSupport` | Empty visible-text filtering | No |

`PackageConfig` supports:

- `OPERATION_OPEN` - launch/open target and deliver payload.
- `OPERATION_IGNORE` - drop notification.
- `OPERATION_NOTIFY` - normal notification display.
- `OPERATION_WAKE` - wake the screen.

Open questions:

- Should `RegisteredApplicationDb` be folded into `Configurations` so there is one rule system?
- Should time-window rules be supported, for example night or work-hours rules?
- Should high-frequency notifications be aggregated into summaries?
- Should content priority drive notification priority?
- Should the app expose a visual rules editor instead of requiring config-file edits?
- Should Live Updates be explicitly configurable per app/rule instead of only heuristic?

## Live Updates Track

Android 16 introduced:

- `Notification.ProgressStyle`.
- Promoted ongoing notifications.
- `POST_PROMOTED_NOTIFICATIONS`.

Framework-side implementation is viable because the system permission can be declared/handled by the
framework app.

Implemented surfaces:

1. `LiveUpdateDetector`
   - Detects progress-like notification text.
   - Categorizes delivery, ride-hailing, logistics, download, travel, navigation, timer, call, and
     generic progress cases.
   - Extracts percentages from formats such as `65%` or `3/5`.
   - Uses active-progress indicators such as delivery ETA, distance, countdown, or ongoing state.
   - Excludes obvious chat-style apps from progress promotion.

2. `ProgressStyleBuilder`
   - Android 16+: applies guarded `Notification.ProgressStyle` and requests promoted ongoing.
   - Older Android: falls back to compat progress style, high priority, and ongoing behavior.
   - Avoids native `ProgressStyle` for custom RemoteViews where platform conversion is unsafe.

3. Manifest integration
   - Declares the promoted notifications permission.

4. `NotificationController.publish()`
   - Detects and applies Live Update semantics during publish.
   - Translates eligible non-MIUI focus semantics to Android-native progress surfaces instead of
     leaking MIUI private extras.

5. Mock notification support
   - Mock notifications are useful for smoke-testing construction, routing, and final posted
     notification records.
   - Final validation should still include real push payloads when click intents, app payload shape,
     or SystemUI island hook behavior is under test.

Key design decisions:

- Android 16+ gets native progress styling; lower versions retain a standard progress notification.
- Existing push payloads do not need server-side changes for heuristic Live Updates.
- MIUI private focus extras and Android-native Live Updates are separate semantic surfaces.

## Focus Notification Track

XMSF already had `miui.focus.param` passthrough. The completion work added the missing pieces around
secure registration, interaction diagnostics, priority, and testability.

Implemented surfaces:

1. `FocusNotificationRegistry`
   - Registers focus notification keys in `Settings.Secure("updatable_focus_notifs")`.
   - Removes registrations on cancel.
   - Uses `WRITE_SECURE_SETTINGS` when available and root shell fallback otherwise.

2. `FocusInteractionReceiver`
   - Receives `com.android.systemui.action.NOTIFICATION_INTERACTION_EVENT`.
   - Logs click, pull-down, and panel interaction events for diagnosis.

3. Channel/priority fallback
   - Focus notifications are promoted to high priority when focus parameters are present.

4. Mock notification panel
   - Includes plain, BigText, BigPicture, Inbox, Messaging, Media, Progress, heads-up, focus,
     focus-with-picture, and VoIP cases.
   - Supports choosing the target package.

Key conclusions:

- `miui.focus.rv` RemoteViews must be built by a foreground app and are not a push-channel feature.
- Some island scenes, such as foreground app delivery progress, do not naturally pass through MiPush.
- Push-channel island behavior requires either server-provided `miui.focus.param` or framework-side
  generated/proxy semantics.

## HyperIsland Initial Integration Track

The initial integration plan was to embed the HyperIsland island display capability into
MiPushFramework so eligible MiPush notifications could automatically receive island/focus behavior
without requiring a separate HyperIsland module.

### Core Integration Scope

Required:

1. `hyperisland_kit` SDK through `HyperIslandNotification.Builder`.
2. `IslandDispatcher` in the SystemUI process.
3. `NotificationIslandNotification` style template for `miui.focus.param` JSON.
4. A simplified `GenericProgressHook`-like path for MiPush notifications.
5. `UnlockFocusAuthHook` to bypass XMSF focus-auth checks when enabled.
6. Mutual handling with the standalone HyperIsland module to avoid duplicate focus unlock hooks.

Optional future scope:

- Template registry.
- AI summaries.
- Download manager island.
- Toast interception.
- Blacklist/keyword filtering.
- Outer glow effects.
- Custom expression engine.

### Hook And Dispatcher Shape

SystemUI process:

- Register a MiPush-specific island hook.
- Hook the MIUI inner notification bean generation path.
- Only handle MiPush notifications marked or shaped by the xmsf publisher.
- Build island request data and post a separate island/proxy notification when applicable.

XMSF process:

- Keep normal push handling and notification publish.
- Apply focus auth unlock only when enabled.
- Keep configured focus payloads on the MIUI private path.
- Allow generated island proxy only for eligible notifications and only on MIUI/HyperOS devices.

Initial file areas:

```text
xposed/src/main/java/io/github/magisk317/mipush/hook/island/
xposed/src/main/java/io/github/magisk317/mipush/hook/island/template/
xposed/src/main/java/io/github/magisk317/mipush/hook/systemui/MiPushIslandHook.kt
xposed/src/main/java/io/github/magisk317/mipush/hook/xmsf/UnlockFocusAuthHook.kt
```

Initial modified areas:

```text
xposed/build.gradle.kts
xposed/src/main/java/.../hook/ModuleHooks.kt
xposed/src/main/java/.../hook/Configurations.kt
```

Reference inputs from HyperIsland remain read-only references:

```text
HyperIsland/xposed/islanddispatch/
HyperIsland/xposed/template/core/
HyperIsland/xposed/template/NotificationIslandNotification.kt
HyperIsland/xposed/hook/XMSF/UnlockFocusAuthHook.kt
HyperIsland/xposed/hook/SystemUI/GenericProgressHook.kt
HyperIsland/xposed/islanddispatch/invoke/IslandDispatcherNotifier.kt
```

### Settings

Island settings carried through xmsf provider / hook process configuration include:

- Island enabled.
- Timeout.
- First-float behavior.
- Floating mode.
- Whether to keep the original notification.
- Whether focus notification auth/unlock behavior is enabled.

### Risks

- `hyperisland_kit` compatibility with the current LSPosed/libxposed API version.
- `miui.focus.param` JSON shape changes across HyperOS versions.
- SystemUI hook timing around MIUI notification bean construction.
- Duplicate hooks when standalone HyperIsland is also installed.

## HyperIsland Multi-Style Track

The later style work expanded the single ChatInfo-like template into a routing system driven by
notification semantics.

### Template Routing

Classifier dimensions:

1. Channel.
2. Content keywords.
3. Package name.

Content has higher priority than package name so package-level guesses do not misclassify mixed-use
apps.

Style mapping:

| Classification | Meaning | HyperIsland Template |
|----------------|---------|----------------------|
| `MESSAGE` | IM/chat | `ChatInfo` |
| `GENERAL` | News/system/general | `BaseInfo type=1` |
| `BANNER` | Ads/promotion banner | `BaseInfo type=2` |
| `ALERT` | Reminder/countdown/alert | `HighlightInfo` |
| `PROMO` | Price/coupon/discount | `HighlightInfoV3` |
| `MEDIA` | Music/podcast/media | `CoverInfo` |
| `PROGRESS` | Download/upload/progress | `IconTextInfo` |

Implemented areas:

- `NotificationStyle` enum in `common`.
- Shared `NotificationClassifier` in `common`.
- Thin xmsf classifier adapter.
- Template routing and component assembly in `MiPushIslandPayloadBuilder`.
- Template routing and timer/progress island configuration in the xposed island builder.
- Removal of the old xposed-only classifier duplication.
- Classifier use from `NotificationIslandTemplate` and `IslandDispatcherNotifier`.

### Component Enhancements

Implemented:

- Progress components:
  - `setProgressBar()`.
  - `setSmallIslandCircularProgress()`.
  - `setBigIslandProgressCircle()`.
- `LiveUpdateDetector` integration:
  - Prefer detected structured progress data.
  - Fall back to text regex.
- `HintInfo`:
  - `setHintInfo()`.
  - `setHintAction()`.
- Actions:
  - Click action and hint action support for all templates.

### Advanced Island Configuration

Implemented:

- Progress notifications use circular/progress island components.
- Countdown notifications use `setBigIslandCountdown()` when a duration or `HH:MM:SS` shape can be
  extracted.
- Tracking notifications such as delivery, ride-hailing, logistics, and travel use count-up island
  behavior from the current time.

Optional future work:

- App-specific custom RemoteViews.
- Advanced style switches in the manager UI.

## Verification Playbook

### Build And Unit Tests

```bash
./gradlew :core:testDebugUnitTest
./gradlew :xmsf:testNormalDebugUnitTest
./gradlew :xposed:compileDebugKotlin
./gradlew :xmsf:assembleNormalDebug
./gradlew :mipush:assembleDebug
```

### Architecture Gates

```bash
./gradlew verifyModuleBoundaries
./gradlew qualityGateDetekt
./gradlew qualityGateKover
./gradlew check
```

### Runtime Notification Validation

Use app logs and device-state evidence together:

```bash
adb shell dumpsys notification --noredact
```

Important checks:

- Non-MIUI/AOSP:
  - Private MIUI extras such as `miui.focus.param` and `miui.focus.pics` should not appear on
    ordinary posted notification records.
  - Progress-like focus semantics should translate into `xmsf.live_update` / ongoing progress
    behavior.
- MIUI/HyperOS:
  - Configured focus notifications keep private focus extras.
  - Generated focus uses the SystemUI island proxy path where enabled.
  - Generated island proxy should not double-post the original notification unless the setting says
    to keep it.
- Mock notifications:
  - Good for construction/routing/record checks.
  - Not a complete substitute for real push payloads when validating click intent, real app payload,
    or SystemUI island-hook behavior.

### Conflict Test

When standalone HyperIsland is installed, repeat a MiPush island/focus test and confirm duplicate
focus unlock or duplicate island dispatch does not occur.

## Non-Goals

- Do not replace the logging stack merely for aesthetic alignment.
- Do not replace Room.
- Do not force a module-structure rewrite when current boundaries are serving compatibility.
- Do not rewrite hook business logic solely to make it look pure; runtime behavior stability wins.
- Do not force style alignment on frozen `vendor`/`pinned` source where compatibility is the point.
