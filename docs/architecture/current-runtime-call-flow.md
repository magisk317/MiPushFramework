# Current Runtime Call Flow

This document captures the current product-owned runtime chain after the `core`, `vendor`, and `pinned` module split.

It is the reference for future stock-XMSF ports: new compatibility features should attach to one of these stages instead of bypassing the runtime spine.

## 1. App Init

- Entry point: packaged host `MiPushHostApp`, which extends `MiPushFrameworkApp`
- Main work:
  - initialize DB and app context
  - install logger and crash logger
  - install hook layer
  - initialize notification compatibility
  - attach runtime execution bridge
  - attach channel tracker
  - enable push controller and wake activation service
  - register manager Koin modules from the app shell after xmsf app dependencies are ready
  - keep manager bootstrap owned by the packaged host app instead of the manager UI itself
  - avoid duplicating `MiPushFrameworkApp.onCreate()` in the app shell; host-specific work should
    use the dedicated post-dependency hook instead

Cold-start trap:

- `MainActivity` injects `SettingsManager` during launch, so `MiPushHostApp` must register
  `ManagerDependencies` as soon as the xmsf root Koin container is ready.
- A June 18 2026 regression showed that guarding this registration with a fragile early-process
  heuristic can skip the manager module during cold start and crash launch with Koin
  `NoDefinitionFoundException` for `SettingsManager`.
- Process-sensitive runtime work can still use `PushControllerUtils.isAppMainProc(...)`, but that
  helper itself now needs stable current-process-name APIs rather than `runningAppProcesses`.

Key source:

- `xmsf/src/main/java/io/github/magisk317/mipush/app/MiPushFrameworkApp.kt`
- `app/src/main/java/com/xiaomi/xmsf/app/MiPushHostApp.kt`

## 2. Bridge Entry

- Entry point: `com.xiaomi.xmsf.push.service.MiPushFacadeService`
- Main work:
  - receive XMSF-facing intents
  - record routing state into `PushRuntime`
  - refresh config state when required
  - forward business intents into vendored `com.xiaomi.push.service.XMPushService`

Key source:

- `xmsf/src/main/java/com/xiaomi/xmsf/push/service/MiPushFacadeService.kt`

## 3. Runtime Spine

- Entry point: `xmsf` `PushRuntime` facade (package `io.github.magisk317.mipush.runtime`)
- Main work:
  - queue and drain bridge intents
  - track registration, connection, and channel state
  - replay registration work on boot, network, and account changes
  - track downstream delivery and notification counters
- Current shape:
  - `PushRuntime` is the stable facade used by stock-facing code and manager adapters
  - `AndroidPushRuntime` owns the Android-coupled in-process state, counters, queues, and snapshots

Key source:

- `xmsf/src/main/java/io/github/magisk317/mipush/runtime/PushRuntime.kt`
- `xmsf/src/main/java/io/github/magisk317/mipush/runtime/android/AndroidPushRuntime.kt`

## 4. Execution Host

- Entry point: `PushRuntimeExecutionBridge`
- Main work:
  - framework self-registration
  - forced app registration
  - pending register task processing
  - Xiaomi-account alias sync through the product-owned account/cloud bridge
  - downstream payload dispatch
  - notification cancel dispatch
  - connection ensure/reset

Key source:

- `xmsf/src/main/java/io/github/magisk317/mipush/runtime/PushRuntimeExecutionBridge.kt`

## 5. Vendored Long Connection

- Entry point: vendored `com.xiaomi.push.service.XMPushService`
- Main work:
  - own the long-lived connection stack
  - run reconnect, packet sync, and intent delegates
  - expose lifecycle state back to product-owned code

Key source:

- `vendor/src/main/java/com/xiaomi/push/service/XMPushService.kt`

## 6. Downstream Delivery

- Entry point: `com.xiaomi.push.sdk.PushMessageProcessor`
- Main work:
  - optionally foreground the target app
  - deliver to target `PushMessageHandler`
  - fall back to broadcast delivery if direct service start fails

Key source:

- `xmsf/src/main/java/com/xiaomi/push/sdk/PushMessageProcessor.kt`

## 7. Notification Publish

