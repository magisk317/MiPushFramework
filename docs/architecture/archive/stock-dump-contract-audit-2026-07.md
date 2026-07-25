# Stock-Dump Contract Audit — July 2026

## Status And Scope

This is the durable audit record for the non-Android-17 P0/P1 stock-XMSF compatibility work.
It replaces the earlier component-presence comparison: a matching Provider, Service, or manifest
name is not treated as compatibility proof.

The scope is limited to the XMSF push runtime, its exported stock-facing surfaces, notification /
XSpace boundaries, and their direct consumers. Android 17/API 37 networking, memory-limit, and
device-matrix work is explicitly outside this audit.

## Evidence Discipline

The evidence order for this document is intentional:

1. Raw artifacts and their provenance in `../device_dumps/metadata/artifact-index.tsv` establish
   what was captured.
2. The stock `7.4.67-C` XMSF split set establishes the stock-facing contract. The April project
   override is only evidence of an earlier project build and must never be used as stock behavior.
3. Decompiled sources are candidate evidence. Binder transaction dispatch, malformed JADX output,
   and switch-heavy paths were cross-checked against dex code before changing product code.
4. Current source, focused tests, Gradle compilation, and packaging prove the implementation that
   is actually in this repository.
5. Previous agent/session reports are discovery aids only. They cannot replace the preceding live
   evidence when deciding that a requirement is complete.

The curated archive currently covers 30 raw APK/JAR/DEX inputs with 29 unique SHA-256 values. Its
index and decompilation scripts are reproducible maintenance evidence, not Gradle inputs.

## Baseline And Method

- Primary stock baseline: the 11-split `7.4.67-C` XMSF capture under
  `../device_dumps/devices/xiaomi_pudding/2026-04-13-stock-baseline/`.
- Preferred source view: each raw split for provenance, plus `combined-jadx-1.5.6` for
  cross-split call-chain searches.
- Rejected baseline: the April `0.3.17` project override. It shares the package name but reflects
  a project runtime, not an official stock implementation.
- Comparison dimensions: method names, Bundle keys and value types, return-code type, caller
  identity, persistence behavior, Binder transaction number/flags, and the real runtime consumer.

## Contract Matrix

| Surface | Live contract now | Consumer / proof boundary |
|---|---|---|
| `PushControlProvider` | Uses stock online-config keys `61`, `57`, `58`, and `56`; no invented numeric aliases remain. | `StockSurfaceSupport.pushControlConfigKeys()` and `StockProviderContractTest`. |
| `PushProfileIdProvider` | Supports the four stock method names (`addProfileId`, `deleteProfileId`, `queryProfileIds`, `deleteAllProfileId`), string result codes, caller-owned registered package state, ten IDs, 64-character IDs, and the stock `;#;` separator rule. | `StockProfileIdStore`; registration/absence/data-clear paths clear state only where stock does. |
| Profile downstream gate | Display-message profile mismatches are rejected after decryption in `MIPushEventProcessor`, enqueue the stock `profileId_missing` acknowledgement, and never reach normal processing. A later `MiPushRuntimeBridge` fence prevents EventDb and notification-allowance leakage without duplicating the ACK. | `MIPushEventProcessorTest` executes the queued job and decodes the emitted Blob to prove the exact ACK action, body, and `profileId_missing` metadata. `StockProviderContractTest` covers the later fence. Pass-through messages do not use the display-profile gate. |
| `PushSupportProvider` Box projection | Box messages require consent (`privacy_status`), `allow_box=true`, non-empty channel type/id, an enabled app/channel, and a constructible Activity route. `intentData` is an Activity `Intent` parcel, not a thrift payload. `deleteMsgs` tombstones the local Event row projection rather than deleting event history, so a later event reusing the same remote message ID remains visible. | `StockPushSupport` and `StockProviderContractTest`; `StockProviderIngressTest` covers production payload ingestion through EventDb and the real Provider transport projection. |
| `ChannelProvider` | Creation/query use the stock broker caller, API/ROM gates, the mapped `mipush|package|source` channel namespace, stock permission bitmask handling, and explicit app/channel state codes. Existing provider-created/legacy channel IDs win before any local fallback channel is used. | `StockChannelSupport`, `NotificationController`, and provider contract tests. |
| Exported permissions | The eight stock-facing permissions restored from the dump use `signatureOrSystem`; sensitive methods retain explicit caller-package/UID checks. Permission reachability is not accepted as a substitute for a method-level allowlist. | `xmsf/src/main/AndroidManifest.xml`, provider code, and `ManifestContractTest`. |
| Binder ABI | `IMainProcBridge` uses transaction `1=int`, `2=string`, `3=boolean`; hand-written stubs attach/query their descriptor and expose proxies. KeepAlive and Stat calls preserve their one-way flags; HTTP/Stat international methods use transaction `2`. | `StockBinderAbiTest`. |
| KeepAlive | Strategy JSON is persisted but cannot activate binding before ServiceBox resolves `KASwitch=142`; a missing main-process bridge or failed Binder read cannot synthesize an enabled state. That switch drives the reduced polling binder/unbinder; `OnetrackSwitch=140` is observed/persisted separately but does not enable stock OneTrack while telemetry remains disabled. A device blacklist removes an already-bound target immediately. | `KeepAliveRuntimeAdapter` and its focused tests. The stock process-observer implementation is intentionally not imported. |
| MiCloud bind | `BindMiCloudPushService` consumes `key_to_bind_intent`, resolves the supported target component, binds, invokes `startWork(...)`, and unbinds with bounded waiting. | `BindMiCloudPushService` and `StockBinderAbiTest`. |
| MiStat HTTP service | XMSF returns local stock-shaped canned/config responses under a concurrency bound and does not POST telemetry to the network. This preserves the product telemetry-disable policy. | `HttpService` and `HttpServiceTest`. |
| Internal push control | `PushInnerReceiver` parses the stock `messageId`/`content` envelope for safe kit delivery and explicitly rejects privileged uninstall controls for which this runtime has no signed module manager. | `PushInnerReceiverTest`. |
| Notification metadata | Stock HyperOS fields such as assistant/group controls and collection statistics are translated in a product-owned bridge, rather than copied into generic app metadata. | `StockNotificationMetadataBridge` and `StockProviderContractTest`. |
| Notification / XSpace hook boundary | Delegated posts keep `pkg=target app` and `opPkg=com.xiaomi.xmsf`; NMS resolves the target user's UID. Configured focus, generated island proxy, and shade visibility are independent controls. Dispatcher/removal ownership and XSpace identity fallbacks are covered by the Xposed boundary record. | `docs/architecture/xposed-notification-boundary.md` and its xposed tests. |

