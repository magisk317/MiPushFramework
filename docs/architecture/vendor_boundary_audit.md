# Vendor Boundary Audit Report

> Generated: 2026-08-22
> Updated: 2026-08-31
> Updated: 2026-09-12 (D1 scan coverage extension)
> Related doc: `docs/architecture/architecture-review-plan.md`
> Exit condition: every retained vendor policy has a stock source, an impact description, and a verification status

## Audit Scope

All `io.github.magisk317.` references under `vendor/src/main/java/` (currently 82 entries: 17 imports and 65 inline fully-qualified references / typealias tables / documentation comment lines), maintained by `scripts/config/vendor_boundary_baseline.txt` and required to stay at zero net additions. Since 2026-09-12 the scanner matches FQCNs anywhere on a line (no longer only `^import`); inline plan delegations and typealias bridges are therefore visible to the "no new additions" check. See the D1 section below.

## Category Summary

### Category 1: MagiskOtel Structured Telemetry (10 entries)

| File | Behavior | Impact |
| :--- | :--- | :--- |
| `MiPushClient.kt` | push.register event reporting | Replaces stock MiLog with structured JSONL output |
| `MiTinyDataClient.kt` | tiny-data event reporting | Same as above |
| `PushMessageHandler.kt` | message handling events | Same as above |
| `PushServiceClient.kt` | service invocation events | Same as above |
| `ClientEventDispatcher.kt` | client event dispatch | Same as above |
| `MIPushAckDispatcher.kt` | ACK response events | Same as above |
| `MIPushEventProcessor.kt` | event processing pipeline | Same as above |
| `NotificationIdentityBridge.kt` | notification identity bridging | Same as above |
| `PacketSync.kt` | packet synchronization events | Same as above |
| `XMPushServicePacketDelegate.kt` | packet delegation events | Same as above |

