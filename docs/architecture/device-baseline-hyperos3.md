# Device Baseline: HyperOS 3 / Android 16

## Sample

Reference device captured on 2026-04-11:
- device codename: `pudding`
- model: `25113PN0EC`
- Android release: `16`
- SDK: `36`
- build incremental: `OS3.0.304.0.WPCCNXM`

The project currently overrides the system package name `com.xiaomi.xmsf` with its own APK.
That means:
- pulled `com.xiaomi.xmsf` APK describes the **project’s shipped runtime behavior**
- pulled framework and MIUI jars describe the **actual platform boundary**

## Collected Artifacts

Curated outside the Gradle repo under `../device_dumps/devices/xiaomi_pudding/`:
- `framework.jar`
- `services.jar`
- `miui-framework.jar`
- `miui-services.jar`
- `xiaomi-framework.jar`
- `com.xiaomi.xmsf base.apk`

The archive now provides an SHA/package/version/output map at
`../device_dumps/metadata/artifact-index.tsv`; all six April inputs have JADX output, including the
supplemental `xiaomi-framework.jar` pass. The April override APK is byte-identical to the
2026-04-13 `current/base.apk` and must not be treated as stock.

The actual stock baseline is the 11-split XMSF `7.4.67-C` set captured on 2026-04-13. Use its
per-split output for provenance and `combined-jadx-1.5.6` for cross-split searches. Generated Java
contains known JADX failures, so Binder/switch conclusions require dex-code confirmation.

## Observed Boundary Signals

- `services.jar` is dominated by `com.android.server`
  - treat as AOSP/system service boundary
- `miui-services.jar` is dominated by `com.miui.server` and vendor namespaces
  - treat as MIUI/OEM extension boundary
- `xmsf.apk` includes both product code and embedded vendored runtime
  - product-owned examples: `com.xiaomi.xmsf`, `io.github.magisk317`, `io.github.magisk317.mipush`
  - embedded vendor/runtime examples: `com.xiaomi.push`, `com.xiaomi.mipush`,
    `com.xiaomi.channel`, `com.xiaomi.smack`, `com.xiaomi.slim`, `org.apache.thrift`

## Practical Consequence

When deciding whether a repository package should be isolated or rewritten:
- compare against framework / services / MIUI jars for **platform responsibility**
- compare against current `xmsf.apk` for **what the project currently ships**
- do not treat the shipped `xmsf.apk` as “official system xmsf”; it is the project build
- compare stock-facing component contracts against the 2026-04-13 stock split set, not only against
  same-name classes in the April project override
