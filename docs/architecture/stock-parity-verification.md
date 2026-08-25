# MiPushFramework Stock Parity Verification

Status: deferred device and cross-process verification. Source-level claims below were refreshed
against `beta` at commit `d8e4cf2d0` on 2026-08-11. A checked source file or JVM test is not
device evidence; items that require a real APK, cross-UID caller, `dumpsys`, or phone-side logs
remain open until their command and observed output are recorded in the completion record.

## Scope

This is the single deferred-verification list for stock XMSF parity work in:

- `/home/lzc/wqk/push/MiPushFramework`
- `/home/lzc/wqk/push/device_dumps`

Run this list only after the implementation work for the target pass is complete. Keep source
verification and device verification separate, and record the tested commit, APK identity, command,
result, and evidence path for every item. Do not push as part of verification.

## Current source audit

The following statements are supported by the current tree and existing targeted tests, but do not
close the device gates below:

- `app/.../MiPushHostApp.kt` starts manager dependencies only after the XMSF runtime host is ready
  and only in the main process; `mipush/.../App.kt` owns the remote-host mode.
- `:xmsf:shell` remains the installable runtime library surface; `:xmsf:runtime` is an internal
  runtime-core library only; `:app:assembleNormalDebug` produces
  the installable `com.xiaomi.xmsf` APK. The `mipush` APK is a separate manager/client artifact.
- `EVENT` and `REGISTERED_APPLICATION` carry `user_id`, and registered applications use the
  `(user_id, pkg)` identity. This is source/database evidence, not proof of complete XSpace support.
- Notification channel reads use the shared `--noredact`-first contract and manager channel
  operations use DTO/domain commands. The HyperOS UID-aware parser and failure-state tests exist.
- Stock notification extension, VoIP, top, sweet, registration ingress, telemetry boundary, and
  intelligent-heartbeat behavior have focused source tests recorded in this file's historical
  build notes; visible notification, Binder, and network behavior still needs device evidence.

The current implementation worktree also contains unrelated user changes in
`xmsf/service/XMPushServiceAbilityAssembler.kt` and `xposed/hook/systemui/`; do not include those
changes in a parity result unless they are intentionally part of the tested commit.

The XMSF build installed on 2026-07-26 before the device was disconnected proved that the then-current
`:app` artifact was installable. It is not final evidence for later commits and must be replaced by a
fresh final build.

Incremental build gates completed after the device was disconnected:

- `8b199c76 fix(xmsf): enforce telemetry collection boundary` passed
  `./gradlew :app:assembleNormalDebug`;
- `92910ae8 fix(xmsf): keep stock event facade local` passed
  `./gradlew :app:assembleNormalDebug`;
- `87994653 docs(xmsf): record telemetry compatibility boundary` passed
  `./gradlew :app:assembleNormalDebug`;
- `0933d02e test(xmsf): fix unit test source compatibility` passed
  `./gradlew :app:assembleNormalDebug`;
- `830a9257 feat(notification): restore stock extension callbacks` passed
  `./gradlew :app:assembleNormalDebug`;
- `5b2147af fix(notification): defer extension arrival callback` passed 17 focused extension/arrival
  tests and `./gradlew :app:assembleNormalDebug`;
- `49aadf52 docs(notification): record stock extension lifecycle` passed
  `./gradlew :app:assembleNormalDebug`;
- `2e607ea7 fix(notification): align VoIP lifecycle predicates` passed 38 focused VoIP/action tests
  and `./gradlew :app:assembleNormalDebug`;
- `af4d68dc feat(notification): restore stock top notification lifecycle` passed seven focused top
  tests, two listener-policy tests, both xmsf detekt tasks, and
  `./gradlew :app:assembleNormalDebug`;
- `f28b66b3 fix(notification): gate stock top lifecycle scheduling` passed eight focused top tests
  and `./gradlew :app:assembleNormalDebug`. Robolectric reported only its known post-success native
  font temp-directory cleanup warning;
- `b37250b1 fix(notification): retain stock VoIP sequence state` passed all eight focused VoIP
  tests, including over-128-package retention, and
  `./gradlew :app:assembleNormalDebug`;
- `23ae363b docs(notification): record VoIP and top lifecycle parity` passed
  `./gradlew :app:assembleNormalDebug` before its local commit;
- `72de4c83 fix(notification): align stock display policy` passed the focused presentation tests and
  `./gradlew :app:assembleNormalDebug`;
