# MiPushFramework Manager/Runtime 应用拆分 —— 全盘代码审查

- **审查日期**：2026-07-22
- **仓库**：`/home/lzc/wqk/push/MiPushFramework`
- **分支**：`feature/manager-runtime-app-split`
- **审查范围**：`e318d170..521c9a0d`（10 个提交，165 文件，+11776 / -370）+ 整改提交 `21362d43`（二次 review，见 §11）
- **HEAD**：`21362d43053fab5428838bd39226674721e9c480`（整改后；工作区干净，未推送，无上游跟踪）
- **计划文档**：`docs/architecture/manager-runtime-app-split-plan.md`
- **原始来源**：codex 会话 `019f7e25`（分析）→ `019f858d`（grok-4.5 推进 Phase 2–6）
- **审查方式**：5 个并行子代理分维度审查 + 主代理独立读源码交叉验证 + 独立 Gradle 构建

---

## 1. 结论摘要

拆分的**架构与安全边界是扎实的**：`manager-api` 零运行时依赖、信任边界（签名 + 调用方 UID + 包名白名单 + 逐调用校验 + `clearCallingIdentity`）在全部 12 个端点一致落地、wire 协议自洽（长度前缀帧 + 读后边界校验 + 全字段有界）、模块边界有真实脚本强制、提交按阶段清晰切分。

但 **"所有阶段完成并验证"的说法在两个阶段的核心不变式上不成立**，两个独立子代理 + 主代理源码复核三方一致确认：

- **Phase 4 幂等性形同虚设**（HIGH）：唯一调用方每次 `UUID.randomUUID()`，去重永不命中，"Binder 死亡后重试不重复破坏性操作"的退出标准未达成。
- **Phase 3 上传配置成为孤儿**（HIGH）：上传写入的目录无任何代码读取，"原子替换活动快照"未实现；且拒绝路径泄漏 FD。
- **Phase 2 三个遗留问题**在 Phase 3–6 中未被触碰，仍然存在（事件分页无累计字节预算、log_export FD 超时泄漏、配置目录在 Binder 事务内阻塞网络 I/O）。

这些均为**代码侧结构问题**，与"设备/ROM 实测挂起"这一已声明的范围外事项无关。计划文档正文对未证明项相当诚实（反复声明 no-device-test），仅个别退出标准清单与实际不符。

### 验证状态

| 项 | 结果 |
|---|---|
| 独立 Gradle 构建 / 编译 / 单测（我方执行） | `BUILD SUCCESSFUL`，exit 0 |
| `scripts/verify_module_boundaries.sh` | passed |
| 设备 / ROM / Binder 进程死亡 instrumentation | 未做（no-device-test 政策，已声明） |

> 注：我方独立构建约 1 分钟，为编译 + 定向单测子集，**非** codex 声称的全量 196 类 xmsf 套件（那需 10–13 分钟）。全绿仅覆盖我方跑到的任务；FD 生命周期、事务大小、进程死亡等路径均无测试覆盖。

---

## 2. 阶段落地情况

| 阶段 | 提交 | 内容 | 状态 |
|---|---|---|---|
| 1 传输基础 | `cd7fd226` / `1e82f374` | manager-api 协议 + AIDL、签名认证 Binder 服务、manager-client 绑定/重连/超时、连接快照对比 | ✅ 基本稳固 |
| 2 只读路径 | `0e0cb4e7` / `1dd53afc` | 应用列表/详情/诊断、事件、通知渠道、配置目录、日志导出 | ⚠️ 3 个遗留问题 |
| 3 偏好/配置 | `61a4f850` | 偏好归属分类、运行时偏好快照、迁移快照、配置内容 FD 上传 | ⚠️ 上传孤儿 + FD 泄漏 |
| 4 写路径 | `6f0fb917` | 应用更新、事件删/恢复、XMPP host、清历史、留存控制 + 幂等缓存 | ❌ 幂等失效 |
| 5 mipush 托管 | `1fc6cf0d` | manager UI 入驻 :mipush、manager-owned Koin、Binder 远程网关 | ✅ 结构正确 |
| 6 默认 APK 拆分 | `a6c9ec31` / `521c9a0d` | composition flavor（split/bundled）、activity-alias 转发、widgets 迁到 :mipush | ✅ 结构正确 |

当前形态：`:mipush` = Manager UI + 远程网关 + widgets + Xposed；`:app` split（默认）= 仅 runtime + 转发 alias；`:app` bundled = 对比基线（仍含进程内 Manager UI）。协议 MAJOR=1 / MINOR=4。

---

## 3. 阻断级 / 高危问题

### H1 — Phase 4 幂等存储形同虚设：调用方每次生成新 UUID
**严重度：HIGH　｜　CONFIRMED（源码复核）**

- `manager/.../remote/RemoteWriteSupport.kt:25`：唯一写入口每次 `requestId = UUID.randomUUID().toString()`。
- `ManagerWriteRequestDto` 的 KDoc 明确要求"重试时复用同一 requestId 以便运行时返回既往结果而非重复执行破坏性操作"——实际调用方做了完全相反的事。
- 后果链：manager 调 `restore_event` → 运行时执行成功 → Binder 在结果返回前死亡（客户端得到 `DeadObjectException` → `Unavailable`）→ 用户重试 → **新 UUID** → `idempotencyStore.get()` 必然 miss → 事件被再次插入。
- `ManagerWriteRuntimeExecutor` 里整条 `WRITE_STATUS_DUPLICATE` 分支在实践中是死代码。
- **Phase 4 退出标准"retry after Binder death cannot repeat a destructive action silently"未达成。**

**修复**：由操作身份派生稳定 requestId（operation + eventId + package + argument 的哈希），或在网关层为同一逻辑动作生成一次 ID 并跨重试复用。

### H2 — `restore_event` 天然非幂等 + 幂等账本不跨进程死亡存活
**严重度：HIGH　｜　CONFIRMED**

- `restoreEvent` → `EventRepository.restoreEvent` 以 `id = null` 插入**新行**，每次执行都产生一份副本。
- `ManagerWriteIdempotencyStore` 是 `ManagerRuntimeService` 进程内的纯内存 `LinkedHashMap`。而"Binder 死亡"通常正是 **XMSF 运行时进程被杀** ——重启后缓存清空，重连后毫无去重记忆（独立于 H1 也成立）。
- `delete_event` 按 id 天然幂等（二次删返回 false），`clear_history`/留存大致幂等，唯 `restore_event` 是确切反例。

**修复**：(a) 数据层让 restore 幂等（`INSERT OR REPLACE` 复用原 id）；且 (b) 幂等账本持久化（小 Room 表 / DataStore，按 requestId + TTL），以跨运行时重启存活。

### H3 — Phase 3 上传的配置快照是孤儿，"已激活"不持久
**严重度：HIGH　｜　CONFIRMED（reviewer grep）**

- `ManagerConfigurationUploadRuntimeWriter.upload` 把校验后的字节写入 `filesDir/manager_runtime_active_config/<basename>`（第 51–53 行），但**没有任何代码读回该目录**（全仓 grep `ACTIVE_CONFIG_DIR` / `manager_runtime_active_config` 仅命中写入方）。
- 运行时真正的配置源仍是 SAF 树 `config_directory`，经 `Configurations.getInstance().init(context, treeUri)` 加载（`ConfigCenter.kt:67` 等 4 处）。
- 上传唯一实际效果是内存里的 `configurations.load(text)` 合并；进程死亡或任何从 SAF 树的重新 init 都会丢失该合并并回退。
- **设计不变式"XMSF 原子替换其私有活动配置快照"未实现**；`activated=true` 夸大了结果。

**修复**：把运行时配置加载路径改为读取持久化的快照目录（作为权威或叠加层），或让上传写入 loader 实际读取的位置。

### H4 — Phase 2 事件分页无累计字节预算，超限整页失败
**严重度：HIGH　｜　CONFIRMED（源码复核，Phase 2 起未变）**

- `ManagerEventRuntimeReader.readPage` 把每行全量映射（单条 payload 上限 256KB），但**无累计字节预算**，不同于 `ManagerApplicationRuntimeReader.takeBoundedPage`（超 `maxPayloadBytes` 即提前截断）。
- service 端 `getEventPage` 随后用 512KB 预算跑 `validateEventPage` → `estimateEventPageWireBytes`（逐条累加 `payload.size`）。`pageSize` 最大 100、单条 payload 最大 256KB，**两条大 payload 即超 512KB** → 抛 `event_page_payload_too_large` → `invalidArgument` → 客户端 `callCapability` 捕获为 `Failed`。
- 整页失败而非返回有界页，直接戳中 Phase 2 退出标准"transaction-size 覆盖最坏页"，且与 `0e0cb4e7` 应用切片的有界分页不一致。

**修复**：仿 `takeBoundedPage`，映射时累加估算 wire 字节并提前停止（并给续页 token，或丢弃"仅用于对比保真"的 payload/regSec）。

### H5 — Phase 3 配置上传在拒绝路径泄漏 FD
**严重度：HIGH　｜　CONFIRMED（源码复核）**

- `descriptor.close()` 只在从第 40 行开始的 `try...finally` 内。三个早返回发生在此**之前**：
  - 非 `.json`（第 26–28 行）：`readLimited` 从未调用，收到的 FD 完全没被碰过 —— **确切泄漏**。
  - `readLimited` 返回 null（第 29–30 行）、内容过大（第 31–33 行）：`readLimited` 内 `FileInputStream(descriptor.fileDescriptor).use{}` 关掉了内层 fd，但 `ParcelFileDescriptor.close()` 从未调用 —— 依赖 FIS 副作用关闭共享 fd，脆弱。
- manager 反复以非 .json 名重试即可持续泄漏 fd 直至耗尽运行时 fd 上限。

**修复**：先取得 descriptor，整个方法体包进 `try { … } finally { runCatching { descriptor.close() } }`；用 `ParcelFileDescriptor.AutoCloseInputStream(descriptor)`（仓内 `LogExportReadSources.kt:139` 已有此惯用法）替代 `FileInputStream(descriptor.fileDescriptor)`。

---

## 4. 中危问题

