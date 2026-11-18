# MIUI Capability Compatibility Layering Analysis: Properties, Classes, and Predicates (2026-09-13)

Context: Douyin 40.4.0 fails to initialize the MiPush provider on a near-stock Xiaomi ROM (external verification report, not in this repository). This report explains why neither the Zygisk nor the framework spoofing layer caught it, inventories the drift across the three "known surface" lists, and gives a prevention mechanism.

## 1. Bottom Line: The Three-Layer Capability Model

A third-party SDK decides "this is MIUI/HyperOS" through three independent technical layers, each with a different spoofing mechanism. **No layer can substitute for another**:

| Layer | What the SDK actually reads | Our existing spoofing mechanism | Douyin case |
|---|---|---|---|
| L1 property layer | `SystemProperties.get("ro.miui.*")`, `Build.*` static fields | Zygisk (`pre_app_specialize` modifies Build fields and hooks the SystemProperties native getter); framework `FakeProperty` (inside the app process) | Pass — the default Zygisk profile supplies 90 properties |
| L2 classpath layer | `Class.forName("miui.os.Build")` — whether the class exists, determined by whether the ROM ships `/system_ext/framework/miui-framework.jar` | Framework `Common.fakeClass()` (maps `miui.os.Build` / `miui.external.SdkHelper` to bundled shims) | **Fail**, see §2 |
| L3 SDK predicate layer | The app's own wrapper methods (e.g. `ToolUtils.isMiui()`: parses L2 and caches the result; different apps have their own predicates) | Previously no hook targeted the real predicate | **Fail** — the provider selector is stuck at this layer |

"Isn't Zygisk spoofing all properties? How did it miss a field?" — **It did not miss a field: `miui.os.Build` is not a property at all.** It is a class in the HyperOS framework mount table (even the EEA build of HyperOS ships `miui-framework.jar`, so real HyperOS can always resolve it; a near-stock ROM has no such class at all). Property spoofing cannot create a class out of nothing by mechanism. This is exactly the L1/L2 boundary.

## 2. Why the Framework Layer Also Missed It: Three Independent Mistakes Compounded

1. **Incorrect guard assumption** (`FakeDevice.kt`): `BRAND/MANUFACTURER == Xiaomi → skip the whole pipeline`. This treats "brand is Xiaomi" as "MIUI framework classes exist" — a near-stock Xiaomi ROM breaks it. More ironically, a non-Xiaomi device whose BRAND is changed to Xiaomi by Zygisk also triggers the skip, so even `fakeClass` never gets installed. Both ends are blocked.
2. **Incorrect overload assumption** (`Common.fakeClass()`): only the three-argument `Class.forName(String, boolean, ClassLoader)` is hooked. Douyin's `X.0TbU.G0(String)` uses the single-argument `Class.forName`, which in ART goes directly to the native `forName0` and **does not pass through the public three-argument method**, so the method-level hook cannot intercept it.
3. **Outdated hook target** (`DouYin.kt`): it hooks `socialbase.appdownloader.util.MIUIUtils.isMIUI*` — the downloader component's MIUI check (knowledge from the old MiPush integration era). It differs from the message SDK's provider selector (`com.ss.android.message.util.ToolUtils.isMiui`) in class, caller, and responsibility.

Fix (`67e691ab6`): added `DouyinMiuiGateHook` with exact signature matching (static / zero-args / primitive boolean `isMiui`), `doAfter` modifies only the result (the original method still runs and caching behavior is untouched), and fails closed with diagnostics instead of blind hooking when the class or method is missing or the signature is ambiguous. The install point is placed **before** the guard (following the `DouyinMiuiGateHook` precedent), so the `DOUYIN` pipeline takes effect directly from the profile.

## 3. "Which Existing, Known Surfaces Are Still Uncovered?" — Three-List Diff Record

Data sources: `MiPushZygisk config.rs DEFAULT_SPOOF_PROPS` (all 131 lines), framework `FakeProperty.kt` (39 keys), and the rubens HyperOS dump `miui-framework/sources/miui/os/Build.java` (88 public statics on the real class plus the properties actually read by the static block).

### 3.1 Present in Zygisk, absent from FakeProperty (27 items, selected categories)

- Region/locale surface: `ro.miui.build.region`, `ro.vendor.miui.region`, `ro.product.locale[.language]`, `persist.sys.oppo.region`, `ro.hw.country`, `ro.csc.countryiso_code`, `gsm.vivo.countrycode`, etc. (**missing region keys affect SDK region selection** — note that we just fixed region-related chains for AMap/Alipay);
- Identity details: `ro.product.{system,vendor}.{manufacturer,brand}` (some apps bypass aliases and read partition-level properties), `ro.build.description/product`, `ro.product.property_source_order`;
- HyperOS feature switches: `persist.sys.miconnect.running`, `persist.sys.millet.handshake`, `ro.miui.enable_cloud_verify`, `ro.mi.development`, `ro.rom.zone`, `ro.fota.oem`.