- `db98aa22 refactor(xmsf): remove redundant service components` passed the component contract tests
  and `./gradlew :app:assembleNormalDebug`;
- `393faf99 fix(notification): share stock top post timestamp` passed the focused top tests and
  `./gradlew :app:assembleNormalDebug`;
- `cfe27f48 fix(xmsf): bootstrap replay through private core` passed the private-core bootstrap test
  and `./gradlew :app:assembleNormalDebug`;
- `3c3aad4e feat(notification): restore stock sweet lifecycle` passed the focused sweet lifecycle,
  style fallback, presentation, top/VoIP, listener, and component tests plus
  `./gradlew :app:assembleNormalDebug`;
- `bc81c0d5 docs(notification): record stock presentation parity` passed
  `./gradlew :app:assembleNormalDebug` before its local commit.

- `ed0962d0 fix(pinned): add stock 7.x intelligent heartbeat ConfigKeys` plus the subsequent
  vendor stable-HB port passed focused
  `StableIntelligentHeartbeatStrategyTest` and `AlarmManagerTimerTest` (11 tests). Full
  `:app:assembleNormalDebug` remains part of the final deferred verification pass.

## 1. Repository And Archive Gates

- [ ] Confirm `MiPushFramework` has only intended local commits and no uncommitted implementation
  changes: `git status --short --branch` and `git log --oneline --decorate`.
- [ ] Run `git diff --check` against the final local commit range.
- [ ] Rebuild the device-dump index:
  `python3 scripts/build_artifact_index.py --check` from `wqk/push/device_dumps`.
- [ ] Expected archive result remains `raw_artifacts=41 unique_sha256=40 coverage_errors=0`, unless
  later evidence collection intentionally adds an indexed artifact.
- [ ] Confirm every new stock-derived behavior has a versioned comment or adjacent evidence document
  explaining the stock behavior, the old project behavior, and why the change was needed.
- [ ] Confirm no OneTrack, LNS/LNC, tiny-data, log upload, installed-app collection, or other excluded
  telemetry was restored indirectly.

## 2. Final Build And Static Tests

Run only after all implementation commits are complete:

```bash
cd /home/lzc/wqk/push/MiPushFramework
./gradlew \
  :xmsf:shell:testNormalDebugUnitTest \
  :xmsf:shell:compileNormalDebugKotlin \
  :xmsf:shell:detekt \
  :xmsf:shell:detektNormalDebugUnitTest
./gradlew :app:assembleNormalDebug
```

- [ ] All unit tests, Kotlin compilation, and both detekt tasks pass.
- [ ] Re-run the full unit suite in the final pass. Commit `0933d02e` fixed the test-source
  compilation blockers, after which 719 tests executed. Three focused top/VoIP regressions were
  added afterward. `AccountChangedReceiverTest` and
  `XMSFUploadReceiverTest` then hit Robolectric native-runtime initialization with
  `FileSystemAlreadyExistsException`; each passes alone, so confirm a clean/serialized final run
  before classifying the shared-runtime failure as infrastructure-only.
- [ ] The installable artifact comes from
  `app/build/outputs/apk/normal/debug/*_normal_xmsf_*_debug.apk`.
- [ ] Do not install an `xmsf` AAR or the separate
  `mipush/build/outputs/apk/debug/*MiPush*_debug.apk` client artifact by mistake.
- [ ] Transfer the APK to the Mac with incremental rsync and verify size plus SHA-256 before install.
  Do not use SSH adb-server port forwarding.

## 3. Install And Background Startup

Do not launch an Activity during this verification section.

- [ ] Install the final XMSF APK with `adb install -r -g`.
- [ ] Verify `adb shell pm path com.xiaomi.xmsf` resolves to the updated data APK.
- [ ] Verify `versionName`, `versionCode`, and `lastUpdateTime` with
  `adb shell dumpsys package com.xiaomi.xmsf`.
- [ ] Confirm both the main XMSF process and the expected `:services` subprocess start in the
  background without an initialization crash.
- [ ] Confirm runtime logs report the packaged `MiPushHostApp`/`MiPushFrameworkApp` startup path and
  that manager Koin is not bootstrapped inside the XMSF host.
- [ ] Confirm the runtime execution bridge becomes ready and the long-connection service can reach
  its normal connected/bound state.