### M1 — Phase 4 `clear_history` 丢弃参数，范围/截止清除静默变成留存裁剪
**CONFIRMED**　三个 manager 操作（`clearHistory()` / `clearHistoryBefore(cutoff)` / `clearHistoryInRange(start,end)`）全映射到 `WRITE_OP_CLEAR_HISTORY`，但 executor 分支**完全忽略** `longArgument`/`argument`，直接调 `runtimeActions.clearHistory()`（= `EventRetentionManager.pruneNow()` 留存裁剪）。用户选"清此日期及更早"或"只清今天"，运行时却按配置留存窗口裁剪，可能删掉与请求不同的事件集；两处调用方还硬编码 `return 0`，UI 恒报"已清 0 条"。**修复**：新增 `clear_history_before`/`clear_history_in_range` 操作或按参数分支，并回填真实删除数到 `resultLong`。

### M2 — Phase 4 幂等存储 TOCTOU（check 与 act 非原子）
**CONFIRMED（潜伏，被 H1 掩盖）**　`execute` 的 `get → dispatch → put` 各自 `synchronized`，但 dispatch 期间不持锁。Binder 线程池并发投递同一 requestId 时两者都 miss、都执行、都 put，无单飞保证。H1 修复后此窗口即活化。**修复**：锁内先插入 in-progress 标记预留 id，并发同 id 阻塞或返回 duplicate。

### M3 — Phase 4 结果校验在破坏性操作提交后才抛异常
**PLAUSIBLE**　`executeWrite` 先 `writeExecutor.execute()`（副作用已发生、结果已缓存），再 `validateWriteResult(it)?.let(::invalidArgument)` 抛 `IllegalArgumentException`。校验失败会让"已发生的破坏性动作"对调用方表现为通用失败，叠加 H1 导致用户重试再次执行。当前内部构造值有界故不易触发，但"执行→校验→可能抛"的顺序对写路径是错误形状。**修复**：写路径校验不抛，降级为携带既有结果的 `failed` 并记日志。

### M4 — Phase 3 `Configurations.load` 是合并而非替换
**CONFIRMED**　`ConfigurationsLoader.load` 先 `HashMap(packageConfigs)` 再逐包 `target[pkg]=…`，是叠加式合并。上传一份省略了某已存在包的配置，会保留该包旧规则。设计称"原子替换"。叠加 H3，"活动快照"语义与文档不符。**修复**：replace 语义应先 clear 再 parse 进全新 map。

### M5 — Phase 3 并发上传不安全
**CONFIRMED**　`Configurations` 是进程单例，writer 无序列化。同 `path` 两个并发上传写同一 `$path.tmp`，交错写入 + rename 前损坏；且 `previous`/`restore` 捕获有竞态（A 持久化失败会用 A 的前像 restore，静默丢弃 B 的合并）。**修复**：writer 级锁序列化；每次上传用唯一 temp 名。

### M6 — Phase 2/3 Binder 线程阻塞 I/O
**CONFIRMED**
- **配置目录（Phase 2, 偏 HIGH）**：`ManagerConfigurationCatalogRuntimeReader.readCatalog` 缓存未命中时 `runBlocking { catalogService.fetchCatalog(source) }` —— 真实网络 GET（`withContext(Dispatchers.IO)`）跑在 Binder 线程上，占住整段网络时长。客户端 3s 超时会释放会话，但按文档自述**无法中断在途 transact**，运行时 Binder 线程仍被占且继续写 `syncStateStore`。
- **偏好读取（Phase 3）**：`ManagerPreferenceRuntimeReader` 用 `runBlocking { exportOwnedPreferences() }` 阻塞 Binder 线程读 DataStore。
- **修复**：事务内只服务缓存（miss 时返回空目录回退），网络刷新走异步/回调；或给 Binder 线程内工作加短 deadline。

### M7 — Phase 3 迁移仅导出侧，幂等性尚不可验证
**CONFIRMED（范围，计划已承认）**　`getManagerMigrationSnapshot`/`getRuntimePreferences` 端到端接通，但无任何 import/seed 消费方。`exportOwnedPreferences` 遍历 `prefs.asMap()`，取默认值的键被省略 —— 仅当 manager 存储应用相同默认值才正确，`settings` 尚共享时成立，一旦存储分裂即为隐患（`islandEnabled`/`isStartForeground` 等默认 true）。无 value 解析/应用回路，"升级保持运行时行为"无法从本提交验证。

---

## 5. 低危 / 加固建议

- **L1（Phase 4）** 写路径包名校验弱于读路径：`executeWrite` 只走 `validateWriteRequest`（非空才校验、缺字符白名单），读端有 `requireValidPackageName` 的 `isLetterOrDigit||'.'||'_'` 白名单。`update_application` 对 `argument.split(',')` 得到的 `type` int 无上界。**修复**：写路径复用 `requireValidPackageName`，约束 `type`。
- **L2（Phase 4）** `runCatching { dispatch }.getOrElse { failed(...) }` 吞掉所有 Throwable（含 `CancellationException`）且不记录 cause，现场调试困难。**修复**：记日志（遵循 logging-neutralization 约定），勿吞 `CancellationException`。
- **L3（Phase 4/5）** root/权限修复/Zygisk 写在 split manager 中是静默 no-op（返回 false/Unit/ROOT_MISSING），而非可解释的 typed `unsupported`。**修复**：网关边界暴露显式 `Unsupported`。
- **L4（Phase 2）** log_export 超时/取消/binder-death 路径 FD 软泄漏：`callRemote` 超时仅 `deferred.cancel()`，在途 transact 完成后携开启 FD 的 DTO 被丢弃而不关闭；靠 PFD finalizer 非确定性回收。`SettingsViewModel.buildRuntimeLogBundle` 可反复触发。**修复**：为携 closeable 的能力在 `invokeOnCompletion` 或丢弃路径加 close 钩子。
- **L5（Phase 3）** 非原子 rename fallback（`!renameTo && !(delete && renameTo)`）rename 再失败会丢失前一文件。同目录 rename 通常原子，且文件当前无人读（H3），影响低。
- **L6（Phase 3）** `contentLength` 声明过小会把合法文件截断为 `read_failed` 而非 `too_large`。
- **L7（hygiene）** `RemoteWriteSupport.execute` 及多个 `RemoteManagerGateways` override 用 `runBlocking{}` 在调用方线程 —— 若被 UI/主线程同步网关调用，阻塞至多 3s Binder 超时，**ANR 风险**。
- **L8（hygiene）** `MiPushWidgetCanvas.widgetMinHeightDp` 疑似未使用；`ManagerWriteIdempotencyStore` 冗余 import；无 `println`/`Log.d`/`TODO`/`FIXME` 残留（干净）。

---

## 6. 测试缺口

- Phase 4：**无 `ManagerWriteRuntimeExecutor.dispatch` 测试**（delete/restore/clear/update 语义、duplicate 路径、clear-history 丢参数 bug 均无覆盖）；`executeWrite` 无专门未授权 UID 测试。仅有幂等存储单测 + parcelable round-trip。
- Phase 2：log_export 带描述符的 wire round-trip 未测（只测 null 分支）；4 个新能力的 `Unavailable/Unsupported/Failed/timeout` 未测；分页 cap/环检测未测。
- 全局：**无 `androidTest`/instrumentation 源集**。所有覆盖为 JVM/Robolectric。FD 生命周期、事务大小、进程死亡、未授权证书等 Verification Matrix 要求项均无对应测试（文档已诚实声明）。

---

## 7. 做得好的地方

- **信任边界扎实且一致**：`onBind` 校验 action + 精确 component；`enforceTrustedCaller` 逐调用查 UID + `checkSignatures` + 包白名单；服务经签名级权限 `BIND_MANAGER_RUNTIME` 导出；每笔事务 `clearCallingIdentity` 包裹。12 个端点无一绕过。
- **manager-api 真正零运行时依赖**，边界由真实脚本（grep FQCN/类名串/build 依赖）强制，非君子协定。依赖图无环：manager-api ← manager-client ← manager ← {app,mipush}；xmsf → manager-api。
- **WireParcel 稳健**：长度前缀帧 + 读后 `enforceFrameBoundary` + 全部读操作有界 + marker 严格校验；两个 page DTO 写/读字段顺序完全对齐。
- **log_export 只读且无路径注入**：`MODE_READ_ONLY`，`exportRuntimeLogs()` 无入参，遍历结构上不可能；归档不含 Room/DataStore，打包前经 `DiagnosticFileSanitizer` 脱敏。
- **对比诊断只报字段名不泄露值**（`compareEventLists`/`compareSnapshots`/`compareCatalogs` 均 `Mismatched(fields.distinct().sorted())`，单测断言无值泄漏）。
- **通知渠道分页双保险**：`MAX_PAGE_REQUESTS=64` + `seenTokens` 环检测。
- **客户端会话生命周期严谨**：在途调用上限 `Semaphore(2)`、迟到结果隔离、death-recipient link/unlink、有界重连，且诚实反映了 synchronous AIDL 无法硬中断 transact 的局限。
- **Phase 6 拆分结构正确**：`composition` flavor（split 默认不编译 :manager，`Class.forName` 反射加载 bundled 专属 `ManagerDependencies`）、activity-alias 转发到独立 manager 包（targetClass 走 `LEGACY_MANAGER_ACTIVITIES` 白名单）、widgets 完整迁移 + 包名/action 串改写 + manifest 契约测试更新。
- **计划文档罕见地坦诚**：反复声明 no-device-test、gateway cut-over 仍开放，限制了硬夸大的数量。

---

## 8. 建议优先级

1. **H1 + H2 一起**（稳定/复用 requestId + restore 幂等或持久账本）—— 否则 Phase 4 存在意义（无静默重复破坏性写）不成立。
2. **H3 + M4**（上传配置真正被运行时读取 + replace 语义）—— 否则 Phase 3 "原子替换活动快照 / 升级保持行为"不成立。
3. **H4**（事件分页有界化）—— 修复整页失败，恢复与应用切片一致。
4. **H5 + L4**（配置上传拒绝路径 + log_export 超时路径的 FD 关闭）。
5. **M1**（clear_history 丢参数导致错误删除）。
6. **M6**（配置目录网络在 Binder 事务内）。
7. **测试**：executor dispatch 套件、未授权 `executeWrite` 测试、FD round-trip / 关闭路径测试。