### 3.2 Present in FakeProperty, absent from the Zygisk system-property surface (2 items, with different mechanisms)

- `ro.build.fingerprint`: Zygisk changes only the `Build.FINGERPRINT` **field**, not the `ro.build.fingerprint` **property** — an SDK reading the fingerprint via `SystemProperties.get` still sees the real value on Zygisk-only devices;
- `ro.build.version.sdk`: Zygisk changes the `Build.VERSION.SDK_INT` field; the same-named property is not covered.

### 3.3 Properties read by the real `miui.os.Build` static block but covered by neither list (L2 semantic gap)

`ro.miui.cts` (→ `IS_CTS_BUILD`), `ro.miui.has_cust_partition`, `ro.build.characteristics`, `ro.debuggable` (→ `IS_DEBUGGABLE`), `ro.cust.test`, `ro.miui.userdata_version`, `persist.sys.user_mode`, `persist.sys.miui_optimization`, `ro.soc.name`, `ro.carrier.name`, `ro.config.low_ram.threshold_gb`, and the modem family (`ro.boot.modem` / `persist.*.modem`). If an SDK is not satisfied with mere "class exists" and reads these derived fields, it will still get values inconsistent with real HyperOS.

### 3.4 Shim surface vs real class surface (the largest quantified gap)

`fakeclass/MiuiBuild.kt` provides 5 fields plus `getRegion`/`getCustVariant`; the real class has **88** public statics (`IS_CTA_BUILD`, `IS_FOLD` and device-level constants, carrier customization bits, etc.) and the real class `extends android.os.Build`. Currently this works by luck because the gate only checks class existence (the Douyin case now uses the direct predicate and does not depend on the shim); any app that takes the `fakeClass` three-argument path and reflects on missing fields will hit `NoSuchFieldError`.

There is also a three-source value drift: `ro.miui.cust_variant` = `cn` (Zygisk) vs `cn_chinatelecom` (FakeProperty, consistent with `shim.getCustVariant`) — the same property gets different answers from the two spoofing channels.

## 4. Prevention Mechanism

- **P1 (effective)**: capability compatibility always uses the "in-app precise predicate hook + fail-closed + diagnostics" template (following the `DouyinMiuiGateHook` precedent); property/class shims are auxiliary only. `xposed/AGENTS.md` now carries the rule: *predicate/class-existence checks must not be assumed to be property problems*.
- **P2 (principle)**: do not create a new shared module for property lists (`MiPushConfigurations` is a narrow resetprop template repository and is not responsible for list convergence). Zygisk and FakeProperty maintain their lists locally, but drift must be checked against the §3 lists during device regression, and any value change is treated as a wire-visible change requiring real-device verification.
- **P3 (recommended)**: a shim contract test — extract the real `miui.os.Build` public member table from the dump as a test resource, assert that the shim covers the "common probe set" with an explicit exemption list for missing entries; if `fakeClass` is retained, extend it to cover both the single- and three-argument `forName`.

## 5. DouYin Pipeline Historical Debt Disposition (Executed This Round)

| Segment | Disposition | Rationale |
|---|---|---|
| Downloader `MIUIUtils.isMIUI/isMIUI6Later` forced hook | **Removed** | The report disproves its comment assumption: the provider gate is `ToolUtils.isMiui`, and the downloader predicate never participates in push initialization. The hook only blindly changed downloader behavior (the original comment "critical for Douyin to initialize MiPush SDK" is a fossil of the old misjudgment). |
| Flyme four-line property cleanup block | **Removed** | Fully redundant with `Common.fakeAllBuildInProperties`: the three flyme keys are cleared unconditionally in `VENDOR_CLEAR_PROPS`, and `display.id`/`user` are already handled on every non-Xiaomi device path that can trigger the branch. |
| cloudpush `update_sender` rewrite segment | **Retained, pending device verification** | A second mechanism complementary to the provider gate (keeps `1 = Xiaomi channel` in the server-side `allow_push_list`); not covered by the report. Evidence does not require decompilation: during device regression, check logcat for `cloudpush request|intercept update_sender` — if it does not appear, the class has drifted and this row's process applies (fail-fast already exists). |
| Gate hook | Added (`67e691ab6`) | See the fix in §2. |

Class KDoc records the removal rationale and the division of responsibility, to prevent future "empirical compatibility hooks" from being reintroduced.