- [ ] Inspect the installed manifest/component list. Confirm only the two stock XMPushService names
  are exported SDK ingress, `MiPushFacadeService` and `CompatXMPushService` are not components, and
  product `XMPushServiceCore` remains private.
- [ ] Trigger manager mock replay without launching an Activity. Confirm it starts private
  `XMPushServiceCore` directly, is not rejected as an empty external-ingress request, and does not
  make the core addressable from a cross-UID explicit service intent.
- [ ] Upgrade XMSF while delegated notifications are active. Before and after self-update revival,
  record target package, operation package, UID/user, tag/ID, channel, content intent, and delete
  intent; confirm revival does not move a target-owned record under XMSF or another user.
- [ ] Measure cold background startup with `ProactiveMiPushRegistrar` enabled. Confirm its package
  scan and optional registration requests are the documented product adaptation, emit no inventory
  or telemetry upload, and remain independent of normal explicit SDK registration.

## 4. Keep-Alive Runtime

Use a disposable, same-signature probe APK with no Activity. It should expose only a background
receiver and a bindable test service. Remove it and its strategy after the test.

- [ ] Confirm the final manifest requests `android.permission.SET_ACTIVITY_WATCHER`.
- [ ] Install the probe and submit one valid stock 7.4.67-C strategy through
  `KeepAliveConfigService`; do not edit production preferences as a substitute for the Binder path.
- [ ] Confirm `ServiceBoxService` resolves stock `KASwitch=142` before the strategy becomes active.
- [ ] Change `KASwitch=142` and `OnetrackSwitch=140` through the normal OnlineConfig update path
  after ServiceBox is already connected. Confirm main-process callback `105` reaches
  `ISubProcBridge.notifyOnlineConfigChanged()` and the subprocess refreshes both values without a
  restart; OneTrack must remain compatibility state only.
- [ ] Confirm one of the two supported process sources becomes active:
  `ProcessObserverCompat` registers successfully, or permission/transaction rejection is logged and
  60-second polling is scheduled.
- [ ] Use an already-foreground system process as the trigger. Do not bring the probe or another app
  to the foreground.
- [ ] Confirm calm-down timing, bind success, binding owner, owner transfer, and unbind when the owner
  leaves the foreground.
- [ ] Confirm a failed bind retries at the stock interval and stops at the configured retry limit.
- [ ] Confirm an unsupported-device strategy update removes the accepted strategy and unbinds the
  target.
- [ ] Confirm `OnetrackSwitch=140` and `need_stat` remain compatibility state only and produce no
  telemetry upload.

## 4a. Intelligent Heartbeat

- [ ] After installing a build that includes the stable HB port, leave XMSF connected on Wi-Fi and
  confirm logs show `[HB] ping interval:600000` (or the current OC/learned value) rather than only
  the fixed SmackConfiguration path.
- [ ] Force repeated client ping timeouts on one Wi-Fi network (NAT loss or blocked pong) at least
  `IntelligentHeartbeatNATCountInt` (default 3) times without changing networks. Confirm
  `hb_record` stores `HB_W-*` = 235000 with a dead-time key and the next alarm interval becomes
  235s. Confirm no category_hb_* / OneTrack upload leaves the device.
- [ ] Toggle airplane mode or switch mobile/Wi-Fi and confirm `[HB] network changed` updates the
  net id; mobile short intervals remain off unless OC 119 is enabled.
- [ ] Push or inject OC updates for ConfigKey 116/118/130/143/145 and confirm interval selection
  follows the new gates without treating the IDs as unknown.
- [ ] Confirm `Alarm.refreshPingInterval` re-registers when the learned interval changes while an
  alarm is already alive.

## 5. Registration And External SDK Ingress

Use a disposable MiPush SDK probe package and collect facade/core service logs without launching its
Activity. Exercise exported ingress from the probe UID rather than substituting same-process calls.

- [ ] Send the stock action-less wake request with only `mipush_app_package`. Confirm the facade
  accepts it, preserves a `null` action when forwarding to the private core service, and does not
  fabricate a payload or `ACTION_START` action.
- [ ] Send two valid registration requests for the same package in rapid succession with identical
  payloads, then repeat with different payloads. Confirm registration is never transport-dropped by
  either the facade or lifecycle pending-start bridge; registration state recording may still
  coalesce its own bookkeeping independently.
- [ ] For both `SEND_MESSAGE` and `UNREGISTER_APP`, confirm the first request is admitted and an
  identical complete serialized payload from the same package is dropped within 60 seconds.