---

## 9. 文档 vs 实现的偏差（供更新 plan 文档参考）

| 文档处 | 声称 | 实际 | 处理建议 |
|---|---|---|---|
| line 61 | "comparison probes close remote descriptors immediately so FDs are not retained" | 仅 success 路径成立，超时/迟到路径不确定性关闭（L4） | 措辞去绝对化 |
| Phase 2 退出标准 line 316 | "manager 不再直接访问运行时 DB 或平台通知对象" | manager 仍在 `RemoteManagerGateways.kt:271` 重建 `NotificationChannelGroup` 平台对象；DB 访问确已消除 | 标注平台通知对象 seam 未闭合 |
| Phase 3 status | "atomically replaces the active snapshot" | 上传目录无人读，仅内存合并（H3）；load 是合并非替换（M4） | 降级表述为"内存合并，持久化快照消费待接线" |
| Phase 4 status/退出标准 line 353 | "retry after Binder death cannot repeat a destructive action silently" | 幂等失效（H1）+ 不跨进程死亡（H2） | 明确标注未达成 |

---

*审查方法：security/trust-boundary、read-projection/plan-fidelity、wire-protocol、concurrency/lifecycle、doc/boundary/build 五维并行子代理；Phase 3、Phase 4 专项子代理；主代理独立读源码复核 H1（`RemoteWriteSupport.kt:25`）、H4（`ManagerEventRuntimeReader`）、H5（`ManagerConfigurationUploadRuntimeWriter`）、Phase 6 manifest 与 `ManagerUiRedirectActivity`，并独立执行 Gradle 构建（`BUILD SUCCESSFUL`, exit 0）。*

---

## 10. 整改落地（2026-07-22 follow-up）

本地 `feature/manager-runtime-app-split` 已按审查优先级完成下列修复（未 push）：

| ID | 处理 | 状态 |
|---|---|---|
| H1 | `RemoteWriteSupport.stableRequestId`：由 operation+args 的 SHA-256 派生稳定 requestId，重试可命中幂等账本 | 已修 |
| H2 | `EventRepository.restoreEvent` 对原 id 走 `insertOrReplace`；内存幂等账本仍不跨进程死亡，但 restore 数据层幂等 | 已修（账本持久化仍可选） |
| H3 | `ActiveConfigurationSnapshotStore` 持久化 + `ConfigurationsLoader.init` 结束后 `applyTo` 叠加；`loadInto` 解析到 staging map | 已修 |
| H4 | `ManagerEventRuntimeReader.takeBoundedSummaries` 累计 wire 预算，必要时丢 payload/regSec | 已修 |
| H5 | 配置上传全程 `try/finally` 关闭 descriptor；读取改用 `FileInputStream(fd)` 避免 AutoClose 双关 | 已修 |
| M1 | `clear_history` 按 longArgument/argument 区分 before/range/full；远程网关返回 `resultLong` | 已修 |
| M2 | `ManagerWriteIdempotencyStore.begin/complete/abort` 单飞预留，消除 get→dispatch→put TOCTOU | 已修 |
| L4 | `ManagerRuntimeClient` 在校验失败/会话失效/超时迟到完成时 `discardOwnedWireResources` 关闭 log-export FD | 已修 |

仍开放：

- 设备/ROM 矩阵与跨包安装证据（策略：无明确要求不做 adb）
- 幂等账本跨进程持久化（可选加固）
- 部分 Phase 2 退出标准中的平台 Notification 对象 seam、全量 instrumentation/FD 生命周期矩阵
- 配置 upload 仍是 merge 语义，不是整库原子替换（审查 M4）

文档：`docs/architecture/manager-runtime-app-split-plan.md` Phase 2–4 status / 退出标准已按上表诚实化。

---

## 11. 整改复审（2026-07-22 二次 review）

- **复审对象**：整改提交 `21362d43 fix(manager): harden split write/config paths after review`（15 文件，+508 / -86），叠在 `521c9a0d` 之上，HEAD=`21362d43`，工作区干净、未推送。
- **复审方式**：主代理逐条读整改 diff 对照原 findings + 1 个对抗性子代理全链路追踪 restore 幂等 + 独立 Gradle 构建。

### 验证结论：H1–H5、M1、M2、L4 均**源码坐实修复**

| ID | 复核证据 | 判定 |
|---|---|---|
| H1 | `RemoteWriteSupport.stableRequestId` 由 `operation+pkg+eventId+args` 拼接后 SHA-256 hex（64 字符 < 128 上限，`.take` 不截断），重试同一逻辑写产生同一 id → 命中账本。新增 `RemoteWriteSupportTest` 断言确定性 + 参数敏感性。 | ✅ 真修复 |
| H2 | 对抗性追踪确认：UI delete→undo 全程保留 `event.id`（>0）；`EventRepository.restoreEvent` 对原 id 先 `getByIdAsync` 早返回、否则 `insertOrReplace`（REPLACE on PK）；表为 `INTEGER PRIMARY KEY AUTOINCREMENT`，SQLite 不复用 rowid → 冷缓存重试也**不产生重复行**、不误伤新行。 | ✅ 真修复（依赖 AUTOINCREMENT）|
| H3 | 新增 `ActiveConfigurationSnapshotStore`：`persist` 落盘 + `applyTo` 在 `ConfigurationsLoader.init` 结束后叠加进 `newConfigs`；`loadInto` 解析到 staging map。上传的配置现在**确实被运行时读取**并跨进程重启存活。 | ✅ 真修复 |
| H4 | `takeBoundedSummaries` 累计 `estimateSummaryWireBytes` 至 `maxPageWireBytes`，单条超限先丢 payload/regSec 再放不下才 break；首条保底至少 1 项 → 整页失败变有界截断。 | ✅ 真修复 |
| H5 | `upload` 整体 `try { … } finally { runCatching { descriptor.close() } }`，not-json/read-failed/too-large 早返回也在 try 内 → FD 全路径关闭。 | ✅ 真修复 |
| M1 | `clearHistory` 按 `longArgument>0 && end>start` → range、`longArgument>0 && arg 空` → before、否则 full；远程网关回填 `resultLong` 计数。 | ✅ 真修复 |
| M2 | `ManagerWriteIdempotencyStore.begin/complete/abort` 用 `ReentrantLock+Condition` 单飞预留：并发同 id 阻塞等待→返回 `duplicate`；executor 用 try/complete/abort 保证异常释放预留。新增并发测试。 | ✅ 真修复 |
| M3 | executor 现 `try { dispatch; complete } catch { abort; throw }`，破坏性操作已提交则结果入账本；validate 仍在 service 层 act 后校验，但账本已记录，重试返回 duplicate。 | ✅ 缓解 |
| L4 | `ManagerRuntimeClient.discardOwnedWireResources` 在校验失败、会话失效、超时迟到完成(`invokeOnCompletion`)三处关闭 log-export FD。 | ✅ 真修复 |

### 独立验证状态

| 项 | 结果 |
|---|---|
| 独立 Gradle 构建（我方执行，全量日志核验） | `BUILD SUCCESSFUL in 5m 22s`，exit 0 |
| 对抗性 restore 幂等全链路追踪 | 正常 UI 流（id>0）冷/热缓存均无重复 |

### 残留（非阻断，多为已声明范围外或新发现的 latent）

- **[新发现·低] restore with eventId ≤ 0**：`ManagerEvent.toEvent()` 把 id≤0 映射为 null → 走普通 `insert` 分支，冷缓存重试无去重依据 → 可能重复行。**标准 swipe-delete/undo 流不可达**（始终 id>0），但任何以 null/0 id 发起的 restore 是潜在重复点。协议校验仅拒 `<0`、放行 `0`。建议：id≤0 时拒绝或补 requestId 持久去重。
- **[已知·低] restore 数据降级**：restore 的 wire 不带 `payload`/`regSec`，恢复行内容降级（与重复问题无关的保真问题）。
- 幂等账本仍进程内、不跨 XMSF 进程死亡持久化（文档已诚实标注为可选加固；H2 靠数据层 id 幂等兜底）。
- 配置 upload 仍是 merge 语义（`loadInto`→`parse` 逐包覆盖），非整库原子替换（原 M4，文档已降级表述）。
- M6（配置目录 `fetchCatalog` 在 Binder 事务内阻塞网络）本次未触碰，仍存在。
- 平台 Notification 对象 seam、全量 instrumentation/FD 生命周期矩阵仍 open（no-device-test 政策）。

### 总评

整改**精准、对症、无过度扩张**：15 文件全部落在原 findings 指名的文件上，新增 3 个测试（stableRequestId 确定性、幂等单飞并发、begin/complete/abort）。上一轮 5 个 HIGH 全部真实修复（H2/H3 是结构性重写而非补丁），构建独立验证通过。唯一新增的 latent 是 restore id≤0 回退路径,不影响正常流,可作为下一轮低优先项。

---

## 后续整改进展（2026-07-22 晚）

### 设置页闪退（已修）
- **现象**：进入设置立即闪退。
- **log 根因**：`IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`  
  Compose `painterResource()` 不支持 `layer-list`；默认预览用了 `drawable/ic_launcher_preview_default.xml`。
- **修复**：预览资源全部改为 PNG（`mipmap/ic_launcher_preview_*`），设置行/对话框仅用 raster；并加重装验证路径。

### 桌面图标策略（本次）
用户目标：更新后「原推送服务图标消失、只剩管理器入口；默认图标=原推送服务；模块图标=备选」，体验上是换位置而不是换应用。

| 包 | 变更 |
|---|---|
| `com.xiaomi.xmsf`（split） | 去掉 `WelcomeActivity` 兼容 alias 的 `MAIN/LAUNCHER`；保留组件名转发 alias，**不再占桌面图标** |
| `io.github.magisk317.mipush` | 默认 launcher alias / application icon = `@mipmap/ic_launcher_xmsf`（原推送服务图） |
| 备选 | `ManagerLauncherActivityLegacy` = 原模块图 `@mipmap/ic_launcher_legacy` |
| 已废弃第三选项 | `ManagerLauncherActivityXmsf` 去掉 LAUNCHER、默认 disabled；偏好 `xmsf` 归一为 `default` |

