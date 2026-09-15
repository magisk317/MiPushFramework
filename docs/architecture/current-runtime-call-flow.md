# Current Runtime Call Flow

This document captures the current product-owned runtime chain after the `core`, `vendor`, and `pinned` module split.

It is the reference for future stock-XMSF ports: new compatibility features should attach to one of these stages instead of bypassing the runtime spine.

## 1. App Init (dual package)

Shipping shape is **two APKs**. Bootstrap belongs to each package's `Application`, never to an
Activity, launcher alias, or widget.

### 1a. XMSF runtime package (`com.xiaomi.xmsf`)

- Entry point: packaged host `MiPushHostApp` → `MiPushFrameworkApp`
- `MiPushFrameworkApp` starts `AppDependencies` first, then invokes
  `onAppDependenciesStarted()`.
- `MiPushHostApp` overrides that hook, verifies the main process, and calls
  `ManagerDependencies.startFromAppShell()` to load `managerKoinModule` into the existing host.
- Main runtime work remains in `MiPushFrameworkApp` / xmsf modules:
  - initialize DB and app context
  - install logger and crash logger
  - install hook layer / notification compatibility
  - attach runtime execution bridge and channel tracker
  - enable push controller and wake activation service
  - expose `ManagerRuntimeService` (Binder) for the manager package
- Process-sensitive runtime work may use `PushControllerUtils.isAppMainProc(...)` with stable
  current-process-name APIs (avoid `runningAppProcesses`)
- `xmsfCoreKoinModule` owns runtime gateway implementations only. It does not import manager UI or
  manager Koin definitions.

Key source:

- `xmsf/src/main/java/com/xiaomi/xmsf/app/MiPushHostApp.kt`
- `xmsf/shell/src/main/java/io/github/magisk317/mipush/app/MiPushFrameworkApp.kt`

### 1b. Manager package (`io.github.magisk317.mipush`)

- Bootstrap owner: `:mipush` `App.onCreate()`
- Bootstrap: **`ManagerDependencies.startAsRemoteHost(...)`**
- Loads manager Koin with **remote-primary** data plane (`Remote*Source` / `ManagerRuntimeClient`)
- Talks to XMSF only through signature-permission Binder (`ManagerRuntimeService`)
- `MainActivity`, manager Activities, and widgets consume the Application-owned Koin host; they
  do not create or repair it.
- The desktop launcher alias `MainActivityDefault` targets `MainActivity` directly. There is no
  `WelcomeActivity` or `ManagerLauncherActivity` trampoline; `MainActivity` checks
  `PreferenceRepository.showWizard` and routes first-run users directly to `RequestPermissionPage`.
- Runtime-to-manager navigation uses `ManagerUiEntryPoints`; XMSF keeps runtime service/provider
  contracts only and does not declare manager activity aliases or `ManagerUiRedirectActivity`.
- Xposed module is packaged with `:mipush`, not with `:xmsf`
- Connection status uses `ConnectionStatusViewModel` -> `RemoteConnectionSnapshotSource` ->
  `ManagerRuntimeClient`; the XMSF service assembles counters, timing, heartbeat, host and socket
  state from `RuntimeSettingsAdapter` / `PushRuntime.connectionSnapshot()`.
- Remote read failures are failures, not successful empty snapshots. UI callers keep the last
  successful value and retry after runtime availability returns.
- Notification-time island settings are read through a synchronous SDK boundary today. Until
  that boundary is converted end-to-end, a settings read failure must fail closed for island
  proxy/focus generation while preserving the original notification path; it must not use the
  enabled default as an implicit fallback. The main XMSF application owns a process-local global
  snapshot cache and refreshes it asynchronously after `ACTION_PREF_CHANGED`; package-scoped
  registration flags remain database-owned and may use a `(userId, packageName)` cache only when
  successful database insert/update operations update that cache synchronously. SystemUI carries
  `StatusBarNotification.userId` through the provider query so its package cache uses the same
  identity instead of a package-only key.
- Zygisk configuration reads use the same rule: a missing root, unavailable Binder, or failed
  file read is an unavailable result, never an empty `ZygiskConfig`. The manager must not save
  while the current configuration cannot be read; an actually empty file remains a valid empty
  configuration. Module status and package scans follow the same rule: unavailable is distinct
  from disabled and from a successful scan with no candidates.