- [ ] Confirm the dedupe key is package plus MD5 of the complete payload: a different payload from
  the same package and the same payload from a different package must both be admitted.
- [ ] Verify the stock 7.4.67-C expiry ordering. The first matching request after more than 60
  seconds is still reported as a duplicate while expiring the old entry; the immediately following
  identical request is admitted. Confirm an expired hit does not refresh the old timestamp.
- [ ] Confirm payload dedupe runs once at the product facade and is not repeated by the lifecycle
  pending-start bridge or another downstream transport layer.
- [ ] Exercise the legitimate stock SDK controls end to end: clear notification, set notification
  type, disable push, disable push messages, and enable push messages. Verify each reaches the
  expected downstream handler and changes only the disposable package's state.
- [ ] For disable/enable push-message actions, verify the serialized container is an unencrypted
  `ActionType.Notification` with the matching stock notification type; mismatched action/type pairs
  must be rejected.
- [ ] For set-notification-type, verify the package/type signature with both the typed and reset
  forms. A bad signature or mismatched local/outer package must be rejected.
- [ ] Confirm caller UID ownership, installed package, outer package, serialized container package,
  action/container pairing, non-empty payload, and 512 KiB payload limit are all enforced from a
  real cross-UID caller.
- [ ] Add unknown or forged internal extras to every admitted request and confirm only the
  action-specific stock allowlist reaches the private core service.
- [ ] Confirm `SEND_TINYDATA` is rejected as `telemetry_disabled` and is never forwarded.
- [ ] Confirm `SEND_MESSAGE` with `mipush_message_cache_collection=1` is rejected as disabled
  notification-exposure telemetry, while collection value `0` follows the normal validated message
  path. No blocked telemetry request may be silently converted into ordinary app traffic.
- [ ] Confirm malformed, mismatched, private-maintenance, and telemetry requests leave no pending
  packet, registration mutation, notification mutation, or network send behind.
- [ ] With chid 5 connected and bound, submit a registration and confirm it is sent once without
  remaining in the pending-registration map or replaying on the next bind.
- [ ] Repeat while disconnected and while chid 5 is unbound. Confirm the latest payload is cached,
  connection/bind is requested, and the payload is flushed exactly once after bind.
- [ ] Capture a registration arriving while chid 5 is already binding. Compare the installed
  behavior with stock `7.4.67-C f0`, which neither sends nor newly caches in that state, and decide
  from runtime evidence whether an explicit product retry is required.
- [ ] Force account creation failure with at least one already cached registration. Confirm one
  payload-bearing error broadcast per pending package, no additional broadcast to
  `com.xiaomi.xmsf`, and map cleanup only for entries whose notification completed.
- [ ] Separately force account creation failure for a directly submitted, not-yet-cached request.
  Record the stock-compatible result: `7.4.67-C f0` calls the pending-map error path before the
  current request can enter that map. Treat any missing app callback as a documented stock
  aftereffect, not proof that the duplicate XMSF fallback should be restored.
- [ ] During a failed pending-registration flush, enqueue a newer payload for the same package.
  Confirm retry sends the newer payload and never overwrites it with the stale failed entry.
- [ ] Deliver registration results with: success plus non-empty `regSecret`, success without
  `regSecret`, and nonzero error with a secret. Only the first may persist the confirmed appId and
  secret; both `pref_registered_pkg_names` and `mipush_apps_scrt` must describe the same result.

## 6. Package Lifecycle

Use only a disposable package. Never clear or uninstall a real user application's data.

- [ ] Register the probe through the real MiPush registration ingress, or seed a controlled
  confirmed registration while XMSF is stopped and then restart only background services. Record
  which method was used.
- [ ] Queue old registration and message packets for the probe, then run `pm clear` on the probe.
- [ ] Confirm the system `PACKAGE_DATA_CLEARED` broadcast reaches `PkgUninstallReceiver`, which
  forwards `com.xiaomi.xmsf.push.PACKAGE_DATA_CLEARED` and `data_cleared_pkg_name`.
- [ ] Confirm active delegated notifications, confirmed/pending registration, notification type,
  profile IDs, local app registration state, regSec, last-receive time, dedupe state, registration
  tasks, and old queued packets are removed.