- Entry point: `MyMIPushNotificationHelper`
- Main work:
  - unpack and dedupe payload
  - apply package-config operations
  - align stock notification behavior for focus, VoIP, SweetTag, grouping, click, and action intents
  - keep explicit `miui.focus.param` on the original notification when remote configuration supplies one
  - leave regular notifications without generated focus extras so they remain visible in the notification shade
  - publish, ignore, wake, or open based on resolved policy

Supporting layers:

- `NotificationController`
- `NotificationManagerEx`
- `NotificationIdentityBridge`
- `IslandPreferenceProvider` in the xmsf process, which exposes HyperIsland display flags through caller validation: self/system/root, callers holding the read permission, or the system-installed `com.android.systemui` package.
- `MiPushIslandHook` in the Xposed `com.android.systemui` process, which posts a separate HyperIsland proxy notification for eligible MiPush notifications before MIUI builds its inner notification bean.
- `UnlockFocusAuthHook` in the Xposed `com.xiaomi.xmsf` process, which relaxes XMSF focus authorization for generated focus payloads.
- `IslandPreferences` in the Xposed module, which periodically reads the xmsf provider so SystemUI injection and XMSF authorization share the same runtime flags.

Delegated notification identity and display policy are deliberately separate concerns:

- a delegated post keeps `pkg` as the target application and `opPkg` as `com.xiaomi.xmsf`; the
  system-server hook resolves the target package's UID for the target user instead of changing the
  operation package to the system;
- a remotely configured `miui.focus.param` remains attached to its original notification;
- a locally generated focus/island proxy is a SystemUI path, and `showNotification=false` controls
  shade presentation rather than suppressing that proxy path.

Stored-event replay contract:

- The manager calls `ManagerEventGateway.mockMessage(...)` as a suspend operation and receives one
  of `BlockedByPermission`, `Dispatched`, `Posted`, or `Failed` from the shared `MockReplayOutcome`
  contract.
- Modern mock replay runs the notification policy/publish path synchronously on the manager's IO
  coroutine. `Posted` is returned only after `NotificationController.publish(...)` receives a
  successful notification-manager post.
- A blocked application, denied notification operation, or focus filter returns
  `BlockedByPermission`. A legacy reflection path whose final post cannot be observed returns
  `Dispatched`; payload/service/publish failures return `Failed`.
- Runtime observation logs use the same outcome names, and the manager event page presents all four
  results explicitly. Do not restore the old Boolean contract, which only proved that a payload was
  parseable and a replay attempt was started.

Key sources:

- `xmsf/src/main/java/io/github/magisk317/mipush/service/runtime/MyMIPushNotificationHelper.kt`
- `xmsf/src/main/java/io/github/magisk317/mipush/notification/NotificationManagerEx.kt`
- `xmsf/src/main/java/com/xiaomi/xmsf/provider/IslandPreferenceProvider.kt`
- `xposed/src/main/java/io/github/magisk317/mipush/hook/island/IslandPreferences.kt`
- `xposed/src/main/java/io/github/magisk317/mipush/hook/systemui/MiPushIslandHook.kt`
- `xposed/src/main/java/io/github/magisk317/mipush/hook/xmsf/UnlockFocusAuthHook.kt`
- `vendor/src/main/java/com/xiaomi/push/service/NotificationIdentityBridge.kt`

## 8. Stock Compatibility Surfaces

- Entry points:
  - `com.xiaomi.xmsf.provider.ChannelProvider`
  - `com.xiaomi.xmsf.provider.PushProfileIdProvider`
  - `com.xiaomi.push.provider.PushCommonProvider`
  - `com.xiaomi.push.provider.PushSupportProvider`
  - `com.xiaomi.xmsf.pushcontrol.PushControlProvider`
  - `com.xiaomi.xmsf.services.MainProcBridgeService`
  - `com.xiaomi.xmsf.services.ServiceBoxService`
  - `com.xiaomi.xmsf.services.keepalive.strategy.KeepAliveConfigService`
  - `com.xiaomi.xmsf.push.service.notificationcollection.NotificationListener`
  - `com.xiaomi.xmsf.push.service.StatService`
  - `com.xiaomi.xmsf.push.service.receivers.XMSFUploadReceiver`
  - `com.xiaomi.xmsf.pushprocess.PushInnerReceiver`