- Notification-channel pages remain Binder DTOs until `RemoteNotificationChannelSource` maps them
  into manager domain summaries. The UI consumes that snapshot directly, and deletion sends only
  the package/channel identity command; no manager path reconstructs framework channel objects.

Key source:

- `mipush/src/main/java/io/github/magisk317/mipush/app/App.kt`
- `manager/.../di/ManagerKoinModules.kt`
- `common/.../ManagerComponentNames.kt` and `common/.../ManagerUiEntryPoints.kt`
- `common/.../XmsfComponentNames.kt` (runtime service component names)

#### Manager event reads and cache

The manager event page is deliberately cache-first. `EventListCacheStore` persists a raw,
user-scoped event snapshot in manager DataStore. Opening or re-entering the page restores that
snapshot without synchronously calling runtime; background maintenance refreshes the first page and
merges new rows into the cache. Explicit pull-to-refresh also uses the remote runtime source, while
preserving the cache when the remote page is empty or temporarily unavailable. Consequently,
`ManagerRuntime getEventPage items=0` means that particular runtime query returned no rows; it does
not prove that the manager has no historical event rows or that the visible page must immediately be
empty. The cache is a UI mirror, not the authoritative XMSF event store.

### 1c. Explicit non-goals / regressions to avoid

- Do **not** remove the app-shell hook or move it into an `xmsf` Koin module
- Do **not** start manager dependencies from UI/launcher/widget entrypoints
- Do **not** mix app-shell and remote-host modes within one process
- Do not reintroduce XMSF-side manager activity aliases, `ManagerUiRedirectActivity`,
  `WelcomeActivity`, or launcher trampolines; cross-process manager navigation must use
  `ManagerUiEntryPoints` and the direct `:mipush` activities.

## 2. Bridge Entry

- Stock entry points: `com.xiaomi.xmsf.push.service.XMPushService` and
  `com.xiaomi.push.service.XMPushService`
- Shared implementation base: `com.xiaomi.xmsf.push.service.MiPushFacadeService` (not a manifest
  component)
- Main work:
  - receive XMSF-facing intents
  - record routing state into `PushRuntime`
  - refresh config state when required
  - forward business intents into the shell ABI facade and private vendor `XMPushServiceCore`

Key source:

- `xmsf/shell/src/main/java/com/xiaomi/xmsf/push/service/MiPushFacadeService.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/push/service/XMPushService.kt`
- `xmsf/shell/src/main/java/com/xiaomi/push/service/XMPushService.kt`

Stock XMSF 7.4.67-C declares only those two public service names. The old product manifest also
declared the facade base and an unused `CompatXMPushService` as private components. Mock replay was
the sole direct facade caller. It now starts the private `XMPushServiceCore` directly because its
empty internal intent is not valid external SDK ingress; both redundant component registrations
were therefore removed while the two stock public entry names remain unchanged.

### External SDK ingress boundary

The public facades support both stock transport forms:

- A bound Messenger request carries `Message.sendingUid`. `ExternalPushIngress` resolves that UID
  to installed packages and requires it to match the package declared by the MiPush payload.
- A legacy `startService` request arrives through `onStartCommand`, where Android does not retain
  the originating UID. `ExternalPushIntentPolicy` therefore validates the public action, target
  package, serialized container/action, payload size, local-control signature, and sanitized
  extras, but cannot authenticate the process that sent the request.

The second limitation is a deliberate stock-SDK compatibility boundary, not an authorization
grant. An arbitrary app that can reach the exported legacy service may forge a payload for another
installed package if it can satisfy those payload checks. Adding a manifest signature permission or
requiring a pre-existing registration would break stock first-registration and legacy SDK flows;
any future hardening must introduce a separate authenticated transport and prove SDK compatibility
before changing the exported route.

## 3. Runtime Spine

- Entry point: `:xmsf:runtime` `PushRuntime` facade (package `io.github.magisk317.mipush.runtime`)
- Main work:
  - queue and drain bridge intents
  - track registration, connection, and channel state
  - replay registration work on boot, network, and account changes
  - track downstream delivery and notification counters