- [ ] Confirm data clear marks the package unregistered but not absent/uninstalled.
- [ ] With a confirmed non-blank appId, decode or capture the emitted request and verify:
  `ActionType.Notification`, request container, target package/appId, type `app_data_cleared`, and
  `requireAck=false`.
- [ ] Repeat with pending-only state and confirm local state is cleared without emitting an
  `app_data_cleared` request.
- [ ] Repeat package removal with the disposable package and confirm the separate app-absent path,
  including queued send while channel 5 is unavailable.
- [ ] Confirm package replacement removal is ignored, on-demand extension-service resolution still
  sees a replaced target, and package add/replace does not restore OnePush or extension telemetry.

## 7. Notification Mainline

- [ ] With a disposable target foreground on MIUI, send `notify_foreground` missing, `0`, malformed,
  and `1`. Confirm only `1` permits the shade notification; repeat on non-MIUI and confirm the
  standard notification is not suppressed by the MIUI-only foreground policy.
- [ ] For a natural push, confirm initial `when` is local post time rather than server `messageTs`,
  `notification_show_when` defaults true, explicit false hides the timestamp, ticker is retained,
  and only a positive numeric `timeout` becomes `timeoutAfter` in milliseconds. Malformed values
  must not crash or generate Xiaomi error-report traffic.
- [ ] With `dumpsys notification --noredact`, verify the exact Bundle types for message count,
  show-at-tail, fold timeout, keyguard/float, section priority, and disable flags. Confirm the
  MIUI-only count/tail/disable fields are absent from the non-MIUI ongoing progress fallback and
  explicit generic `enable_keyguard`/`enable_float` values remain authoritative.
- [ ] For the first valid bounded-top post, confirm visible `when` and typed `mipush_org_when` are
  byte-for-byte the same millisecond value rather than two adjacent clock samples.
- [ ] Send style-5 content with alert/left/right/background fields and `<ft>` markup in both title
  and body. Confirm private Xiaomi RemoteViews are not required and the product renders a readable
  standard text/BigPicture card on MIUI and non-MIUI.
- [ ] Exercise sweet sequences older than, equal to, and newer than the stored positive sequence.
  Confirm only the older value is suppressed. Click/cancel the reminder, resend the same status
  while its milepost is live, and confirm suppression occurs only when no matching active reminder
  remains.
- [ ] Verify sweet timeout values below 180, within range, and above 7200 seconds; replacement by a
  normal notification; `remind_end`; and removal reasons 1, 2, 3, and 19. Confirm old timeout or
  removal callbacks cannot cancel a newer status/sequence in the same notification slot.
- [ ] With the screen initially off, post changed and repeated sweet statuses and verify typed
  keyguard/float policy, target-foreground behavior, explicit payload overrides, and SCREEN_ON
  restoration only for tracked reminders whose keyguard flag is false.
- [ ] Restart XMSF while a sweet reminder is active. Record the stock-compatible process-local job
  loss/persistent-milepost aftereffect before considering recovery scheduling, and confirm no
  OneTrack, tiny-data, notification-exposure, or error upload occurs on any sweet lifecycle path.
- [ ] Replay a natural server push and confirm display-message arrival callback ordering, target
  package resolution, receiver permission, and the exclusion of a lone `:pushExtensionService`
  process.
- [ ] Install a disposable target extension service using the exact stock action and
  `<target>:pushExtensionService` process. Confirm `hyper_type=1` is intercepted only on the HyperOS
  3.1+ boundary and ordinary/ineligible notifications publish immediately without binding it.
- [ ] Confirm the extension service receives the initial oneway call, the 8-second expiry warning,
  and a 10-second original-notification fallback. Verify the first valid callback wins, null/late
  callbacks do not consume the fallback, and the package connection is released after its last
  pending message.
- [ ] Return title/body/badge/click/image changes and confirm the rendered notification uses them,
  the click and `MESSAGE_ARRIVED` payload uses the rebuilt serialization without `temp_large_icon`,
  and the in-memory notification honors the image only below 49,152 pixels.
- [ ] Return `isShowNotification=false` and confirm neither notification nor `MESSAGE_ARRIVED` is
  emitted. Repeat timeout/bind/dispatch failure and confirm the original notification plus original
  arrival payload is restored exactly once.
- [ ] Confirm package-config ignore, denied notification permission, stale/end VoIP handling, and
  extension suppression retain their documented distinct outcomes and do not restore `msgUpload_`,
  OneTrack, tiny-data, or another extension telemetry path.