## Source Ownership

Stock compatibility glue remains in product-owned `xmsf` code:

- `com.xiaomi.xmsf.stock.{StockPushSupport,StockChannelSupport,StockProfileIdStore,
  StockNotificationMetadataBridge}`
- `io.github.magisk317.mipush.service.runtime.KeepAliveRuntimeAdapter`
- the named Provider/Service/Binder facades under `com.xiaomi.xmsf`

The retained connection stack stays in `vendor`; `pinned` stays a frozen wire surface. Device
dumps remain reference artifacts outside the Gradle source graph. New behavior must not be added to
`vendor` merely because a stock class had a similar package name.

## Current Verification

The current working-tree verification for this audit is:

```bash
./gradlew :xmsf:testNormalDebugUnitTest :xmsf:compileNormalDebugKotlin \
  :xposed:testDebugUnitTest :xposed:compileDebugKotlin \
  :xmsf:detekt :xmsf:detektNormalDebugUnitTest :xposed:detekt verifyModuleBoundaries \
  :app:assembleNormalDebug -PallowIncompatibleDebugSigning=true
```

The command passes and builds the device-installable `com.xiaomi.xmsf` runtime from `:app`.
`xmsf` itself is a library module; its AAR output is not device-installation evidence. Focused
coverage lives in `StockProviderContractTest`, `StockBinderAbiTest`, `HttpServiceTest`,
`KeepAliveRuntimeAdapterTest`, `BindMiCloudPushServiceTest`, `ManifestContractTest`,
`StockProviderIngressTest`, and the corresponding xposed tests. The ingress test calls the real
`ContentProvider.Transport`: it proves that Profile state follows the Binder UID package rather
than caller-controlled extras, preserves PushSupport's nested Bundle shape, projects a production
payload persisted through EventDb, and makes Channel access fail closed when the framework cannot
verify the caller. The Manifest test pins exported ingress and permission declarations. A positive
Channel call from the stock broker through cross-UID `ContentResolver.call()` remains an
installed-device/instrumented boundary.

Archive maintenance is separately reproducible with:

```bash
cd ../device_dumps
python3 scripts/build_artifact_index.py --write
python3 scripts/build_artifact_index.py --check
./scripts/decompile_gaps.sh
```

## Remaining Evidence Limits

- `SecurityCoreAdd.apk` is still absent from the curated raw archive. SecurityCore/XSpace claims
  that depend on it remain historical live-device evidence until raw artifact, SHA metadata, and
  output are captured.
- No current device run proves the final notification `pkg`, `opPkg`, UID, user, channel, focus,
  island, or XSpace rendering state. Validate those with installed `:app` output plus
  `dumpsys notification --noredact` and hook logs.
- The reduced KeepAlive adapter has deterministic unit coverage, but its polling cadence and
  target-app lifecycle behavior still need device evidence.
- `signatureOrSystem` declarations restore stock reachability only on suitable platform builds;
  an APK cannot recreate all system privileges, allowlists, or multi-user policy.

Do not turn any of these limits into a broader compatibility claim without fresh artifact and
runtime evidence.