设置 UI 仅两项：`默认（推送服务）` / `模块`。

### 构建与安装（arm64）
- 目标：`:app:assembleNormalSplitDebug` + `:mipush:assembleDebug`，`-PbuildSplits=true`
- 设备：HP 远端 adb（model 25113PN0EC）
- 验证点：launcher query 不再列出 xmsf LAUNCHER；mipush 默认 alias 为 xmsf 图标；设置页可打开且有预览。

### 模块图标预览不一致（已修）
- **现象**：设置里「模块」预览像推送图标，桌面实际是蓝绿色自适应图标（桌面正确）。
- **根因**：`ManagerLauncherActivityLegacy` 在 API 26+ 用 `mipmap-anydpi` 自适应（teal `#018786` + 白色 foreground）；设置预览却用了旧的 `ic_launcher_legacy.png` 位图，观感不同甚至接近推送图。
- **修复**：按自适应同色/同 vector 栅格化生成 `ic_launcher_preview_legacy.png`；同步更新各密度 `ic_launcher_legacy.png` 回退位图；默认预览仍为 `ic_launcher_xmsf`。
- **已装**：`io.github.magisk317.mipush` `0.6.3-20260722_170217`。

---

## 12. 跟进（2026-07-22 晚）——双开 Root 误报 + Snackbar 统一 + 图标/层级

### 构建与安装

- 构建：`0.6.3-20260722_183633`，`:app:assembleNormalSplitDebug` + `:mipush:assembleDebug` arm64
- 设备已装：`com.xiaomi.xmsf` / `io.github.magisk317.mipush` 均为 `0.6.3-20260722_183633`

### 双开开关「已授权仍提示需要 Root」——日志与根因

**现象**：Magisk 已给 xmsf 与 manager 授权，杂项里「启用双开」仍 snackbar「需要 Root 权限」。

**日志证据**（不靠猜）：

1. xmsf 主进程里 `SystemNotificationManager` 大量 `root fallback` 成功 → **xmsf 进程 root 本身可用**。
2. `XSpaceXmsfInstallKeeper` 在冷启动时：`stage=ROOT_MISSING` → 启动瞬间 `Shell.isAppGrantedRoot()` 仍为 unknown 时，`refreshRootAccessIfGranted()` 直接 false（不 probe）。
3. 写路径 `set_dual_app` 此前**无专用日志**；`RemoteWriteSupport` 在 Binder 失败时把任意 `null` 映射成 `ROOT_MISSING`，UI 无法区分「真缺 root」与「运行时未连上」。

**代码根因（两处叠加）**：

| # | 位置 | 问题 |
|---|---|---|
| A | `RootAccessFacade.refreshRootAccessIfGranted` + `setDualAppEnabled` | grant 为 `null` 时不 probe；双开只调 refresh，不 `requestRootAccess()` 强制 Shell 初始化 |
| B | `ManagerWriteIdempotencyStore.complete` | **失败结果也被缓存**；`RemoteWriteSupport.stableRequestId` 对同一 `set_dual_app+enabled` 固定 id → 首次 `ROOT_MISSING` 后，即使后来有 root，重试永远命中幂等缓存 |

**整改（已合入 `_183633`）**：

1. `setDualAppEnabled` / `isDualAppInstalled`：`refresh` 失败后再 `requestRootAccess()`。
2. 幂等账本：**仅缓存 SUCCESS/DUPLICATE**，FAILED 可重试。
3. Binder 写失败 → `PARTIAL_FAILED` + `runtime_write_unavailable`，文案改为「运行时未连接」而非假 root。
4. `ManagerWriteRuntimeExecutor.setDualApp` 打 `logI("set_dual_app ...")` 便于复测。

### Snackbar 层级与统一

- 新增 `magisk-ui-kit` `ElevatedSnackbarHost`：**薄封装**，内部直接复用 `DismissibleSnackbarHost`（可滑动删除 + 默认动画/样式），外层 `Popup` 抬到底栏之上。
- 设置/总览/事件/状态栏图标/应用详情页改用该 host；事件页去掉重复的 SwipeToDismiss 手写，只保留 `DeleteCountdownSnackbar` 内容。

### 图标选择

- 选择图标对话框去掉描述性长文；设置行 summary 仅显示当前选项名。
- `applyAndRelaunch`：`AppTask.finishAndRemoveTask` + 清 recents + 延迟 `killProcess`，刷新多任务图标。

### 用户侧复测建议

1. 强停推送服务与管理器后打开管理器 → 杂项 → 启用双开。
2. 期望：成功「双开已启用」；或明确「未找到分身 999 / 运行时未连接 / 部分失败」，**不应**在已授权且 shell 可用时仍固定假 root。
3. xmsf 日志应出现 `set_dual_app enabled=... stage=...`。
4. Snackbar 应可滑动消失，且在底栏上方。

---

## 13. 分身 999 悬浮窗 + 全量 root 静默权限补齐（2026-07-22）

### 日志结论（999 悬浮窗列表无 mipush）

1. `set_dual_app enabled=true stage=COMPLETED … managerInstalled=true` —— 双开安装成功，user 999 上 xmsf/mipush 均 `installed=true`。
2. `appops get --user 999 io.github.magisk317.mipush SYSTEM_ALERT_WINDOW` 曾为 **ignore**；主用户列表的「显示在其他应用上层」**只枚举当前 user**，**不会出现 999 分身包** —— 属系统设置范围，不是包未安装。
3. 旧路径 `PermissionUtils.allowPermission` 只给 `com.xiaomi.xmsf` + `myUserId()`；manager 侧 `RemoteManagerPermissionGateway.launchAppOps/grantNotification/…` **全部 stub false**，向导静默授权在拆分后失效。

### 整改（构建 `0.6.3-20260722_190316`）

| 层 | 改动 |
|---|---|
| `PermissionUtils` | 统一 `grantSilentPermissions` / `grantSilentPermissionsForFramework`：主用户 + 999（若已装）×（xmsf + mipush）；覆盖悬浮窗、使用情况、通知、后台 run、电池白名单 |
| 双开完成 | `setDualAppEnabled(true)` 后 `USER_AUTO` 全量授予 |
| xmsf 启动 | `MiPushFrameworkApp` 主进程 `scheduleSilentPermissionGrants()` |
| 远程协议 | `grant_silent_permissions` / `query_root` 写命令；manager 网关接上 root 查询与静默授权（向导不再全 stub） |
| 幂等 | 查询 root 用 unique requestId，避免“无 root”成功结果被永久缓存 |

### 用户侧

- 无需在主用户「悬浮窗」列表里找 999 的 mipush；有 root 时应自动 allow。
- 复测：`appops get --user 0/999` 对两个包的 `SYSTEM_ALERT_WINDOW` / `GET_USAGE_STATS` 应为 allow。

---

## 14. 图标切换后设置页拉起失败 + 状态栏彩色开关重启拦截（2026-07-23）

### 日志结论（拉起设置失败）

设备 dumpsys（构建 `0.6.3-20260723_103404` 复现）：

```
Intent { flg=0x10000000 cmp=io.github.magisk317.mipush/.feature.main.MainActivity }
```

- 仅有 `NEW_TASK`，**没有** `CLEAR_TASK` / extras；`mLastPausedActivity=WelcomeActivity`
- 无 `AndroidRuntime` 崩溃；进程被 `killProcess` 后系统重拉 task 时 **Intent extras 丢失**
- 根因：同进程 `startActivity(MainActivity+route extras)` 后 `350ms killProcess` 竞态——AM 可能以不带 extras 的 task root 恢复，落到 Overview 而非 Settings

### 整改（构建 `0.6.3-20260723_105206`）

| 层 | 改动 |
|---|---|
| `LauncherIconController` | 抽 `relaunchTo`：SharedPreferences 持久化 `pending_resume_route`（commit 后 kill）、`AlarmManager` PendingIntent 带完整 extras、再 `startActivity` + 延迟 kill |
| `MainActivity` | Intent 无 extras 时 `consumePendingResumeRoute`；StatusBar/Connection 路由归 Settings tab |
| 状态栏彩色两开关 | 切换弹出「需要重启」确认；确认后写 manager 本地 + Binder `set_runtime_boolean` 写 xmsf 权威 prefs + `restart_runtime` 杀 xmsf 进程 + 管理器 relaunch 到 `status_bar_icon_settings` |
| 协议 | `WRITE_OP_SET_RUNTIME_BOOLEAN` / `WRITE_OP_RESTART_RUNTIME`（allowlist 仅两枚 color 键） |

### 附带根因

拆包后 color 状态栏键属 **RUNTIME** 所有，Island ContentProvider 读的是 **xmsf** DataStore。原先 manager 只写本地时，钩子侧可能一直读旧值——“不重启不生效”一部分是 **写错包**，不只是缺重启。现已双写 + 重启。

### 安装验证

- HP adb：`arm64` normalSplit xmsf + mipush 均 `0.6.3-20260723_105206`
- shell 带 extras 启动 MainActivity：`Intent (has extras)` 正常
- 请在设备上：切换应用图标应回到 **设置**；状态栏彩色开关应弹确认后重启并回到该页

### 用户侧复测

1. 设置 → 应用图标：切换后应回到设置页（非总览）
2. 设置 → 状态栏彩色：拨动任一开关 → 确认重启 → 管理器回到该页；xmsf 日志应有 `set_runtime_boolean` / `restart_runtime scheduled`
3. 系统「应用管理」包图标仍不随别名变化（平台限制，未变）

### 14.1 竞态复现与二次修复（2026-07-23 11:03）

设备 log（`0.6.3-20260723_105206`）时间线：

| 时间 | 事件 |
|---|---|
| 10:58:43.816 | `wm_on_resume_called` MainActivity（同进程 startActivity 成功） |
| 10:58:44.319 | `am_proc_died` killProcess |
| 10:58:44.327 | `proc died without state saved` |
| 10:58:48.800 | Alarm PendingIntent **BAL 拦截**：`Background activity launch blocked`，`balAllowedByPiCreator: BSP.NONE` |

