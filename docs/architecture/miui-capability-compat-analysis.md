# MIUI 能力兼容分层分析：属性、类与谓词（2026-09-13）

起因：Douyin 40.4.0 在类原生 Xiaomi ROM 上不初始化 MiPush provider（外部验证报告
`UPSTREAM_REPORT_DOUYIN_MIUI_GATE.md`）。本报告解释为什么 Zygisk 与框架两层伪装都
没接住它，盘点三套「已知面」的漂移，并给出防复发机制。

## 1. 结论先行：三层能力模型

第三方 SDK 判断「这是 MIUI/HyperOS」有三个互相独立的技术层，每层的伪造手段不同，
**任何一层都不能替代另一层**：

| 层 | SDK 实际读取的东西 | 我们现有的伪造手段 | Douyin 案例 |
|---|---|---|---|
| L1 属性层 | `SystemProperties.get("ro.miui.*")`、`Build.*` 静态字段 | Zygisk（`pre_app_specialize` 改 Build 字段 + hook SystemProperties native getter）；框架 `FakeProperty`（app 进程内） | 通过——Zygisk 默认 profile 提供 90 项属性 |
| L2 类路径层 | `Class.forName("miui.os.Build")`——类是否存在，由 ROM 是否携带 `/system_ext/framework/miui-framework.jar` 决定 | 框架 `Common.fakeClass()`（把 `miui.os.Build`/`miui.external.SdkHelper` 映射到自带 shim） | **失败**，见 §2 |
| L3 SDK 谓词层 | app 自己的封装方法（如 `ToolUtils.isMiui()`：解析 L2 并缓存结果；不同 app 还有各自的谓词） | 此前无任何 hook 对准真实谓词 | **失败**——provider selector 卡在这层 |

「Zygisk 不是仿冒全属性吗，怎么漏了一个字段」——**它没漏字段：`miui.os.Build` 根本不是属性**。
它是 HyperOS framework 挂载表里的一个类（连 EEA 版 HyperOS 都带 `miui-framework.jar`，所以
真 HyperOS 永远能解析到；类原生 ROM 则整个类不存在）。属性仿冒在机制上无法凭空造出类。
这正是 L1 与 L2 的边界。

## 2. 框架侧为什么也漏：三重独立失误叠加

1. **guard 假设错误**（`FakeDevice.kt`）：`BRAND/MANUFACTURER==Xiaomi → 跳过全部 pipeline`。
   该假设把「品牌是小米」当成了「MIUI 框架类存在」——类原生 Xiaomi ROM 恰好打破它；
   更讽刺的是**非小米设备 + Zygisk 把 BRAND 改成 Xiaomi 后，也反过来触发了这个跳过**，
   连 `fakeClass` 都没装上。两头堵死。
2. **overload 假设错误**（`Common.fakeClass()`）：只 hook `Class.forName(String,boolean,ClassLoader)`
   三参版本；Douyin 的 `X.0TbU.G0(String)` 走单参 `Class.forName`，在 ART 里单参版直接进
   native `forName0`，**不经过公开三参方法**，方法级 hook 截不到。
3. **hook 目标年代错误**（`DouYin.kt`）：挂的是 `socialbase.appdownloader.util.MIUIUtils.isMIUI*`
   ——downloader 组件的 MIUI 判断（历史 MiPush 接入文章时代的知识沉淀），与 message SDK 的
   provider selector（`com.ss.android.message.util.ToolUtils.isMiui`）类、调用方、职责都不同。

修复（`67e691ab6`）：新增 `DouyinMiuiGateHook`，精确签名匹配（static/0 参/primitive boolean
`isMiui`）、`doAfter` 只改结果（原方法照跑、缓存行为不受干扰）、类/方法缺失或签名歧义时
**fail-closed 留诊断不盲挂**；安装点放在 guard **之前**（复用 `AgooClickDecryptHook` 的先例模式），
`DOUYIN` pipeline 在 profile 里即生效。

## 3. 「还有哪些已有、已知的面没被覆盖」——三套清单 diff 实录

数据源：`MiPushZygisk config.rs DEFAULT_SPOOF_PROPS`（131 行全量）、框架
`FakeProperty.kt`（51 键）、rubens HyperOS dump `miui-framework/sources/miui/os/Build.java`
（真类 88 个 public statics + 静态块实际读取的属性）。

### 3.1 Zygisk 有、FakeProperty 无（27 项，节选类别）
- 区域/locale 面：`ro.miui.build.region`、`ro.vendor.miui.region`、`ro.product.locale[.language]`、
  `persist.sys.oppo.region`、`ro.hw.country`、`ro.csc.countryiso_code`、`gsm.vivo.countrycode` 等
  （**区域键缺失会影响 SDK 选区**，注意我们刚给高德/支付宝排过 region 相关链路）；