- [ ] Send the four VoIP predicate combinations independently: style-only type 6, business-only
  `msg_busi_type=voip`, both markers, and neither. Confirm only style type 6 selects the VoIP
  builder, while only the business marker participates in sequence filtering and type-0
  cancellation.
- [ ] Confirm malformed/alias `voip_type` values parse as stock type 0, `voice`/`video` are not
  accepted aliases, and only numeric types 1/2 can receive the documented AOSP full-screen
  fallback. Verify `voip_type`, `msg_busi_type`, and `mipush_custom_extra` remain strings and the
  effective delegated target package is used in both target metadata keys.
- [ ] Seed one package with a newer VoIP sequence, then process more than 128 other package keys and
  resend the original package's older sequence. Confirm the older message remains rejected until
  process restart, while an equal sequence remains admitted, matching stock's process-lifetime
  per-package map rather than the removed LRU.
- [ ] Post a valid top notification with explicit `notification_top_repeat=true`, positive period,
  and frequency within the period. With `dumpsys notification --noredact`, record priority,
  group-alert behavior, channel ID, `when`, and the typed `mipush_org_when`, `mipush_n_top_flag`,
  `mipush_n_top_fre`, and `mipush_n_top_prd` extras before and after at least one periodic repost.
- [ ] At top-period expiry, confirm priority becomes default, `when` advances, all four local top
  markers disappear, and the original channel ID and target/delegation identity remain unchanged.
  Repeat with frequency 0, frequency equal to period, missing/false repeat, period 0, negative
  values, malformed numbers, and frequency greater than period.
- [ ] While a top update is due, replace the same package/tag/id first with a different message ID,
  then with the same message ID and newer original-when marker. Confirm no stale job overwrites or
  cancels the replacement. Replace it with a non-top notification in the same slot and confirm the
  old lifecycle relinquishes ownership. Swipe the active top notification away and confirm it is
  not reposted.
- [ ] Confirm the top lifecycle starts only on MIUI/XMSF and that a standard non-MIUI notification
  carrying restored/foreign local marker extras cannot start the keyed update job.
- [ ] Restart XMSF while a top notification is active. Record the stock-compatible in-memory
  scheduler result and confirm restart creates neither a duplicate job nor channel migration. If
  the existing record remains elevated without a job, classify that as a stock aftereffect before
  deciding whether a product-only recovery scan is warranted.
- [ ] Confirm remote `miui.focus.param` remains on the original notification and does not create a
  generated HyperIsland proxy or unwanted status-bar icon.
- [ ] Confirm an ordinary notification without remote focus metadata remains a normal shade
  notification and does not gain generated focus extras.
- [ ] Confirm product-configured island proxy suppression affects only the intended proxy/original
  relationship and does not suppress unrelated notifications.
- [ ] Confirm delegated notification identity remains target `pkg` plus XMSF `opPkg`, with the target
  UID and user resolved by system server.
- [ ] Confirm channel mapping, permission mask, grouping, click/action intents, notification clear,
  and XSpace target-user behavior on the installed build.
- [ ] On AOSP/non-MIUI, verify live-update downgrade as a continuously updated ongoing standard
  progress notification.
- [ ] On Android 16+, verify `Notification.ProgressStyle` is used when available. Verify promoted
  ongoing is granted only when the delegated target has `POST_PROMOTED_NOTIFICATIONS`; denial must
  leave the same standard progress notification intact.

## 8. Providers, Binder, Account, And Cloud

- [ ] Exercise Profile, Channel, PushSupport, PushControl, and notification metadata providers from
  a real cross-UID caller. Verify caller identity, nested Bundle shape, result types, persistence,
  and downstream consumers.
- [ ] Call `PushCommonProvider/is_push_support` with a non-empty `arg`, unrelated extras, missing and
  wrong-type flags, and supported/unsupported integer flags. Confirm `arg`/unrelated fields are
  ignored, invalid input returns only `is_supported=false`, and an unknown method returns an empty
  `Bundle` without invented `code` or `msg` fields.
- [ ] Query `PushControlProvider` with canonical and noncanonical URI/projection/selection shapes.
  Confirm every call returns the same fixed one-row cursor and `insert()` returns its input URI
  without persisting caller values.
- [ ] Exercise `MiCloudSettingsProvider.query()` from an unauthorized cross-UID caller and confirm
  stock-shaped denial: a one-row cursor containing `null`, not a thrown `SecurityException`.