结论：用户感知「先拉起再 kill」完全正确；Alarm 补救也被 API 34+ BAL 挡住。

修复 `0.6.3-20260723_110117`：

1. **禁止** dying 进程内 `startActivity`（只 finish task + kill）
2. `PendingIntent` 设置 `setPendingIntentCreatorBackgroundActivityStartMode(ALLOWED)` + sender mode
3. 优先 `setExactAndAllowWhileIdle`（manifest 加 `SCHEDULE_EXACT_ALARM`）
4. SharedPreferences `pending_resume_route` 仍作冷启动兜底

请复测：设置 → 应用图标切换 → 约 0.7s 后应自动回到设置页；log 不应再出现 BAL block。

### 14.2 只退出不拉起 + 无线 adb（2026-07-23）

产品决策调整：图标切换 / 状态栏彩色确认后 **只退出**（`finishAndRemoveTask` + `killProcess`），**不再** Alarm/`startActivity` 自动拉起，避免 start-then-kill 与 BAL 竞态。用户从桌面手动再开。

- 构建：`0.6.3-20260723_110857`（manager/mipush）
- 分析链路：HP 侧 `adb tcpip 5555` 后无线连接设备；USB 可断开
- 状态栏文案改为「应用并退出」

### 14.3 图标只退出 log 结论 + 状态栏改为整机重启（2026-07-23）

#### 图标切换（无线 log，`0.6.3-20260723_110857`）

| 时间 | 事件 | 含义 |
|---|---|---|
| 11:11:53.977 | 进程尾部 avc / 即将退出 | exitOnly 路径 |
| 11:11:54.019 | `am_proc_died` mipush | **只退出成功**，无自动 start |
| 11:11:54.633 | task removed | Recents 任务清掉 |
| 11:12:55.007 | `am_proc_start` **ManagerLauncherActivityDefault** MAIN | **用户桌面手动打开**（~61s 后），非 Alarm/BAL |
| — | 无 `Background activity launch blocked` | 不再自动拉起 |

结论：exit-only 符合预期；图标别名已切到 Default（推送服务风格）。

#### 状态栏彩色

产品更正：确认后 **整机 reboot**（root `reboot` / `svc power reboot`），不是杀管理器。

- 协议：`WRITE_OP_REBOOT_DEVICE`
- 写 prefs（manager 本地 + `set_runtime_boolean` → xmsf）后调度 reboot
- 构建：`0.6.3-20260723_111506`（xmsf + mipush 均已无线安装）

注意：整机重启会断开无线 adb，需重启后重新 `tcpip`/connect。

### 14.4 恢复图标切换自动弹回（2026-07-23）

说明：此前「只退出不拉起」是按用户当时「只退出，用无线分析 log」做的**诊断临时态**，不是产品终态。现已恢复「退出后自动弹回设置」。

实现（构建 `0.6.3-20260723_112339`）：

1. **主路径**：`WRITE_OP_RELAUNCH_MANAGER` → xmsf 进程延迟 root `am start` MainActivity（带 `extra_start_route=settings`），绕过 BAL
2. **兜底**：SharedPreferences `pending_resume_route` + AlarmManager（BAL creator allow）
3. **禁止** dying 进程内 `startActivity` 再 kill（旧竞态）
4. 状态栏彩色仍为**整机 reboot**（与图标弹回无关）

### 14.5 「大量批量注册」log 结论（2026-07-23）

证据：`run-as com.xiaomi.xmsf` → `files/log/runtime.2026-07-23.jsonl` 尾部约 800KB（本地 `/tmp/xmsf_runtime_tail.jsonl`），窗口 `11:25:31`–`11:28:34`，构建 `0.6.3-20260723_112339` 安装后。

#### 触发操作（根因）

| 时间 | 事件 | 含义 |
|---|---|---|
| ~11:25 | 安装/更新 `com.xiaomi.xmsf`（`lastUpdateTime` 对齐） | 运行时包被替换 |
| 11:25:31 | 旧 PID `32277` 仍处理少量 `REGISTER_APP` | 更新前/替换瞬间的 client 重试 |
| 11:25:44.873 | 新 PID `10046` `MiPushFrameworkApp` / `App starts: 0.6.3` | **冷启动** |
| 11:25:44.970 | `requestFrameworkRegistration reason=FirstRegister.run:initial_register` **`regIdPresent=false`** | 框架自身因 regId 空而重新注册 |
| 11:25:45.609 | `ProactiveMiPushRegistrar no new apps to register` | **主动扫描并未批量注册全机应用** |
| 11:25:45–48 | 少量 app `REGISTER_APP` + `MIPushAppRegisterJob` | 已安装 push 客户端在服务重启后重发注册 |

**结论：批量感来自「xmsf 重装/更新 → 进程冷启动 + 框架 re-register + 少数 app 重发 REGISTER_APP + 日志噪声放大」，不是「扫描全机并给所有应用注册」。**

#### 真实注册 vs 日志噪声（同窗口计数）

| 类别 | 约计数 | 说明 |
|---|---|---|
| `Utils.getRegSecs` | ~660 | 查 regSec 偏好，**不是**发起注册 |
| `RegisteredApplicationDb.registerApplication` | ~30 called / ~148 existing list | 多为**已有行**复用，不是新装注册 |
| Aspect/`Registration` payload 日志 | ~71–140 | 同一批请求的多层 log 重复 |
| `RegisterRecorder`「want to register push」 | **仅 7 次** | 真·REGISTER_APP 入口 |
| `MIPushAppRegisterJob` 实际 job | **7 次** | 见下表 |
| `skip duplicate register record` | 14 | 同一 app 重复 intent 被去重 |
| `Not a register app request`（`SEND_MESSAGE` 误进 recorder） | 22 | 消息路径，不是注册 |
| `FirstRegister.initial_register` | 1 次链路 | 框架自身 |
| `ProactiveMiPushRegistrar` 新 app | **0**（`no new apps`） | 排除主动扫全机 |

#### 真正走到 register job 的包（全部）

| 包名 | 行为 |
|---|---|
| `com.xiaomi.xmsf` | 框架 `create new client info`（regId 空） |
| `com.sdu.didi.psnger` | `reuse existing` |
| `com.tencent.wework` | `reuse existing` |
| `com.miui.mediaeditor` | `reuse existing` |
| `com.taobao.idlefish` | `reuse existing` |
| `com.ct.client` | `reuse existing` |

合计：**1 个框架 + 5 个业务 app**，业务侧均为 reuse，不是「列表里几百个 app 全量注册」。

#### 用户侧可能的误解来源

1. Log 里大量 `getRegSecs` / `registerApplication existing list` / Aspect 多层 dump，关键词带 register，看起来像批量。
2. 管理器事件流若不过滤 `SEND_MESSAGE` / regSec 查询，也会刷屏。
3. 重装 xmsf 后 `regIdPresent=false` 会强制框架重新向服务器注册一次（正常）。

#### 与 manager 包名拆分的关系

- **不是**「manager 换包名把 DB 迁走导致全量重注册」。
- 本窗口未见 manager Apps 列表 `loadApplications` 拉爆 DB 的特征；主动注册扫描明确 `no new apps`。
- 若 UI 应用列表仍空，应另查 Binder 读失败 / 曾卸载清库，而不是本波「注册 log 噪声」。

#### 图标弹回补充说明

「只退出不拉起」仅是 §14.2–14.3 无线抓 log 的**临时诊断**；§14.4 已恢复自动弹回设置（`WRITE_OP_RELAUNCH_MANAGER`）。状态栏彩色仍为**整机 reboot**。

### 14.6 通知默认变彩色 — log + 代码结论（2026-07-23）

#### log 事实

SystemUI / IslandPreferences 持续刷新为：

`colorStatusBarIcon=false colorStatusBarIconGlobal=true`

（runtime 与 mipush tail 中唯一组合；代码默认均为 `false`，**强力模式 global=true 是设备上已持久化的 runtime 偏好**，不是本次安装把「彩色开关」默认打开。）

含义对照：

| 键 | 当前值 | 产品语义 |
|---|---|---|
| `pref_color_status_bar_icon` | false | **关闭彩色** → 期望 MiPush 管理通知 **单色** |
| `pref_color_status_bar_icon_global` | true | 强力模式：全状态栏图标跟随同一策略（此配置下应强制单色） |

#### 根因（代码，非猜）

1. **主发通知路径未遵守开关**  
   `NotificationController.notify()` 一直调用 `processIcon()`（会把 app 彩色图标 / brand color 写进 smallIcon），**从未**调用已实现的 `applyStatusBarIcon(colorStatusBarIcon)`。  
   `applyStatusBarIcon(false)` 才会设 `ic_notifications_black_24dp` + `COLOR_DEFAULT`，此前只在单元测试里用到。

2. **color=false 时的依赖链脆弱**  
   `SystemNotificationManager.injectAppIcons` 在 `colorStatusBarIcon=false` 时「保留 original smallIcon，指望 MIUI 默认单色路径」。但 original 已被 `processIcon` 换成 **彩色 TYPE_BITMAP**，MIUI 往往不再压成单色 → 用户看到「默认彩色」。

3. **global=true 仍可能见彩**  
   SystemUI `shouldForceGlobalMonochrome` 对部分 `canColorize`/系统应用有豁免；且强力模式改完需**整机 reboot** 才完整生效。若未 reboot 或仅看通知栏大图标/抽屉 app 图标，仍可能感觉彩色。

4. **管理器 UI 读本地 DataStore**  
   彩色键是 **RUNTIME 所有**；manager 开关展示来自 manager 包 DataStore，与 xmsf 真值可能短暂不一致（写时会 `set_runtime_boolean` 同步）。本次 log 侧真值已是 monochrome 期望，不是 UI 误显成「开了彩色」。

#### 修复

- 将 `notify` / mock receipt / mock channel 构建改为 `applyStatusBarIcon(..., colorStatusBarIcon)`。
- 单测：`disabled color…` 已覆盖；real/mock monochrome publish 改为断言 `TYPE_RESOURCE` + `COLOR_DEFAULT`。

#### 用户侧预期

- 开关「MiPush 管理的通知」**关** → 新通知 smallIcon 单色资源。  
- 若仍有旧通知彩色：清掉旧通知或重推；global 策略变更需整机重启。