**Stock source**: the original code used `com.xiaomi.channel.commonutils.logger.MyLog` (vendor's built-in logger).
**Impact**: replaced with `MagiskOtel.event()` structured events supporting JSONL, Sink, and Xposed transport; no business-logic change.
**Verification status**: runtime JSONL output verification in `:xmsf:shell:testNormalDebugUnitTest` and `PushPacketRuntimeTest` reason-code verification.

### Category 2: LoggerExtensions Structured Logging (5 entries)

| File | Behavior |
| :--- | :--- |
| `NotificationIdentityBridge.kt` | logD/logE/logI/logV/logW used for notification channel management logs |

**Stock source**: the original code used `MyLog`.
**Impact**: replaced with the Kermit structured logging facade, supporting redaction and level mapping.
**Verification status**: `LogSanitizerTest` and `XposedLogClientSanitizationTest` verify redacted output.

### Category 3: DefaultLogSanitizer (1 entry)

| File | Behavior |
| :--- | :--- |
| `AssemblePushHelper.kt` | redaction when encoding/decoding push tokens |

**Stock source**: the original code had no redaction (logged the token directly).
**Impact**: added token/secret redaction to prevent sensitive data leaking into logcat.
**Verification status**: `LogSanitizerTest`, `XposedLogClientSanitizationTest`.

### Category 4: PushServiceBroadcastActions Constant Contract (1 entry)

| File | Behavior |
| :--- | :--- |
| `XMPushServiceMessenger.kt` | registers GET_CONNECTION_STATUS / START_FOREGROUND broadcast actions |

**Stock source**: the original code used hardcoded strings in `PushConstants`.
**Impact**: shared contract constants, no behavior change.
**Verification status**: `XMPushServiceAppIntentDelegateContractTest`.

## Conclusion

All baseline entries have a clear stock source, impact description, and verification status. No new vendor product-layer import debt; the baseline is not an exemption list that permits new product behavior.

## D1 Scan Coverage Extension (2026-09-12)

> Related audit finding: D1 — the vendor boundary scanner previously matched only `^import io\.github\.magisk317\.`, so inline fully-qualified references and typealias bridges were invisible to the "no new additions" check, causing the boundary scan to under-report.

### Scanner Behavior Change

The vendor check in `scripts/checks/verify_module_boundaries.sh` changed its match pattern from `^import io\.github\.magisk317\.` to matching `io\.github\.magisk317\.` anywhere on a line (scan root `vendor/src/main`, leading whitespace stripped before comparison). Comment and string lines remain in scope, consistent with how documentation lines were handled in the existing baseline. This extension booked 65 existing sanctioned references at once (baseline categories 5–7); further additions of the same kind are now rejected.

### Newly Exposed Categories (baseline categories 5–7)

| Category | File | Count | Idiom |
| :--- | :--- | :--- | :--- |
| 5 — inline FQCN plan delegations | `smack/Connection.kt`, `slim/BlobReader.kt`, `slim/BlobWriter.kt`, `NetworkCheckup.kt`, `XMPushServiceIntentDelegate.kt` | 22 | inline calls to runtime-core PlanFactory/Event/Command (plan-delegation/adapter idiom) |
| 6 — PushVersionInfo inline FQCN delegations | `push/service/PushVersionInfo.kt` | 10 | thin delegation of constants and methods to runtime-core `PushVersionInfo` |
| 7 — typealias bridge tables | `push/service/PushRuntimeModels.kt` | 33 | re-export runtime-core models/factories into stock package names via typealias (including one explanatory comment line) |

### Reflection Seam (invisible to the scanner, documented exemption)

`createProductAlarm()` in `push/service/timers/Alarm.kt` loads the product-side timer via `Class.forName("com.xiaomi.push.service.timers.AlarmManagerTimer")` (the class file is at `xmsf/shell/src/main/java/com/xiaomi/push/service/timers/AlarmManagerTimer.kt`, sharing the `com.xiaomi` package namespace with vendor) and falls back to vendor's own `StockAlarmManagerTimer` if resolution fails. The target is a `com.xiaomi.*` class-name string, outside the coverage of the `io.github.magisk317.` scan pattern; this scanner intentionally does not cover it, and it is recorded here as a sanctioned compatibility seam (aligned with the timer identity semantics of stock 7.4.67-C selecting the AlarmManager backend).

## Verification Commands

```bash
./gradlew check --warning-mode=all --console=plain
./gradlew verifyModuleBoundaries :xmsf:shell:detekt :settings:detekt
./gradlew :vendor:testDebugUnitTest :xmsf:shell:testNormalDebugUnitTest
```

---

## Appendix: `:xmsf:runtime:store` Production Database

> Added: 2026-08-22
> Related doc: `docs/architecture/boundary-model.md` § runtime/storage boundaries

### Module Overview

`xmsf/runtime/store/` is the Kotlin Multiplatform module for production runtime storage. It maintains the v9 schema and uses KMP `room.generateKotlin = true` to generate type-safe Kotlin queries; xmsf opens the existing `db` file through the Bundled SQLite driver.

### Relationship to the Vendor Boundary

`:xmsf:runtime:store` **introduces no new vendor boundary crossing**. Its data comes from xmsf's runtime storage business path (KMP DAOs injected via Koin). All stock SDK ingress still goes through the vendor → xmsf path. The KMP module only implements storage and does not change the existing vendor boundary contract.

### File List

| File | Purpose |
| :--- | :--- |
| `commonMain/RuntimeStoreDatabase.kt` | KMP Room database definition (v9 schema) |
| `commonMain/RuntimeStoreDaos.kt` | type-safe DAO interfaces |
| `commonMain/RuntimeStoreRows.kt` | query result data classes |
| `androidMain/RuntimeStoreDatabase.android.kt` | Android Bundled SQLite driver and production `db` builder |
| `commonMain/RuntimeStoreMigrations.kt` | v1→v9 production database migrations |

### Verification Status

- `:xmsf:runtime:store:compileAndroidMain` — passed
- `:xmsf:runtime:compileDebugKotlin` — passed
- `:xmsf:runtime:testDebugUnitTest` — passed
- `:xmsf:shell:compileNormalDebugKotlin` — passed
- `RuntimeStoreMigrationContractTest` — passed

### Exit Conditions

1. The KMP module compiles via `:xmsf:runtime:store:compileAndroidMain`.
2. The production builder uses `db` and the v9 migration registry.
3. xmsf business paths consume only KMP DAOs and retain no Android Room database shell.