- [ ] Confirm cloud-setting reads are authorized only by `CLOUD_MANAGER` or a caller whose signing
  certificate matches the platform/system UID certificate; an arbitrary root, self, or same-name
  caller must not become a substitute for the stock signature check.
- [ ] Exercise `MiCloudSettingsProvider.insert()` success and persistence failure paths. Confirm the
  synchronous result is durable before return, the input URI is returned only on success, and
  failure returns `null`.
- [ ] Confirm the retained fresh-install migration policy does not recreate the legacy
  migration-completion deadlock while reads and writes still preserve the documented stock-shaped
  contract after startup.
- [ ] Call the former `getAvailability` and `getServiceToken` method names on
  `MiCloudSettingsProvider` and confirm the inherited stock-shaped behavior returns `null`; no
  account metadata, token, custom error Bundle, or unknown-method string may be exposed.
- [ ] Exercise MainProcBridge, ServiceBox, KeepAlive, HTTP, Stat, and cloud-bind Binder surfaces from
  a real remote process. Verify descriptors, runtime transaction IDs, one-way flags, and reply types.
- [ ] Bind `HttpService` from an ordinary unsigned probe and confirm no custom permission is needed.
  Exercise recognized and unrecognized URLs and confirm all responses remain local with no network
  or telemetry side effect.
- [ ] Bind exported `StatService` from the probe and confirm the stock null-Binder result leaves no
  stored event or upload work.
- [ ] Confirm the same-package `:services` process can bind `ServiceBoxService`, while an external
  probe cannot bind it. Verify online-config/keepalive refresh still works through the internal
  bridge.
- [ ] Send `LOGIN_ACCOUNTS_CHANGED` in the background and confirm the stock component receives it
  without replaying app registrations, starting push activation, reading Android accounts, or
  adding/removing a MiPush server alias.
- [ ] Start exported `MiuiPushActivateService` with arbitrary, `SCAN`, `ACCOUNT_CHANGE`, and
  `APP_REGISTERED` actions. Confirm each worker stops without enumerating installed packages,
  mutating registration state, or launching a target application service. Explicit SDK
  registration must still work through the normal validated facade; any separately observed cold
  startup package scan must be attributable only to the documented `ProactiveMiPushRegistrar`
  adaptation, not this inert stock service.
- [ ] Complete one XMSF self-registration command and confirm it does not derive a server alias from
  the Xiaomi account. Explicit application alias commands must continue through the normal MiPush
  message processor unchanged.
- [ ] Confirm account/token access remains owned by the platform account package and is not
  fabricated or proxied through the XMSF settings authority.
- [ ] Confirm `BindMiCloudPushService` consumes `key_to_bind_intent` and invokes the expected cloud
  worker when the installed platform package allows it.
- [ ] Confirm stock-shaped HTTP/Stat compatibility responses do not forward telemetry.

## 9. Telemetry And Collection Boundary

- [ ] Upgrade from a build that has populated `traffic.db`. Start XMSF in the background and confirm
  `TelemetryDisabler` removes the old database before any new push traffic is processed.
- [ ] Generate sustained send and receive traffic for a disposable package, wait beyond the stock
  five-second flush interval, and confirm no traffic row or IMSI is retained.
- [ ] Query `com.xiaomi.push.providers.TrafficProvider/traffic` from a permissioned cross-UID probe.
  Confirm the stock cursor schema remains usable but contains no historical or newly collected rows;
  an `update_imsi` call must not make later cellular records possible.
- [ ] Send valid and malformed `XMSF_UPLOAD_ACTIVE` broadcasts. Confirm no `stock_surface` mutation,
  tiny-data item, OneTrack event, OTLP event, service start, or network request occurs.
- [ ] Send valid and malformed `NOTIFICATION_ACTIVE` broadcasts. Confirm only non-sensitive local
  runtime/health diagnostics are produced and no OneTrack, tiny-data, or OTLP request occurs.
- [ ] Confirm `XmsfProvider`, `LogProvider`, `InstalledAppProvider`, `PoiBroadcastReceiver`,
  `PushUploadLogReceiver`, and `DumpLogProvider` remain absent from the installed component list.
  Confirm the separate `ModuleLogProvider` remains caller-guarded local hook-log ingress rather than
  a stock log-fetch/upload replacement.