### 14.7 单色应保留应用图标，不是 xmsf 铃铛（2026-07-23）

用户反馈：`0.6.3-20260723_115451` 安装后状态栏/通知图标变成 **xmsf 通用铃铛**，而不是具体 app 图标。

原因：§14.6 把 monochrome 接到 `applyStatusBarIcon(false)` 后，该分支错误地写死 `R.drawable.ic_notifications_black_24dp`（铃铛）。  
产品语义应是：

| 模式 | smallIcon | color |
|---|---|---|
| 彩色开 | `processIcon` → **目标 app 图标** | brand color |
| 彩色关（单色） | 仍 `processIcon` → **目标 app 图标形状** | `COLOR_DEFAULT`，由 SystemUI/MIUI 做单色 tint |

修复：`applyStatusBarIcon` monochrome 分支改为 `processIcon` + `setColor(COLOR_DEFAULT)`，不再强制铃铛。

### 14.8 重启后仍彩色 — 原因与修复（2026-07-23）

#### 远端 log 状态

手机重启后 **无线 adb 未恢复**（`connect …:5555` → Connection refused；HP/Mac 链均无 device），无法再拉 post-reboot runtime JSONL。  
分析依据：**重启前已验证的 IslandPreferences**（`colorStatusBarIcon=false`）+ **代码/提交 diff**。

#### 根因（代码，非猜）

历史强单色路径（`1e979b8d`）在 `processSmallIconColor` 于 **彩色关** 时执行：

`contentView.setInt(android.R.id.icon, "setOriginalIconColor", 0)` + `result=true`

`6c316700`「让 MIUI 原生处理」把 OFF 分支改成 **直接 return**。  
对 `processIcon` 产出的 **TYPE_BITMAP 应用图标**，MIUI 原生 **不会** 压成单色 → 重启后仍彩色。

§14.6/14.7 只改了 xmsf 发通知侧（icon 形状 / COLOR_DEFAULT），**没有**恢复 SystemUI 强制单色，故 reboot 后现象不变。

#### 修复（本轮）

1. `HookSystemUI.processSmallIconColor`：彩色关且（MiPush 管理 **或** 强力模式）→ `setOriginalIconColor(0)`  
2. `IconManager.setIcon` tint：彩色关时对 **MiPush 管理通知** 也套 monochrome tint（不依赖强力模式）  
3. 策略 helper：`shouldForceMonochromeProcessSmallIcon` / `shouldApplyMonochromeTintToNotification`  
4. 单测已按新语义更新并通过  

生效包：需重装 **mipush**（内嵌 xposed SystemUI hook）；xmsf 无强制依赖本轮 hook 变更，但可一并装。

### 14.9 岛划掉仍在 + 点开显示「推送服务」（2026-07-23）

#### 证据（设备 log / dumpsys，非猜）

粘性岛通知（支付宝物流）：

- key=`0|com.xiaomi.xmsf|-316406143|mipush_com.eg.android.AlipayGphone|10209`
- `flags=ONGOING_EVENT|PROMOTED_ONGOING`，`xmsf.live_update=true`，`android.requestPromotedOngoing=true`
- `focus payload plan … reason=native_live_update`，`allowIslandProxy=false`（不走 `trackedForCancel` / miui_island_proxy）
- `deleteIntent=null`（旧包）；`android.appInfo=com.xiaomi.xmsf`

点开岛显示「推送服务」的根因链：

1. 14:23:15 发布：`NotificationIdentityBridge.notifyAsTargetPackage` **FRAMEWORK** 失败  
   `SecurityException: uid … does not have android.permission.UPDATE_APP_OPS_STATS`
2. 同时 `isHooked=false`（HookPushNC 未接管 xmsf 进程，无法走 `SystemNotificationManager.enqueue(pkg=target, opPkg=xmsf)`）
3. 回落 `notifyLocally` → `pkg=com.xiaomi.xmsf` → SystemUI 用 xmsf 的 `app_name`=**推送服务**
4. 普通非 Live Update 通知同一设备上 `notifyAsTargetPackage success` → `pkg=支付宝`，故只有 ProgressStyle/promoted 路径踩 AppOps

划掉通知栏岛仍在：

- 非 HyperIsland proxy 未 cancel；同一条 native Live Update 在 shade dismiss 后仍可能以 PROMOTED 形态留在岛上
- 旧路径 `onNotificationRemoved` 只 cancel **proxyId**，native_live_update 从不 `trackedForCancel`

#### 修复（`0.6.3-20260723_145148`）

1. **Root silent grant** 增加 `pm grant … UPDATE_APP_OPS_STATS` + `POST_PROMOTED_NOTIFICATIONS`（`PermissionUtils.grantSilentPermissions`）
2. Live Update 身份失败时 **grant 后重试一次** `notifyAsTargetPackage`（`NotificationManagerEx.maybeRetryNotifyAsTargetAfterAppOpsGrant`）
3. 回落本地发布时写入 `android.substName` / target extras，尽量避免显示「推送服务」
4. **同步 cancel**：
   - 发布时挂 `LiveUpdateDismissReceiver` deleteIntent → `NotificationManagerEx.cancel` + 本地 cancel
   - SystemUI `MiPushIslandHook.onNotificationRemoved`：对 `xmsf.live_update` / promoted 调用 `NotificationListenerService.cancelNotification(key)`

#### 安装验证

- xmsf/mipush versionName=`0.6.3-20260723_145148`，`UPDATE_APP_OPS_STATS: granted=true`（user 0）
- 需新的物流/Live Update 推送验证：`pkg` 应为目标 app（或至少 substName=支付宝），划掉 shade 后岛应同步消失
- `isHooked=false` 仍可能存在（LSPosed 是否注入 xmsf）；本轮不依赖它也能靠 AppOps grant 修 Live Update 身份

### 15.0 LSPosed「整挂」与单色失效 — 证据结论（2026-07-23）

用户反馈：重装/重启后单色仍失败，并怀疑 **mipush 导致整个 LSPosed 挂掉**。  
本轮在 HP 有线 adb（`pudding`）上取证，**结论以 log/配置为准，不是猜测**。

#### 15.0.1 现象拆分

| 表象 | 设备证据 | 结论 |
|---|---|---|
| LSPosed「整挂」/模块像失效 | 17:01 启动后 modules log 到 **17:10:39 后停止增长**；`killall SystemUI` / `force-stop xmsf` **都不再出现** `onModuleLoaded` | **新进程注入失效**，不是 manager UI 单独卡死 |
| 寄生 Manager 打不开 | `am start … LAUNCH_MANAGER` → unable to resolve；`org.lsposed.manager` 包不存在 | 寄生入口未解析（框架异常时常见）；`lspd` 进程本身仍在 |
| 单色仍彩色 | 17:01 会话 modules log **零条** `com.android.systemui` / `HookSystemUI` | SystemUI **从未装上** monochrome hook |
| 与 mipush 的相关性 | mipush scope 含 `android`/`systemui`/`xmsf`；但 **不是** Java 层 FATAL 拖垮 lspd | 更像 **ZygiskNext 注入链路在会话中期崩坏**；时间上与 17:10 附近 zygote 重注相关 |

#### 15.0.2 崩溃时间线（17:01 boot → 17:10 注入失效）

1. **17:01:23** `lspd` 正常启动；system 侧只有 HMA/Thanox 等；**boot 时 modules_state 里 mipush 曾缺失 enabled 行**（仅 `modules`+`scope` 有记录，`enabled` 无行）。
2. **17:10:09** `com.xiaomi.xmsf` / `:services` 首次 `onModuleLoaded` mipush，`HookPushNC isHooked=true`（说明当时注入仍可用）。
3. **~17:10:54**（uptime ~572s）dmesg：`zn-daemon` **再次** inject zygote，加载 `zygisk_lsposed` + `mipush_zygisk`，新 `zn-daemon` pid 与新 `zygote64` 出现；同时保留旧 `zn-daemon` → **双 daemon**。
4. 此后 verbose 大量：`zn-zygisk-loader64 connect daemon failed with 111` / `GetLinker failed` / `ReadModules failed`。
5. **17:10:39 之后** modules log 文件大小冻结；后续 SystemUI 重启、xmsf force-stop 重拉起均 **无** 新 hook 日志 → **LSPosed 对新进程实质失效**。
6. dropbox 同期 `system_app_crash` 是相机 RxJava，`data_app_crash` 是 `com.miui.securitymanager` NPE，**不是** mipush/lspd 堆栈；`SYSTEM_RESTART` 仅为重启记录。

#### 15.0.3 配置侧问题（加重「像挂了」）

| 项 | 证据 | 影响 |
|---|---|---|
| `modules_state` 缺 mipush（曾） | sqlite：`enabled=-1`（无行）；同批还有 smscode/xinyi/disableflagsecure | Manager 列表/启用态异常；boot 早期可能不注入 system/SystemUI |
| `modules.apk_path` 过期 | config 旧路径 `~~Y_oRLD…`，实际 `pm path` 为 `~~NBT5ZD…` | 重装后 LSPosed 解析模块 APK 失败风险；artd 报 no usable artifacts |
| 偏好其实正确 | `content://…island.prefs`：`pref_color_status_bar_icon=0`，`pref_color_status_bar_icon_global=1` | **不是偏好写错**；缺的是 SystemUI hook |
| 早期 IslandPreferences 读失败 | system_server 启动瞬间 AMS null → refresh fail；稍后重试成功 | 解释 boot 日志里短暂 `colorStatusBarIconGlobal=false` |

本轮已在设备侧修复：`modules_state(mipush,enabled=1)` + 更新 `apk_path` 为当前路径（备份 `modules_config.db.bak.fix`）。

#### 15.0.4 根因结论（对用户问题的直接回答）