- Current shape:
  - `PushRuntime` is the stable facade used by stock-facing code and manager adapters
  - `AndroidPushRuntime` owns the Android-coupled in-process state, counters, queues, and snapshots

Key source:

- `xmsf/runtime/src/main/java/io/github/magisk317/mipush/runtime/PushRuntime.kt`
- `xmsf/runtime/src/main/java/io/github/magisk317/mipush/runtime/android/AndroidPushRuntime.kt`

### Package data-clear lifecycle

- The system `PACKAGE_DATA_CLEARED` broadcast enters the existing
  `PkgUninstallReceiver`, which retains its legacy component name but routes the 7.4.67-C stock
  action `com.xiaomi.xmsf.push.PACKAGE_DATA_CLEARED` and
  `data_cleared_pkg_name` key into `XMPushServiceCore`.
- The service clears active notifications first, then delegates product state cleanup to
  `PackageDataClearedCoordinator`. The coordinator captures only the confirmed appId, invalidates
  confirmed and pending registration state, profile IDs, notification type, registration secret,
  replay/deduplication state, registration tasks, and old queued packets, and marks the installed
  application unregistered.
- Stock XMSF 7.4.67-C emits an `ActionType.Notification` request with type
  `app_data_cleared` only when `pref_registered_pkg_names` contains a non-blank appId. The project
  follows that wire condition: a pending-only appId is cleared locally but is never represented to
  the server as a confirmed registration.
- Clearing pending-only state is deliberate local hardening beyond stock's observable branch. The
  target application has erased its own registration credentials, so replaying a pre-clear
  registration request or payload would restore state the target can no longer decrypt or manage.
- Old queued packets are discarded before the new lifecycle request is sent or queued. Data clear
  does not use the uninstall/app-absent path and does not mark the still-installed package absent.
- The `app_data_cleared` wire string remains in product-owned `xmsf` code because the frozen MiPush
  SDK 3.7.9 `NotificationType` enum predates the 7.x value. There is no reason to update `pinned` or
  move this product policy into `vendor`.

Key source:

- `xmsf/shell/src/main/java/io/github/magisk317/mipush/receiver/PkgUninstallReceiver.kt`
- `xmsf/push/src/main/java/io/github/magisk317/mipush/push/pipeline/PackageDataClearedCoordinator.kt`
- `xmsf/runtime/src/main/java/io/github/magisk317/mipush/runtime/android/PushRuntimePendingPacketStore.kt`

## 4. Execution Host

- Entry point: `PushRuntimeExecutionBridge`
- Main work:
  - framework self-registration
  - forced app registration
  - pending register task processing
  - downstream payload dispatch
  - notification cancel dispatch
  - connection ensure/reset

Key source:

- `xmsf/shell/src/main/java/io/github/magisk317/mipush/runtime/PushRuntimeExecutionBridge.kt`

## 5. Long Connection Host

- Entry point: shell `com.xiaomi.push.service.XMPushService` facade, forwarding to private vendor
  `XMPushServiceCore`
- Main work:
  - own the long-lived connection stack
  - run reconnect, packet sync, and intent delegates
  - expose lifecycle state back to product-owned code

Key source:

- `xmsf/shell/src/main/java/com/xiaomi/push/service/XMPushService.kt`
- `vendor/src/main/java/com/xiaomi/push/service/XMPushServiceCore.kt`

## 6. Downstream Delivery

- Entry point: `io.github.magisk317.mipush.service.runtime.AppPushMessageProcessor`
- Main work:
  - optionally foreground the target app
  - deliver to target `PushMessageHandler`
  - fall back to broadcast delivery if direct service start fails

Key source:

- `xmsf/shell/src/main/java/com/xiaomi/push/sdk/PushMessageProcessor.kt`

## 7. Notification Publish

