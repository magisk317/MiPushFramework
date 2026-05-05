# Push Module Split And Kotlin Port Plan

This document continues the refactor plan in `/home/lzc/.claude/plans/vivid-frolicking-hummingbird.md`.
It is the working rulebook for splitting `push/` while porting the remaining Java sources to Kotlin.

## Current Snapshot

As of 2026-05-05, after the runtime, timer, service receiver, client-report, remaining `com.xiaomi.push.*` splits, and the final `PushMessageProcessor` Kotlin port, `push/src/main/java` contains:

- 0 Java files
- 269 Kotlin files

The remaining legacy app-facing compatibility surface now lives primarily under:

- `legacy-runtime/src/main/java/com/xiaomi/mipush/sdk`

The HyperOS 3 / Android 16 reference dump is:

```text
/home/lzc/device_dumps/xiaomi_pudding_2026-04-13_stock_baseline
```

Use these two source trees differently:

- Stock 7.x baseline:
  `/home/lzc/device_dumps/xiaomi_pudding_2026-04-13_stock_baseline/jadx/com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources`
  - package `com.xiaomi.xmsf`
  - version `7.4.67-C`
  - versionCode `70004067`
  - APK SHA-256 `444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b`
  - many runtime packages are obfuscated, so matching requires behavior and call-site review.
- Current project override:
  `/home/lzc/device_dumps/xiaomi_pudding_2026-04-13_stock_baseline/jadx/com.xiaomi.xmsf/current/base/sources`
  - package `com.xiaomi.xmsf`
  - version `0.3.17-20260410000745`
  - versionCode `1003003000`
  - APK SHA-256 `f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590`
  - useful for identifying current shipped behavior, but not a stock 7.x source.

There are no remaining Java sources under `push/src/main/java`.
Source-trace cleanup now focuses on retained `legacy-runtime/com/xiaomi/*` compatibility classes, where old
3.x decompiler banners still need to be normalized to the 7.4.67-C stock and/or 2026-04-13 current references.
Stock 7.x files often contain JADX artifacts such as obfuscated names, synthetic switch maps, or invalid `??`
temporaries; those are reference signals, not source-ready code.

## Goals

1. Convert remaining Java in `push/` to Kotlin in small package-sized batches.
2. Preserve useful source comments while porting:
   - Javadoc and behavior notes stay with the relevant declaration.
   - Inline comments stay near the same branch or side effect.
   - Decompiler source comments such as `JADX INFO` are kept when the file still tracks stock code.
   - Java `@Override // ...` comments may become Kotlin comments when they clarify the original owner.
3. Use 7.x dump references to update behavior when the change is clear and compatibility-preserving.
4. Keep `com.xiaomi.*` package names for compatibility-sensitive surfaces.
5. Split modules only after ownership is clear; do not move code just because it compiles.

## Ownership Rules

Classify each package before translating it:

- Product-owned code remains in `push/` unless it is reusable across modules.
- Long-connection runtime code moves toward `legacy-runtime` when it is still required but not product-owned.
- Protocol and generated-like wire types stay in `protocol-frozen`.
- Platform/system references from device dumps never enter the Gradle source graph directly.

Suggested first migration lanes:

- `com.xiaomi.slim.*` and `com.xiaomi.smack.*`: runtime transport, good candidates for `legacy-runtime` after Kotlin parity.
- `com.xiaomi.stats.*` and `com.xiaomi.tinyData.*`: telemetry/runtime support, review delete-first candidates before porting.
- `com.xiaomi.mipush.sdk.*`: the `push/` duplicates are now removed or ported; retained app-facing compatibility code stays in `legacy-runtime` until the public API and manifest/broadcast behavior are stable.
- `com.xiaomi.push.service.*`: split by role; runtime internals trend toward `legacy-runtime`, stock compatibility glue stays in `push/`.

## Porting Workflow

For each batch:

1. List Java files and call sites with `rg`.
2. Compare the repo file with the same path under the 7.x dump.
3. Prefer stock 7.x for behavior, then use the current override only to explain current project-specific drift.
4. Mark differences as one of:
   - behavior update to port
   - decompiler rename/artifact to ignore
   - project-specific runtime bridge to keep
   - deletion candidate
5. Convert Java to Kotlin with the same package and public JVM surface.
6. Preserve comments during the conversion.
7. Delete the same-class Java file in the same change.
8. Run at least `./gradlew :push:compileDebugKotlin`.
9. For moved code, run the destination module compile task too.

## Kotlin Porting Notes

- Avoid creative rewrites during the first port; keep control flow close to the reviewed Java.
- Prefer Kotlin nullability that matches actual Java behavior, especially for decompiled platform/runtime code.
- Keep constants and static factory calls stable for Java callers.
- Use explicit `Blob.CMD_*`-style qualifiers when it prevents ambiguity in mixed Java/Kotlin packages.
- Be careful with Java package-private methods. Kotlin may widen JVM visibility, so compile the batch before moving on.