1. **「LSP 整挂」**：不是 `lspd` 进程死掉；是 **ZygiskNext 在会话中期 zygote 重注后 IPC 失效（双 daemon + connect 111）**，导致 **新进程装不上任何 Xposed 模块**。体感 = 整个 LSPosed 挂了。
2. **「是不是 mipush 弄挂的」**：
   - **直接 Java 崩溃拖死 lspd：否**（无 mipush FATAL 对应 lspd/zygote tombstone）。
   - **相关因素**：`mipush_zygisk` 与 `zygisk_lsposed` 同被加载；`modules_info` 同时列出二者；模块重装导致 path/state 脏；**时间线与 17:10 注入失效重合**。
   - **更准确表述**：故障在 **ZygiskNext 注入层**；mipush（含 zygisk 子模块）是同场模块，**不能单凭现有 log 判 mipush_zygisk 为唯一凶手**，但重装/更新 mipush 会触发 LSPosed 模块路径刷新与进程重拉，**容易暴露/触发** 这条注入链路问题。
3. **单色仍彩色**：在注入失效期间 **SystemUI 无 `HookSystemUI` / `processSmallIconColor`**，BITMAP 应用图标保持彩色；xmsf 侧仍会打 `Prepared notification for default MIUI monochrome target path`，**只改通知侧不够**。

#### 15.0.5 恢复动作与验证（已做）

1. 修复 LSPosed `modules_config`（enable + apk_path）。
2. **整机 reboot**（仅 kill SystemUI **无法** 恢复已死的 zn 注入）。
3. 重启后验证（17:42 boot）：
   - 单 `zn-daemon` + `zygote64` + `lspd` 正常；
   - `onModuleLoaded`：**system / SystemUI / xmsf / securitycore** 均有 mipush；
   - `HookSystemUI.hook()`、`processSmallIconColor hook installed successfully`、`NotifImageUtil.getSmallIcon` 已装；
   - SystemUI `IslandPreferences`：`colorStatusBarIcon=false colorStatusBarIconGlobal=true`（与 provider 一致）。
4. 无线：`tcpip 5555` 已开；wlan0 当前网段见设备侧（会话内已记录到 `/tmp/mipush_wireless_serial.txt` 意图；若 USB 仍在线可优先进 USB）。

#### 15.0.6 后续建议

- 单色请用 **新通知** 再观察状态栏；若仍彩，再抓 `processSmallIconColor` / `setOriginalIconColor` 运行时命中（hook 已在，属第二层兼容问题）。
- 若再次出现 modules log 停写 + `connect daemon failed 111`：**优先 reboot**，不要只杀 SystemUI。
- 可观察是否在仅启用 `zygisk_lsposed`、临时禁用 `mipush_zygisk` 时更稳（A/B，需用户确认后再动 Magisk 模块）。
- 版本仍为设备上的 `0.6.3-20260723_145148`；树内已有 0.7.0/beta 合入记录，**尚未**在本轮重装 0.7.0 产物。
- GitLab 为主、**不推送** 策略不变。

### 15.1 设备安装 0.7.0（2026-07-23）

- 构建：`./gradlew :app:assembleNormalSplitDebug :mipush:assembleDebug -PbuildSplits=true -PbuildTs=20260723_174818`
- 产物：`arm64-v8a_normalSplit_xmsf_v0.7.0-20260723_174818_debug.apk` + `arm64-v8a_MiPush_v0.7.0-20260723_174818_debug.apk`
- 安装：HP 有线 adb，xmsf + mipush 均 `versionName=0.7.0-20260723_174818`（lastUpdate ~17:49）
- 安装后刷新 LSPosed `modules.apk_path` 为新 `pm path`，并 `killall com.android.systemui` 使 SystemUI 重新 `HookSystemUI`/`processSmallIconColor`
- 偏好仍为 monochrome 意图：`pref_color_status_bar_icon=0`，`pref_color_status_bar_icon_global=1`

### 15.2 调试信息格式化修复 + 安装（2026-07-23）

- 根因：独立 manager 的 `RemoteManagerEventGateway.getJson()` 恒 `null`，UI 回落到 `key=value` 文本
- 修复：common `EventDebugJson` pretty JSON；remote/xmsf fallback；对话框等宽+滚动+**换行/不换行**切换
- 安装：`0.7.0-20260723_180646` arm64 normalSplit xmsf + mipush（HP USB）

### 15.3 拆包后同类缺口清单（证据：`RemoteManagerGateways` / write 协议，非猜）

模式与 `getJson=null` 同类：**接口仍在 UI 中可达，但 remote 实现是 silent no-op / empty / 假失败**，用户体感「功能坏了/回退了」。

| 优先级 | 能力 | remote 现状 | 用户可见影响 | 建议方向 |
|---|---|---|---|---|
| P0 已修 | 事件调试 JSON | 曾恒 null | 调试信息未格式化 | `EventDebugJson`（已装） |
| P0 | 日历清理计数 `countEventsByDay` | **恒 `emptyList()`** | 清理日历无高亮/计数（清理 write 可能仍可用） | 新增 read capability 或本地聚合可清理事件 |
| P0 | 配置同步 `ManagerConfigSyncGateway` | load/pull/import/save/reset **空实现** | 配置页像空仓库、无法同步 | 配置树 manager 本地化 **或** Binder 配置 catalog+内容读写 |
| P0 | Zygisk 配置网关 | enabled=false、save=false、forceStop no-op | 应用页进 Zygisk 配置无效 | root/path 经 write 到 xmsf 或 manager 直接读 `/data/adb/...` |
| P1 | 清空运行日志 `clearLogFolders` | 固定 `success=false, clear_logs_unsupported_on_remote_host` | 设置里清日志失败 | 扩展 log capability 到 clear；或 manager 清自己的 + 命令 xmsf 清 runtime |
| P1 | 删除通知渠道 `deleteNotificationChannel` | **no-op**（注释 phase-4 未做） | 应用详情删渠道无效果 | 加 `WRITE_OP_DELETE_NOTIFICATION_CHANNEL` |
| P1 | `startConfigPreview(packageName)` | **no-op** | 从事件/应用「打开配置」无跳转 | manager 本地导航到 ConfigurationsPage |
| P1 | `getContent` | 仅 `event.content` 摘要 | 与旧版「配置处理后的 content JSON」不一致 | 同 getJson：payload 本地展开或 runtime getContent RPC |
| P2 | `repairXSpaceUserSupport` | 直接 `ROOT_MISSING` | 双开修复入口假失败 | 走已有 dual-app/root write 路径 |
| P2 | `observeNotificationEvent` / `resetTopActivityCache` | Unit | 诊断/观测静默丢失 | 低优先；可去掉 UI 依赖或写 telemetry |
| P2 | LSPosed `modules.apk_path` | 重装后易过期 | 模块像未启用/单色 hook 不加载 | 安装脚本刷新 path；或 manager 检测并提示重启 |
| P2 | `getJson` 完整 thrift 解密 | client 侧无 ConvertUtils 解密链 | 加密 pushAction 细节不如旧同进程 | 可选：runtime 返回 pretty JSON（注意 Binder 大小） |

#### 已较完整（对照）

- 应用列表/详情/诊断、事件列表、mock 重放、删/恢复事件、渠道读、连接快照、部分 root 静默授权、双开 set/query、日志导出、多数 write_commands

#### 推荐修复顺序

1. **P0 日历计数 + 配置同步/Zygisk**（高频用户路径，silent empty 最伤）
2. **P1 清日志 / 删渠道 / startConfigPreview 本地导航**（小改动高收益）
3. **安装后 LSPosed path 自愈**（减少「装完又挂」）
4. getContent/完整 decrypt JSON 按需增强

### 15.4 全部修：remote 缺口落地（2026-07-23）

目标：消除拆包后与 `getJson=null` 同类的 silent no-op / empty stub（§15.3）。

#### 协议 / 写路径

`ManagerProtocol` MINOR **5 → 6**，新增 write ops：

| Op | 用途 |
|---|---|
| `count_events_by_day` | 日历清理按日计数（details 行格式 `yyyy-MM-dd:count`） |
| `clear_log_folders` | 运行时清日志目录 |
| `delete_notification_channel` | 删目标应用通知渠道 |
| `zygisk_is_enabled` / `zygisk_get_config` / `zygisk_save_config` / `zygisk_force_stop` | Zygisk 配置读写与强停 |
| `repair_xspace` | 双开/XSpace 修复（复用 xmsf permission gateway） |
| `reset_top_activity_cache` | 顶层 Activity 缓存重置 |
| `get_event_content` | 事件配置处理后 content（runtime 按 id 回源 payload） |

执行端：`ManagerWriteRuntimeExecutor` 注入 log / notification / zygisk gateway。

#### Manager remote 接线

- `RemoteManagerEventGateway.countEventsByDay` / `getContent` / `startConfigPreview`
- `RemoteManagerNotificationGateway.deleteNotificationChannel`
- `RemoteManagerLogGateway.clearLogFolders`（manager 本地 dir + runtime clear）
- `RemoteManagerPermissionGateway.repairXSpaceUserSupport`
- `RemoteManagerRuntimeActions.resetTopActivityCache`
- `RemoteZygiskConfigGateway`（经 write 到 xmsf root 路径）

#### 配置同步（manager 本地）

- 移植 config 栈到 `manager/.../configuration/sync/*`
- `LocalManagerConfigSyncGateway`：SAF 树 + 远程 catalog 同步
- `saveLocal` / `resetToRemote` / `loadConfigurations` 后通过 `uploadConfiguration` PFD 激活 runtime
- `openForPackage` / `startConfigPreview` → 本地 `config_editor` / `configs_search` 路由

#### 构建

- `0.7.0-20260723_182122`（含 remote 缺口修复后重编）
- xmsf：`arm64-v8a_normalSplit_xmsf_v0.7.0-20260723_182122_debug.apk`
- mipush：实装 `universal_MiPush_v0.7.0-20260723_182122_debug.apk`（arm64 分包 scp 损坏后回退 universal）

#### 安装
- **2026-07-23 18:34 已安装**（HP 无线 adb）
  - xmsf：`versionName=0.7.0-20260723_182122`，`lastUpdateTime=2026-07-23 18:33:00`
  - mipush：`versionName=0.7.0-20260723_182122`，`lastUpdateTime=2026-07-23 18:34:46`
  - 中间一度 `INSTALL_PARSE_FAILED_NOT_APK`（arm64 分包 scp 损坏），改 universal 校验重装后成功
  - 建议刷新 LSPosed `modules.apk_path` 到当前 `pm path io.github.magisk317.mipush`；必要时 kill SystemUI