- 身份细节：`ro.product.{system,vendor}.{manufacturer,brand}`（部分 app 绕过别名读分区级属性）、
  `ro.build.description/product`、`ro.product.property_source_order`；
- HyperOS 特性开关：`persist.sys.miconnect.running`、`persist.sys.millet.handshake`、
  `ro.miui.enable_cloud_verify`、`ro.mi.development`、`ro.rom.zone`、`ro.fota.oem`。

### 3.2 FakeProperty 有、Zygisk 系统属性面无（2 项，但成对机制不同）
- `ro.build.fingerprint`：Zygisk 只改 `Build.FINGERPRINT` **字段**，不写 `ro.build.fingerprint`
  **属性**——走 `SystemProperties.get` 读 fingerprint 的 SDK 在 Zygisk-only 设备上仍见真值；
- `ro.build.version.sdk`：Zygisk 改 `Build.VERSION.SDK_INT` 字段，同名属性未覆盖。

### 3.3 真机 `miui.os.Build` 静态块读取、但两套清单都不覆盖的属性（L2 语义缺口）
`ro.miui.cts`（→IS_CTS_BUILD）、`ro.miui.has_cust_partition`、`ro.build.characteristics`、
`ro.debuggable`（→IS_DEBUGGABLE）、`ro.cust.test`、`ro.miui.userdata_version`、
`persist.sys.user_mode`、`persist.sys.miui_optimization`、`ro.soc.name`、`ro.carrier.name`、
`ro.config.low_ram.threshold_gb`、modem 系（`ro.boot.modem`/`persist.*.modem`）。
→ 若某 SDK 不满足于「类存在」而读取这些派生字段，仍会拿到与真 HyperOS 不一致的值。

### 3.4 shim 面 vs 真类面（最大的量化缺口）
`fakeclass/MiuiBuild.kt` 提供 5 字段 + `getRegion/getCustVariant`；真类 **88** 个 public statics
（`IS_CTA_BUILD`、`IS_FOLD`/device-level 常量、carrier 定制位等），且真类 `extends android.os.Build`。
当前靠「gate 只查类存在性」侥幸成立（Douyin 案例已改为直接 predicate，不依赖 shim）；
任何走 `fakeClass` 三参路径、反射读缺失字段的 app 会撞 `NoSuchFieldError`。
另有一处三源值漂移：`ro.miui.cust_variant` = `cn`（Zygisk）vs `cn_chinatelecom`
（FakeProperty 与 shim.getCustVariant 一致）——同一属性两个伪造通道给出不同答案。

## 4. 防复发机制

- **P1（已生效）**：能力兼容一律走「app 进程内精确谓词 hook + fail-closed + 诊断」模板
  （`DouyinMiuiGateHook`/`AgooClickDecryptHook` 两个先例），属性/类 shim 只作辅助；
  `xposed/AGENTS.md` 已加规则：*谓词/类存在性检查不允许假定为属性问题*。
- **P2（原则）**：属性清单**不新建共享模块**（`MiPushConfigurations` 仓库是狭义的 resetprop 模板仓，
  不承担清单收敛）；Zygisk 与 FakeProperty 各自就近维护，但漂移必须在设备回归时按 §3 清单核对，
  改任何值都视作 wire 可见变更走真机验证。
- **P3（建议）**：shim 契约测试——从 dump 提取真 `miui.os.Build` public 成员表为测试资源，
  断言 shim 覆盖「常见探针对集」且缺失项有显式豁免清单；`fakeClass` 若保留，扩为同时覆盖
  单参/三参 forName。

## 5. DouYin pipeline 历史债处置（本次执行）

| 段 | 处置 | 依据 |
|---|---|---|
| downloader `MIUIUtils.isMIUI/isMIUI6Later` 强制钩子 | **删除** | 报告证伪其注释假设：provider 门是 `ToolUtils.isMiui`，downloader 谓词从不参与 push 初始化；钩子只盲改下载器行为（原注释「critical for DouYin to initialize MiPush SDK」即当年误判的化石） |
| flyme 属性四行清理块 | **删除** | 与 `Common.fakeAllBuildInProperties` 完全重复：flyme 三键在 VENDOR_CLEAR_PROPS（无条件清），display.id/user 在一切能触发该分支的非小米设备路径上本就会被处理 |
| cloudpush `update_sender` 改写段 | **保留，待设备验证** | 与 provider 门互补的第二机制（服务端渠道名单 `allow_push_list` 里保 1=小米通道）；报告未覆盖它。死活取证不需要反编译：设备回归时看 logcat `cloudpush request|intercept update_sender` 是否出现——不出现则类已漂移，按本表流程删除（fail-fast 已有）|
| gate hook | 新增（`67e691ab6`） | 见 §2 修复 |

类 KDoc 已记录删除理由与分工，防止后人把「经验式兼容钩子」再养回来。