- Entry point: `MIPushNotificationPublishHelper`
- Main work:
  - unpack the payload; the external-intent, runtime-observation, hook, and SDK dedupe layers
    remain separate and are applied at their respective call sites
  - apply package-config operations
  - align stock notification behavior for focus, VoIP, SweetTag, grouping, click, and action intents
  - intercept eligible `hyper_type=1` notifications through the target application's stock extension
    service before ordinary notification construction
  - after a real display-message notification attempt, send the stock
    `com.xiaomi.mipush.MESSAGE_ARRIVED` callback; like stock the arrival is not gated by the
    container action (the raw profile matcher only gates legacy SendMessage payloads), and the
    target is addressed by package plus receiver query, not by observed running state
  - Notification containers the display path cannot render are handed off through the same
    `MESSAGE_ARRIVED` callback so the target app can surface its own notification (stock behavior
    for Alipay-pushsdk-style republished payloads); business, mock, and duplicate containers
    never hand off
  - keep explicit `miui.focus.param` on the original notification when remote configuration supplies one
  - leave regular notifications without generated focus extras so they remain visible in the notification shade
  - publish, ignore, wake, or open based on resolved policy

Supporting layers:

- `NotificationController`
- `ExtensionNotificationCoordinator`
- `NotificationManagerEx`
- `NotificationDumpCommandContract`, which owns noredact-first/plain-fallback command order while
  each process validates the dump formats it can actually parse. Runtime silent name probing is an
  explicit opt-in and is not part of the default read path.
- `NotificationIdentityBridge`
- `IslandPreferenceProvider` in the xmsf process, which exposes HyperIsland display flags through caller validation: self/system/root, callers holding the read permission, or the system-installed `com.android.systemui` package.
- `MiPushIslandHook` in the Xposed `com.android.systemui` process, which posts a separate HyperIsland proxy notification for eligible MiPush notifications before MIUI builds its inner notification bean.
- Proxy notification IDs are derived from `(userId, sourcePackage)`, and source ownership still
  uses the full status-bar key, so same-package notifications in owner and cloned users do not
  replace or cancel each other's proxy.
- The known broken-click launcher fallback resolves and creates its `PendingIntent` in the
  notification user's context. If SystemUI cannot obtain that context, it preserves the original
  PendingIntent instead of silently launching the package in the wrong user.
- `UnlockFocusAuthHook` in the Xposed `com.xiaomi.xmsf` process, which relaxes XMSF focus authorization for generated focus payloads.
- `IslandPreferences` in the Xposed module, which periodically reads the xmsf provider so SystemUI injection and XMSF authorization share the same runtime flags.

Delegated notification identity and display policy are deliberately separate concerns:

- a delegated post keeps `pkg` as the target application and `opPkg` as `com.xiaomi.xmsf`; the
  system-server hook resolves the target package's UID for the target user instead of changing the
  operation package to the system;
- a remotely configured `miui.focus.param` remains attached to its original notification;
- a locally generated focus/island proxy is a SystemUI path, and `showNotification=false` controls
  shade presentation rather than suppressing that proxy path.

Base display policy follows the retained stock notification builders before product enhancement:

- on MIUI, `notify_foreground` values other than `1` suppress display while the target application
  is foreground; non-MIUI systems preserve the standard notification;
- initial `when` is local post time, `notification_show_when` defaults to true, and payload ticker
  plus positive timeout seconds are applied. The server message timestamp is not used as the shade
  timestamp;
- message count, show-at-tail, fold timeout, keyguard/float controls, section priority, and disable
  flags are copied with their stock Bundle types. Invalid numeric values are ignored locally and do
  not restore Xiaomi error-report upload;
- MIUI-only count/tail/disable fields are not attached to the non-MIUI ongoing progress fallback.

Sweet notifications are a later stock lifecycle layered on top of the base builder:

- stock XMSF 7.4.67-C style type 5 uses private Xiaomi RemoteViews unavailable to the product, so
  the same alert/left/right/background fields fall back to a standard text or BigPicture card;
  `<ft>` markup is rendered for both title and body;
- on MIUI/XMSF, `remind_status`, `sequence`, clicked state, and a 180--7200 second milepost suppress
  stale or already-consumed reminders. `remind_end`, timeout, replacement, and removal reasons
  1/2/3 follow the stock cleanup boundaries without restoring its upload side effects;
- SCREEN_ON restores keyguard visibility only for the currently tracked reminder. Generic payload
  `enable_keyguard` and `enable_float` values are applied before the style defaults and therefore
  remain authoritative;
- the retained SDK 3.7.9 has no equivalent state machine. This behavior stays in product-owned
  `xmsf` code and is disabled on non-MIUI, where style 5 remains an ordinary standard card.

Native progress and Android 16 promotion are also separate layers:

- `ProgressStyleBuilder` always builds an ongoing, continuously updateable standard progress
  notification first, then uses platform `Notification.ProgressStyle` on API 36+;
- it requests promoted ongoing on Android 16 QPR1+, but does not claim that the request was granted;
- Android 16 / HyperOS `OS3.0.315` `NotificationManagerService.fixNotificationWithChannel(...)`
  checks `POST_PROMOTED_NOTIFICATIONS` with the final delegated target `pkg` and its resolved
  notification UID, not the XMSF operation package;
- therefore XMSF's own manifest permission cannot promote a delegated target application. A denied
  request correctly remains the same ongoing standard progress notification, while an authorized
  target may be promoted by the system without changing notification identity;
- do not rewrite `pkg`/`opPkg`, inject the promoted flag, or consult XMSF's own
  `NotificationManager.canPostPromotedNotifications()` as a substitute for the target-app check.

Extension notification callbacks preserve the stock 7.4.67-C completion boundary:

- eligibility requires HyperOS 3.1+, `hyper_type=1`, the exact extension action, and a service in
  `<target>:pushExtensionService`; the service transactions are oneway while `onFinish` is a
  synchronous callback;
- the initial call is followed by an 8-second expiry warning and a 10-second original-notification
  fallback. The first non-null callback wins and the package connection is released after its final
  pending message;
- accepted title/body/badge/click changes rebuild the payload before the temporary large icon is
  attached to the in-memory notification. `isShowNotification=false` consumes the task without a
  notification or arrival callback;
- package configuration, notification permission, and the product VoIP sequence/end path stay ahead
  of extension binding so a target service cannot bypass local policy or existing VoIP lifecycle;
- stock extension reporting, including delayed `msgUpload_` work, remains disabled with the rest of
  the telemetry boundary.

VoIP handling keeps the independent stock 7.4.67-C presentation and lifecycle predicates:

- only `notification_style_type=6` selects the VoIP notification builder. The separate
  `msg_busi_type=voip` marker owns sequence ordering and `voip_type=0` cancellation, so a malformed
  style-only payload cannot end a call and a business-only control payload cannot select the style;
- sequence state follows stock's process-lifetime, per-package `HashMap`. It is not capped by the
  former product 128-entry LRU, which could evict an active package and then admit an older call;
- `voip_type` is parsed as a number with stock fallback `0`; the former product-only `voice` and
  `video` aliases are not accepted. Delegated metadata uses the effective target package and keeps
  the caller's original string values for `voip_type`, `msg_busi_type`, and `mipush_custom_extra`;
- Xiaomi's private CallKit/heads-up stack is not available on AOSP. The retained full-screen
  fallback is therefore limited to numeric call types `1` and `2`; type `0` and malformed control
  payloads never launch it.

Top-notification behavior follows the newer XMSF runtime rather than the older SDK helper:

- MiPush SDK 3.7.9 defaulted a missing `notification_top_repeat` to true and switched an expired
  notification to the default channel. Stock XMSF 7.4.67-C `t0`/`m2` requires an explicit true
  value and keeps the existing channel when the bounded top period ends;
- valid input requires `period > 0` and `0 <= frequency <= period`. The initial notification uses
  priority `MAX`, group-alert behavior `SUMMARY`, and typed `mipush_org_when`,
  `mipush_n_top_flag`, `mipush_n_top_fre`, and `mipush_n_top_prd` markers. Initial `when` and
  `mipush_org_when` reuse the exact same local post timestamp;
- periodic reposts mutate only `when`, preserving hidden MIUI and delegated-identity fields. Expiry
  sets default priority, updates `when`, removes exactly those four markers, and leaves the channel
  unchanged;
- `TopNotificationCoordinator` owns this lifecycle in product `xmsf` code. Slot/generation
  ownership prevents running or stale jobs from replacing a newer notification, and listener
  removal cancels both product jobs and any dormant vendor-route job without changing `vendor`;
- scheduling repeats the stock MIUI/XMSF, local-marker, and message-ID checks. On non-MIUI systems
  no top job is created; semantic progress remains the standard ongoing progress fallback described
  above.

Application arrival callbacks are independent of XMSF's display result:

- MiPush SDK 3.7.9 sends `MESSAGE_ARRIVED` from the display branch after the notification helper
  returns, including when notification permission prevents a visible post; stock XMSF 7.4.67-C
  preserves that order and resolves `miui_package_name` before checking and addressing the target;
- 7.4.67-C does not treat a lone `<target>:pushExtensionService` process as a running target. The
  target must also declare the package-scoped receiver and receives the raw `mipush_payload` under
  `<target>.permission.MIPUSH_RECEIVE`;
- local `PackageConfig` ignore is a MiPushFramework display policy, so it must not suppress the
  app callback. Business, pass-through, duplicate/replay, foreground-suppressed, mock, and direct
  helper paths do not synthesize this callback;
- non-display Notification containers (no metaInfo title/description or pass-through shaped, e.g.
  Alipay pushsdk channels that ask XMSF to present a message they already received) are exempt from
  the foreground suppression and hand off through the same callback, because no other delivery
  route exists for them;
- an eligible extension notification is the exception to immediate post-attempt timing: the callback
  or fallback owns publication, so arrival uses the extension-resolved payload at that later point;
  app-requested notification suppression also suppresses arrival, matching stock `i0.f(...)` entry;
- the callback is dispatched only by the real runtime observer path. This keeps stored-event mock
  replay from waking applications and lets apps such as Weather render their own native surface.

Stored-event replay contract:

- The manager calls `ManagerEventGateway.mockMessage(...)` as a suspend operation and receives one
  of `BlockedByPermission`, `Dispatched`, `Posted`, `FailedChannelDisabled`, or `Failed` from the port-owned
  `MockReplayOutcome` contract.
- Modern mock replay runs the notification policy/publish path synchronously on the manager's IO
  coroutine. `Posted` is returned only after `NotificationController.publish(...)` receives a
  successful notification-manager post.
- A blocked application, denied notification operation, or focus filter returns
  `BlockedByPermission`. A legacy reflection path whose final post cannot be observed returns
  `Dispatched`; payload/service/publish failures return `Failed`.
- Runtime observation logs use the same outcome names, and the manager event page presents all five
  results explicitly. Do not restore the old Boolean contract, which only proved that a payload was
  parseable and a replay attempt was started.

Key sources:

- `xmsf/shell/src/main/java/io/github/magisk317/mipush/service/runtime/MIPushNotificationPublishHelper.kt`
- `xmsf/shell/src/main/java/io/github/magisk317/mipush/service/runtime/ExtensionNotificationCoordinator.kt`
- `xmsf/shell/src/main/java/io/github/magisk317/mipush/bridge/MiPushRuntimeObserverBridge.kt`
- `xmsf/shell/src/main/java/io/github/magisk317/mipush/notification/NotificationManagerEx.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/provider/IslandPreferenceProvider.kt`
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
  - `com.xiaomi.xmsf.provider.MiCloudSettingsProvider`
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
    state, and the stock-aligned keep-alive runtime
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
  stock `KASwitch=142`. It then uses foreground-activity events from a dynamically resolved
  `IProcessObserver`, or 60-second process polling when registration fails, to drive the same
  owner/calm-down/retry state machine. `KeepAliveEnvironment` applies stock strategy device,
  memory, temperature, battery, CTS, and monkey gates before binding. `OnetrackSwitch=140` and
  `need_stat` remain non-uploading compatibility state while telemetry is disabled.
- `NotificationListener` keeps stock 7.4.67-C current-user/`USER_ALL` filtering before focus and
  removal dispatch. It does not restore the stock LNS/LNC, notification-pull, or OneTrack upload
  collectors; with telemetry disabled, only MiPush-owned posted/removed events enter local runtime
  diagnostics, while native focus records still reach the local focus collection filter.
- `TelemetryDisabler` runs before the main/non-main process split. Stock 7.4.67-C `va.g.e(...)`
  collects package traffic, network type, bytes, and cellular IMSI without an OnlineConfig gate; the
  retained vendor implementation therefore defaults to stock-enabled but is disabled by this
  product-owned startup policy, which also purges queued data and an older `traffic.db`.
- `TrafficProvider` remains discoverable for stock compatibility but exposes only an empty database
  while collection is disabled. `XMSFUploadReceiver` is a stateless component facade, and
  `NotificationEventReceiver` keeps stock field validation plus local diagnostics without OneTrack,
  tiny-data, or product OTLP export. Stock log, installed-app, and cognition components remain
  deliberately absent because no push-mainline consumer is proven.