## Completed Slices

`com.xiaomi.slim.*` is now owned by `legacy-runtime`:

- removed the duplicate `push/src/main/java/com/xiaomi/slim` Java/Kotlin sources
- kept the Kotlin implementation in `legacy-runtime/src/main/java/com/xiaomi/slim`
- moved source notes to the retained `legacy-runtime` files
- `Ping` was checked against stock `com.xiaomi.xmsf` `7.4.67-C` / versionCode `70004067`
- stock equivalents are `pa/a.java` through `pa/i.java`; retained Kotlin keeps the deobfuscated `com.xiaomi.slim.*` APIs

`com.xiaomi.tinyData.*` is now owned by `legacy-runtime`:

- removed the duplicate `push/src/main/java/com/xiaomi/tinyData` Java sources
- kept the Kotlin implementation in `legacy-runtime/src/main/java/com/xiaomi/tinyData`
- moved source notes to the retained `legacy-runtime` files
- stock equivalents are `wa/a.java` through `wa/e.java`; retained Kotlin keeps the deobfuscated `com.xiaomi.tinyData.*` APIs
- `HttpUploader` has no stock `7.4.67-C` counterpart in the split source; it remains a local compatibility shim

`com.xiaomi.stats.*` is now owned by `legacy-runtime`:

- removed the duplicate `push/src/main/java/com/xiaomi/stats` Java sources
- kept the Kotlin implementation in `legacy-runtime/src/main/java/com/xiaomi/stats`
- moved source notes to the retained `legacy-runtime` files
- stock equivalents are `oa/a.java` through `oa/e.java`; retained Kotlin keeps the deobfuscated `com.xiaomi.stats.*` APIs
- aligned `legacy-runtime` smack error constants with the stock values used by stats error classification

`com.xiaomi.smack.*` transport core is now owned by `legacy-runtime`:

- removed the duplicate `push/src/main/java/com/xiaomi/smack` Java sources
- kept the Kotlin implementation in `legacy-runtime/src/main/java/com/xiaomi/smack`
- moved source notes to the retained `legacy-runtime` files
- stock equivalents are `qa/b.java`, `qa/c.java`, `qa/d.java`, `qa/g.java`, `qa/h.java`, and `qa/j.java`
- `HttpRequestProxy` has no stock `7.4.67-C` `qa.*` counterpart; the current override keeps an empty same-path interface, while this runtime retains the compatibility methods

`com.xiaomi.push.service.timers.*` and `com.xiaomi.push.service.receivers.*` are now owned by `legacy-runtime`:

- removed duplicate Java sources from `push/src/main/java/com/xiaomi/push/service/timers`
- removed duplicate Java sources from `push/src/main/java/com/xiaomi/push/service/receivers`
- kept the Kotlin implementations in `legacy-runtime`
- moved source notes to the retained Kotlin files
- timer stock equivalents are in `ia/*`; receiver stock references stay under `com.xiaomi.push.service.receivers` or the stock package action receiver
- aligned `PkgUninstallReceiver` with stock/current `PACKAGE_REMOVED` -> `ACTION_UNINSTALL` handling

`com.xiaomi.push.service.clientReport.*` is now owned by `legacy-runtime`:

- removed duplicate Java sources from `push/src/main/java/com/xiaomi/push/service/clientReport`
- kept the Kotlin implementation in `legacy-runtime/src/main/java/com/xiaomi/push/service/clientReport`
- moved source notes to the retained Kotlin files
- stock equivalents are obfuscated as `ea/a.java`, `ea/b.java`, and `ea/c.java`; current override keeps same-path processor/helper shells
- `ReportConstants` has no current same-path source in the 2026-04-13 override; stock 7.4.67 inlines many report ids across the `ea.*` report helper and downstream report builders

The rest of `com.xiaomi.push.*` is now owned by `legacy-runtime`:

- removed duplicate Java sources from `push/src/main/java/com/xiaomi/push/log`, `mpcd`, `providers`, `service/awake`, `service/notification`, `service/profile`, and `service/xmpush`
- kept the Kotlin implementations in `legacy-runtime`
- moved source notes to the retained Kotlin files
- converted `com.xiaomi.push.clientreport.PerfMessageHelper` from Java to Kotlin and updated it to the current override source comment
- stock references include `t9/c.java`, `aa/a.java`, `ja/a.java`, `ha/*`, `u9/*`, `v9/*`, and `vb/*`; current-only awake sources are marked with their exact override version and path

The duplicate `com.xiaomi.mipush.sdk.*` surface in `push/` is now removed:

- removed the duplicate Java sources from `push/src/main/java/com/xiaomi/mipush/sdk`
- kept shared SDK compatibility types in `legacy-runtime`
- converted the final in-place processor `push/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessor.java` to Kotlin as `PushMessageProcessor.kt`
- updated the new Kotlin file to reference stock `d0.java` and current same-path `PushMessageProcessor.java`
- `push/src/main/java` is now fully Kotlin
