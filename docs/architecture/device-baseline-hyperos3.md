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

Collected outside the repo as reference samples:
- `framework.jar`
- `services.jar`
- `miui-framework.jar`
- `miui-services.jar`
- `xiaomi-framework.jar`
- `com.xiaomi.xmsf base.apk`

## Observed Boundary Signals

- `services.jar` is dominated by `com.android.server`
  - treat as AOSP/system service boundary
- `miui-services.jar` is dominated by `com.miui.server` and vendor namespaces
  - treat as MIUI/OEM extension boundary
- `xmsf.apk` includes both product code and embedded legacy runtime
  - product-owned examples: `com.xiaomi.xmsf`, `top.trumeet`, `com.magisk317`, `io.github.magisk317`
  - embedded legacy/runtime examples: `com.xiaomi.push`, `com.xiaomi.mipush`,
    `com.xiaomi.channel`, `com.xiaomi.smack`, `com.xiaomi.slim`, `org.apache.thrift`

## Practical Consequence

When deciding whether a repository package should be deleted, isolated, or rewritten:
- compare against framework / services / MIUI jars for **platform responsibility**
- compare against current `xmsf.apk` for **what the project currently ships**
- do not treat the shipped `xmsf.apk` as “official system xmsf”; it is the project build
