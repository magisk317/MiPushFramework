# MiPushFramework Boundary Model

## Summary

MiPushFramework is not just an app project. It currently mixes four distinct layers inside the
`push` module:

1. **platform-reference**
   - Device system artifacts such as `framework.jar`, `services.jar`, `miui-framework.jar`,
     `miui-services.jar`, and `xiaomi-framework.jar`.
   - These are sources of truth for capability and boundary discovery only.
   - They must not be treated as app-owned code.

2. **product-owned**
   - The code that defines the shipped `com.xiaomi.xmsf` behavior for this project.
   - Primary prefixes:
     - `com.xiaomi.xmsf.*`
     - `io.github.magisk317.*`
     - `io.github.magisk317.mipush.*`
     - app-facing service/sdk surfaces such as `com.xiaomi.push.service.*`,
       `com.xiaomi.mipush.sdk.*`, and `com.xiaomi.push.sdk.*`
   - Legacy `top.trumeet.*` entrypoints are compat shims only and should not be used as the
     primary implementation namespace for new work.

3. **legacy-runtime**
   - Vendored Xiaomi push/runtime/network/telemetry stacks that are packaged into the app but are
     not the desired long-term product architecture.
   - Typical prefixes:
     - `com.xiaomi.channel.*`
     - `com.xiaomi.network.*`
     - `com.xiaomi.smack.*`
     - `com.xiaomi.slim.*`
     - `com.xiaomi.clientreport.*`
     - `com.xiaomi.stats.*`
     - `com.xiaomi.tinyData.*`
     - `com.xiaomi.common.logger.*`
     - `com.xiaomi.push.mpcd.*`

4. **frozen-protocol**
   - Protocol and serialization layers that should be treated like generated or frozen source.
   - Typical prefixes:
     - `org.apache.thrift.*`
     - `com.google.protobuf.micro.*`
     - `com.xiaomi.xmpush.thrift.*`
     - `com.xiaomi.push.protobuf.*`
     - `com.xiaomi.push.thrift.*`

## Compatibility Constraints

- The shipped package name remains `com.xiaomi.xmsf`.
- Compatibility is intentionally reduced to the minimum set that still preserves:
  - registration
  - long-lived connection
  - downstream message dispatch
  - ACK / error feedback
  - target-package notification publish / click / grouping behavior
- Internal structure may change aggressively as long as these external contracts remain stable:
  - package / component names
  - manifest entrypoints
  - broadcast actions
  - intent extras
  - binder and wire behavior

## Layering Rules

- `product-owned` code must not directly import deep `legacy-runtime` or `frozen-protocol`
  packages except through explicit facades.
- `legacy-runtime` code may depend on `frozen-protocol`, but feature/UI code must not.
- `platform-reference` artifacts never enter the Gradle build graph.
- `frozen-protocol` changes must be compatibility-preserving and non-creative.

## Delete-First Candidates

Delete before translating where possible:
- demo / sample / `usagedemo` code
- duplicated `BuildConfig` shells
- thin marker enums / interfaces / trivial holders
- old ads / debug / support code that is not used by the current minimum compatibility chain

## Structural End State

- `push` becomes the product/app/system-entry module only.
- `legacy-runtime` contains vendored runtime still needed after pruning.
- `protocol-frozen` contains protocol/serialization source that must remain wire-stable.
- Device system jars remain external reference inputs, not source modules.

## Working Migration Plan

The current package-by-package Java to Kotlin porting and `push/` split rules are tracked in
`docs/architecture/push-module-split.md`.