- Main work:
  - expose stock provider authorities and service names expected by callers
  - preserve exact stock method names, Bundle shape, result-code type, Binder ABI, caller
    identity, persistence effect, and downstream consumer rather than accepting same-name
    components as proof
  - bridge stock-facing calls into `PushRuntime`, notification helpers, online config, app DB
    state, and the reduced keep-alive runtime
  - keep subprocess and keepalive coordination inside product-owned glue instead of pushing it down into `vendor`

Important live consumers:

- `StockProfileIdStore` implements the four caller-owned profile calls. Display-message profile
  mismatches are fenced after decrypt in `MIPushEventProcessor`, which owns the single missing
  profile acknowledgement; `MiPushRuntimeBridge` has a later storage/notification fence so a
  rejected payload cannot leak into EventDb or notification allowance.
- `StockPushSupport` owns the consent-gated Box projection. It exposes only records with the
  explicit Box opt-in, enabled channel state, and a constructible Activity route; the returned
  bytes are an Activity `Intent` parcel. Local delete state is tied to an Event row, not a remote
  message ID.
- `StockChannelSupport` owns the broker-gated stock channel mapping and permission bitmask.
  `NotificationController` prefers the resulting provider/legacy channel ID before generating a
  local fallback channel.
- `KeepAliveRuntimeAdapter` persists strategy JSON but waits for `ServiceBoxService` to resolve
  stock `KASwitch=142` before making reduced polling bind/unbind decisions. It reads/persists
  `OnetrackSwitch=140` separately but does not re-enable stock OneTrack behavior while telemetry
  remains disabled.
- Hand-written Binder facades preserve descriptor attachment, local/remote resolution, transaction
  ordering, and one-way flags. `HttpService` returns stock-shaped local responses instead of
  forwarding telemetry; `BindMiCloudPushService` consumes `key_to_bind_intent` and invokes the
  remote worker.

Key source:

- `xmsf/src/main/java/com/xiaomi/xmsf/stock/StockSurfaceSupport.kt`
- `xmsf/src/main/java/com/xiaomi/xmsf/stock/StockProfileIdStore.kt`
- `xmsf/src/main/java/com/xiaomi/xmsf/stock/StockPushSupport.kt`
- `xmsf/src/main/java/com/xiaomi/xmsf/stock/StockChannelSupport.kt`
- `xmsf/src/main/java/com/xiaomi/xmsf/stock/StockNotificationMetadataBridge.kt`
- `docs/architecture/stock-dump-contract-audit-2026-07.md`

## 9. Account / Cloud Bridge

- Entry point: `DefaultAccountCloudBridge`
- Main work:
  - discover Xiaomi-account presence
  - synchronize alias state
  - retrieve service tokens through `AccountManager` when available
  - expose stock-restored cloud/bind surfaces with defined degraded behavior

Key source:

- `xmsf/src/main/java/com/xiaomi/xmsf/account/AccountCloudBridge.kt`

## Porting Rule

When porting stock XMSF behavior, first classify the feature into one of these stages:

- app init
- bridge entry
- runtime spine
- execution host
- vendored long connection
- downstream delivery
- notification publish
- stock compatibility surfaces
- account / cloud bridge

Only bypass `PushRuntime` when the stock feature is strictly self-contained and does not participate in routing, lifecycle, or shared compatibility state.

## Adapter Boundary

`xmsf/src/main/java/io/github/magisk317/mipush/service/runtime` and
`xmsf/src/main/java/io/github/magisk317/mipush/bridge` are the allowed product-owned adapters
that may touch vendor/runtime and protocol types directly. UI, settings, and feature code should
go through these adapters or through `core` facades instead of importing deep `com.xiaomi.*`
transport/protocol classes.

Settings/runtime actions such as foregrounding the push service, resetting the XMPP connection, and
reading the current stock XMPP host are routed through `RuntimeSettingsAdapter`. Root and shell
actions are routed through `RootAccessFacade`, with hook-side root probes kept in the xposed module's
bounded runner.