## 10. Missing Platform Evidence

- [ ] Re-capture `SecurityCoreAdd.apk` into `wqk/push/device_dumps` before changing XSpace
  auto-install or user-999 behavior.
- [ ] Add raw artifact metadata, hashes, decompiler output, README entry, and parity-matrix mapping
  for every newly captured component.
- [ ] Re-run the archive index and update the expected artifact counts after intentional additions.
- [ ] Keep XMSFKeeper and other privileged platform helpers classified as external dependencies;
  do not merge their behavior into XMSF merely to make component lists look similar.

## HP Device Verification Record

This is a partial device pass performed on 2026-08-11 against the HP device (`pudding`, Android
16, arm64-v8a, HyperOS `OS3.0.315.0.WPCCNXM`). The tested worktree was at `d8e4cf2d0`, but it
also contained pre-existing uncommitted changes in the XMSF ability assembler and SystemUI island
files. The APK result must therefore be treated as a worktree build, not a clean-commit result.

Tested artifacts:

- XMSF runtime: `0.7.0-20260811_145020`, versionCode `1003003000`, installed at `14:51:55`,
  SHA-256 `5e38316363f87b8bb7651cd2301f99a16eecdb7f8d22ee53bac163a01cf26120`.
- Manager: `0.7.0-20260811_145256`, versionCode `1617`, installed at `14:53:16`, SHA-256
  `e2e0c28f2bcd45df543971f9d631796695e65252c0e5e1a6d9dc782f3a367e47`.

Evidence-backed results:

- [x] `adb devices -l` reports the HP device online; `pm path` and `dumpsys package` report the
  tested XMSF package and metadata.
- [x] Background XMSF startup reaches `MiPushHostApp`, the main process and `:services` process;
  `ServiceBoxService`, `MainProcBridgeService`, `XMPushServiceCore`, and the notification listener
  are present. No XMSF `FATAL EXCEPTION` was observed in the collected logs.
- [x] Manager UI is non-empty and shows Connected, application statistics, version information,
  bottom navigation, and a non-empty application list (`488` total, `82` with MiPush surface,
  `72` registered).
- [x] Manager Binder calls for runtime preferences, application list/detail, connection snapshot,
  diagnostics, and notification channels complete successfully on the device.
- [x] `com.tencent.wework` notification reads use the root/dumpsys fallback and return `5` channels
  and `1` group; `com.coolapk.market` returns `16` channels and `1` group for UID `10329`; and
  `com.unionpay` returns `8` channels and `2` groups for UID `10301`.
- [x] The UnionPay detail UI renders the MiPush channel section, including disabled/native state
  and channel IDs. This is UI evidence for the non-empty channel path.
- [x] The Records page renders real event entries after refresh. Its cold `event_list` call
  completed, but the observed Binder call took `8533ms`; this keeps the latency/refresh-loop
  concern open even though the call did not time out.
- [x] Repeated `connection_snapshot` calls remained successful at roughly `2-12ms` while the
  Manager was open.

Still open after this pass:

- [ ] Notification-channel retry and explicit unavailable/permission-denied UI states.
- [ ] A clean-commit rebuild and final static-test pass after the remaining worktree changes.
- [ ] Heartbeat learning, forced ping-timeout fallback, alarm rescheduling, and network-change
  behavior; seeing a `PING_TIMER` alarm alone does not validate these transitions.
- [ ] Cross-UID SDK ingress, registration dedupe, control actions, package clear/removal, and
  malformed/telemetry rejection.
- [ ] Keep-alive Binder/configuration and process-observer fallback behavior.
- [ ] End-to-end delegated notification, HyperIsland/top/sweet/VoIP, click-routing, self-update,
  provider authorization, and XSpace/platform-helper behavior.
- [ ] Investigate why `event_list` still takes about `8.5s` on cold refresh and whether the page
  can avoid the observed long Binder transaction without reintroducing the old refresh loop.

The following are intentionally not claimed as complete from this pass: source tests, static
inspection, installed manifest presence alone, `dumpsys notification` snapshots alone, and the
absence of a crash in a short foreground session.

## Completion Record

When the deferred pass is run, record for each failed item:

- final commit tested;
- artifact/package version tested;
- command or probe used;
- observed result and relevant log timestamp;
- whether the failure is a code defect, platform permission limit, missing external component, or
  deliberate non-goal;
- follow-up commit, if any.