- Hand-written Binder facades preserve descriptor attachment, local/remote resolution, transaction
  ordering, and one-way flags. `HttpService` returns stock-shaped local responses instead of
  forwarding telemetry; `BindMiCloudPushService` consumes `key_to_bind_intent` and invokes the
  remote worker.
- `MiCloudSettingsProvider` preserves the stock one-key cursor, platform-signature/CLOUD_MANAGER
  read policy, write allowlist, and synchronous commit result. It deliberately keeps fresh installs
  usable when the legacy migration authority is absent. Stock 7.4.67-C defines no account or token
  `call()` methods on this authority, so the older product extensions were removed.

Key source:

- `xmsf/shell/src/main/java/com/xiaomi/xmsf/stock/StockSurfaceSupport.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/stock/StockProfileIdStore.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/stock/StockPushSupport.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/stock/StockChannelSupport.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/stock/StockNotificationMetadataBridge.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/provider/MiCloudSettingsProvider.kt`
- `xmsf/runtime/src/main/java/io/github/magisk317/mipush/service/runtime/KeepAliveRuntimeAdapter.kt`
- `xmsf/runtime/src/main/java/io/github/magisk317/mipush/service/runtime/KeepAliveEnvironment.kt`
- `xmsf/runtime/src/main/java/io/github/magisk317/mipush/service/runtime/ProcessObserverCompat.kt`
- `xmsf/shell/src/main/java/io/github/magisk317/mipush/telemetry/TelemetryDisabler.kt`
- `vendor/src/main/java/com/xiaomi/smack/util/TrafficUtils.kt`

Evidence rules:

- Provider calls must be tested through the real transport so caller UID/package and nested Bundle
  shape cannot be supplied by caller-controlled extras.
- Hand-written Binder facades must pin descriptor, transaction number, reply type and one-way flags.
- Restored `signatureOrSystem` declarations do not replace method-level caller allowlists.
- Device dumps remain reference inputs outside the Gradle graph; missing raw artifacts must remain
  explicit evidence gaps.
- Final notification identity, channel, focus, island, XSpace and keep-alive behavior requires an
  installed `:xmsf` build plus hook logs and relevant `dumpsys` output.

## 9. Account / Cloud Boundary

- Stock 7.4.67-C `AccountChangedReceiver` is a component-compatible no-op. The older product path
  replayed registrations and started activation work on account broadcasts; that non-stock path is
  no longer part of `PushRuntime`.
- Stock 7.4.67-C's post-registration account manager resolves its desired alias to null in DEX and
  performs no first-run alias mutation. XMSF therefore does not read `AccountManager.accounts` or
  expose service tokens through the settings provider. Normal explicit MiPush alias commands remain
  in the message processor.
- Xiaomi account/token services and their signature/account-visibility privileges remain owned by
  `com.xiaomi.account`. `BindMiCloudPushService` is the separate XMSF-to-cloud worker compatibility
  surface.

Key source:

- `xmsf/shell/src/main/java/com/xiaomi/xmsf/push/service/receivers/AccountChangedReceiver.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/provider/MiCloudSettingsProvider.kt`
- `xmsf/shell/src/main/java/com/xiaomi/xmsf/sync/BindMiCloudPushService.kt`

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
- account / cloud boundary

Only bypass `PushRuntime` when the stock feature is strictly self-contained and does not participate in routing, lifecycle, or shared compatibility state.

## Adapter Boundary

`xmsf/shell/src/main/java/io/github/magisk317/mipush/service/runtime` and
`xmsf/shell/src/main/java/io/github/magisk317/mipush/bridge` are the allowed product-owned adapters
that may touch vendor/runtime and protocol types directly. UI, settings, and feature code should
go through these adapters or through `core` facades instead of importing deep `com.xiaomi.*`
transport/protocol classes.

Settings/runtime actions such as foregrounding the push service, resetting the XMPP connection, and
reading the current stock XMPP host are routed through `RuntimeSettingsAdapter`. Root and shell
actions are routed through `AppRootAccessFacade`, with hook-side root probes kept in the xposed module's
bounded runner.