#### 仍保留 / 低优先

- `observeNotificationEvent`：runtime 侧观测，manager UI 无强依赖，仍 no-op
- LSPosed path 自愈：未做成安装自动步骤，重装后仍建议手动刷新
- 完整 thrift decrypt 细节仍受 write `details` 4KB 上限约束；调试 JSON 继续走 `EventDebugJson`

#### 用户侧复测

1. 日历清理页：有事件日后应出现计数高亮
2. 配置页：选目录后可 pull/import/save；save 后 runtime 生效
3. Zygisk 页：能读 `app.conf`、切换包后保存、force-stop
4. 设置 → 清空日志：不再固定 `clear_logs_unsupported_on_remote_host`
5. 应用详情 → 删通知渠道：应真正删除
6. 事件详情 → 调试信息 / content：非空且可格式化
7. 双开修复入口：root 可用时不再恒 `ROOT_MISSING`

## 15.x 偶发首页/应用列表/记录页全空（2026-07-24 09:38 / 10:34 复现）

### 现象
- 独立 manager（`io.github.magisk317.mipush`）偶发：首页统计空白、应用列表空白、记录页空白
- 进程未死；过一段时间或强刷后又好；非必现

### 现场证据（HP，manager pid=30603 / xmsf pid=26432）
- 10:31 manager 冷启动后 `IManagerRuntimeService` Binder 调用偏慢（code=3/6 约 300–500ms）
- 同期 xmsf 侧大量 `getNotificationChannelsForPackage` `NoSuchMethodException`（拉应用页时放大耗时）
- 10:34 UI 仍“全空”，runtime / manager 进程均在；`BIND_MANAGER_RUNTIME` 服务仍注册
- 既有 runtime JSONL **几乎没有** ManagerRuntime 读写轨迹 → 排障困难（本次已补日志）

### 根因（代码 + 日志，高置信）
拆包后 manager UI 全部走 Binder 远程读；以下链路把瞬时失败“洗成成功空数据”，再缓存：

1. **`ManagerRuntimeClient` 并发许可**：`MAX_IN_FLIGHT_REMOTE_CALLS=2` + `tryAcquire()` 不等待  
   - 第 3 路并发立刻 `permitsExhausted`  
   - `callCapability` 仍 `releaseSession(... TimedOut)` 拆会话，且 **不** `scheduleReconnect`  
   - 后续读全部 Unavailable → 空白
2. **`RemoteManager*Gateway`**：`Unavailable` → `ManagerApplications()` / `emptyList()`（静默空成功）
3. **空缓存粘住**：`ApplicationListViewModel.listLoaded` / `EventListPage` snapshot 把空结果当合法缓存，tab 切回也不重拉
4. **Comparing* 二次远程对比**：primary 已是 remote gateway，再 `compareRemote` 双倍打 Binder，更容易打满 2 许可
5. 连接页 primary 曾走 `SettingsManager` 空壳路径（standalone 下无本地连接态）

### 修复（本轮）
- Client：许可等待（超时内 `acquire`）、忙时 **不拆会话**；超时 3s→8s；并发 2→6；tag=`ManagerRuntime` 日志
- Gateway：`Unavailable` 抛 `RuntimeReadUnavailableException`，禁止洗成空成功
- ViewModel/UI：失败不缓存；保留上次成功数据；Available 恢复后自动重拉；Event 用 `runtimeReadySignal`
- Comparing：standalone 关闭二次 remote compare；连接 primary 改为 `RemoteConnectionSnapshotSource`
- Runtime Service：handshake / getApplicationPage / getEventPage / getConnectionSnapshot 打耗时与条数日志

### 验证建议
1. 冷启动 manager，快速切换 首页/应用/记录
2. logcat 过滤 `ManagerRuntime`：应见 handshake / get* / availability 变迁；不应再因 busy 拆会话
3. 人为 kill `com.xiaomi.xmsf` 后恢复：页应自动回填，而不是永久空白

## 15.y 日志保留天数：覆盖全部产物（2026-07-24）

### 问题
- 设置「日志保留天数」默认 2，但现场仍见 `files/xmsf_logs/` 下 6 月/7 月的 `mipush_logs_*.zip` 与 `.tmp_mipush_logs_*`。
- 根因：旧实现只对 `files/log/runtime*.jsonl` 做按天裁剪；导出 zip、staging、crash、legacy cache、MiPush SDK 日志**不在**保留窗口内；仅「清空日志」会整目录删除。

### 整改
统一入口 `LogUtils.pruneAllLogArtifacts(context, now, force)`：
| 产物 | 路径 | 判定 |
|------|------|------|
| Runtime JSONL | `files/log/`（含 `modules/`） | 文件名日期 / mtime |
| Crash | `files/crash/Crash_yyyy-MM-dd.txt` | 文件名日期 / mtime |
| 导出 zip | `files/xmsf_logs/mipush_logs_*.zip` | 文件名日期 / mtime |
| Staging | `files/xmsf_logs/.tmp_mipush_logs_*` | 文件名日期 / mtime |
| Legacy cache | `cache/logs` | mtime 树扫 |
| MiPush SDK | `externalFiles/MiPushLog` | mtime 树扫 |
| 缓存临时 zip | `cache/runtime-log-*.zip` 等 | mtime |
| Manager 本地诊断 dir | manager 进程 `files/{log,crash,private_export}` + cache | 改保留天数时 best-effort mtime 清理 |

触发点：
- `LogUtils.init`（force）
- `setRetentionDays`（force，runtime write + manager 本地）
- `LogBundleExporter.buildLogBundle` 导出前（force）
- `appendRuntimeLog`：完整裁剪 **30 分钟节流**；节流窗口内仅裁 runtime jsonl

其它：
- xmsf 文案「默认 7」→「默认 2」，与 `DEFAULT_RETENTION_DAYS=2` / manager 一致
- manager 文案明确「全部日志产物」
- 单测：`retention deletes expired export zip crash staging and legacy artifacts`

### 与「日志导不出」关系（同轮）
- 导出 AIDL 超时已单独拉到 180s，并去掉 Settings 侧 `compareRemote` 二次全量导出；本条是**保留/清盘**范围修复，不替代导出超时修复。

### 验证建议
1. 安装 xmsf+mipush 后冷启动（或改一次保留天数）
2. `run-as com.xiaomi.xmsf ls -la files/xmsf_logs`：超过窗口的 zip/tmp 应消失
3. 导出日志仍应成功；新 zip 当日保留

### 设备验证（HP，2026-07-24）
- 安装：`0.7.0-20260724_113304`（xmsf normalSplit）+ 已装 manager `0.7.0-20260724_112931`
- 现场：`files/xmsf_logs` 中 **2026-07-02 的 `.tmp_*` 与 6 月旧 zip 已不在**；仅余当日 zip + 当日新 staging（导出残留，30min 陈旧阈值会清）
- 单测：`LogUtilsRobolectricTest` 14/14 通过（含全产物保留用例）

## 15.z 状态栏三图标与单色仍彩色（2026-07-24）

### 设备快照（HP 有线 ADB）
- 偏好：`pref_color_status_bar_icon=0`、`pref_color_status_bar_icon_global=1`
- SystemUI `IslandPreferences` 与 provider 一致：`colorStatusBarIcon=false colorStatusBarIconGlobal=true`
- 用户可见三图标（均 MiPush 托管）：

| 应用 | pkg | opPkg | smallIcon | color |
|------|-----|-------|-----------|-------|
| 抖音 | `com.ss.android.ugc.aweme` | `com.xiaomi.xmsf` | BITMAP 72×72 | `0x00000000` |
| IT之家 | `com.ruanmei.ithome` | `com.xiaomi.xmsf` | BITMAP 50×50 | `0x00000000` |
| 支付宝 | `com.eg.android.AlipayGphone` | `com.xiaomi.xmsf` | BITMAP 72×72 | `0x00000000` |

三条均带 `target_package` / `miui.targetPkg` / `xmsf_target_package`，**不是**微信那种原生 `RESOURCE` 路径。

### 配置 `icon/` 是否有用
- SAF 树：`Documents/mipush_config`，`icon/NotifyIconsSupportConfig.json` 对上述三包均有条目：`isEnabled=true`、`isEnabledAll=false`。
- `processIcon`：`isEnabledAll` 不全开时，资源失败后仍可用 `isEnabled + iconBitmap` 作 smallIcon。
- IT之家 **50×50** 与配置 PNG 尺寸一致 → 配置图在发帖侧**确实生效**；配置图用于品牌 smallIcon，**不是** SystemUI 单色引擎。

### 根因（代码，非强制转灰）
1. 发帖侧 monochrome 仍 `processIcon` → 配置/缓存 **TYPE_BITMAP** + `COLOR_DEFAULT`（形状保留，像素仍是品牌图）。
2. SystemUI 强单色依赖 `IconManager` / `StatusBarIconView` 的 `SRC_IN` tint。
3. **`globalMonochromeTint(requested, fallback)` 旧逻辑：`requestedColor != 0` 原样返回**。  
   品牌色（如支付宝配置 `#ff1678ff`、系统/图标路径留下的非灰 tint）一旦非 0，会作为“单色 tint”涂回 ImageView → BITMAP **保持全彩观感**。  
   这与“不要 BITMAP 强制灰度”不冲突：修的是 **tint 选择**，不是改像素。

### 修复
- `SystemUiNotificationPolicy.globalMonochromeTint`：仅保留灰度/白黑系统 tint；通道不平衡的品牌 RGB 一律回退 `DEFAULT_STATUS_BAR_ICON_TINT`（白）。
- 新增 `isGrayscaleTintColor`；单测更新 `FocusNotificationPermissionPolicyTest`。
- **未**做 BITMAP→灰度像素转换；**未**改 getSmallIcon 拦截矩阵（与现有 strong monochrome 测试一致）。

### 验证
1. 单元：`FocusNotificationPermissionPolicyTest` / monochrome tint 断言
2. 安装 xmsf（含 xposed 模块）后 **整机 reboot**（global 策略）
3. 新推抖音/IT之家/支付宝：状态栏小图标应为白/灰 tint，而非品牌原色
