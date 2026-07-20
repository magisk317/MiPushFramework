# 事件/记录「保留期限」共享抽取提案

> 状态：提案（未实施）。本次已在 MiPushFramework 本地落地完整实现，本文档记录后续三仓
> （MiPushFramework / XposedSmsCode / xinyi-relay）共享复用的方案与取舍，供评审后再动手。

## 背景

三个 app 都有「记录/事件列表 + 保留期限清理」的需求，但当前各自为政：

| 项目 | 保留语义 | 清理触发 | 现状 |
|---|---|---|---|
| **xinyi-relay** | 按**条数**（history limit，分 4 类） | 插入时同步 trim | 成熟，`RelayRecordRepository.trimOldRecordsIfNeeded` + `RetentionDialog` |
| **XposedSmsCode** | 按**天数**（日志） | 经 smscode-core 共享 | 用 `RuntimeLogStore` + `RuntimeDiagnosticsConfig` provider 注入范式 |
| **MiPushFramework** | 按**天数**（事件） | 启动 + 入库节流（本次新增） | 本次落地：`EventRetentionManager` + `search_text` 快照搜索 |

问题：三套「保留期限」交互与清理算法各写一遍，UI（设置图标 → sheet → 保留期限对话框）也各写一遍。

## 关键约束（调研已确认）

1. **MiPushFramework 不消费 smscode-core**。它的 `.gitmodules` 只有 `magisk-ui-kit` / `magisk-xposed-kit` / `build-logic`。smscode-core 是 SMS 生态核心库，让 MiPush 为了 retention 引入整个 smscode-core 不合理。
2. **smscode-core 是唯一已有成熟 retention 范式的库**：`RuntimeLogStore`（按天保留 + 节流 prune）+ `RuntimeDiagnosticsConfig`（`logRetentionDaysProvider` provider 注入，让共享代码持算法、各 app 持 DataStore）。
3. **magisk-ui-kit 有 "No Business Logic" 铁律**（见其 AGENTS.md）。只能放无状态展示组件，不能承载 DataStore / Room / 清理调度。
4. 三方**没有 maven 发布**，全是 git submodule + `include(project(...))` 编译期依赖。

## 本次在 MiPush 落地的结构（作为抽取蓝本）

已实现且编译 + 单测通过：

- **`EventRetentionManager`**（`xmsf/.../runtime/store/db/`）：`object`，持 `retentionDaysProvider: (() -> Int)?` + 节流状态（`PRUNE_INTERVAL_MS=6h` + `AtomicBoolean` 防重入）。`install(provider)` 由 app 层注入，`pruneNow()` / `maybePruneAfterInsert()` 两个入口。**这正是 smscode `RuntimeDiagnosticsConfig` 范式的翻版**。
- **清理算法**：`EventDb.deleteHistoryAsync(days)` → DAO `DELETE FROM EVENT WHERE type NOT IN (20,21) AND date < :cutoff`（保留注册状态事件）。
- **provider 注入**：`MiPushFrameworkApp.initEventRetention()` 从 `PreferenceRepository.eventRetentionDays` 缓存回传，启动清理一次；入库咽喉 `EventDb.insertEventAsync` 触发节流清理。
- **UI**：顶栏 `ic_tune_24dp` → `AppBottomSheet`（uikit 已封装 Miuix/Material 双风格）→ 保留期限项 → `TextInputDialog`（uikit 现成，带 validator）。与 xinyi 的 `Settings → ModalBottomSheet → RetentionDialog` 交互同构。

## 抽取方案（分层，按关注点）

**没有单一 kit 能同时容纳「UI + DataStore + 清理调度」**（因 MiPush 不吃 smscode-core，且 ui-kit 禁业务逻辑）。建议分三层落：

### 1. 清理算法 + provider 注入 → 新建中立轻量 kit `magisk-runtime-kit`

- 承载：`RetentionCleaner`（按天 cutoff 计算 + 节流 + `AtomicBoolean` 防重入）+ `RetentionConfig(daysProvider, cleanupAction, pruneIntervalMs)` 注入范式。
- **不含** Room / DataStore 具体实现——`cleanupAction: suspend (cutoffMillis: Long) -> Unit` 由各 app 注入自己的删除 SQL；`daysProvider: () -> Int` 由各 app 从自己的 DataStore 回传。
- 三方都能引（纯 JVM + 最小 Android 依赖），不强迫 MiPush 吃 smscode-core。
- 替代方案：放进 smscode-core 新增的纯净子模块（如 `smscode-core:retention`），但需 MiPush 新增该 submodule 依赖——**不如新建 runtime-kit 干净**。

### 2. UI 皮（无状态）→ 复用 magisk-ui-kit

- `AppBottomSheet` / `Item` / `StateSwitchItem` / `TextInputDialog` **已在 ui-kit**，本次 MiPush 已直接复用，无需新增。
- 若要进一步统一，可在 ui-kit 加一个纯 stateless 的 `RetentionDialog`（仿 xinyi 的单选 + 自定义输入组合），state hoisting 到各 app。这是唯一可考虑加进 ui-kit 的东西，且必须保持无业务逻辑。

### 3. DataStore 键 + 调度触发 → 留在各 app，不下沉

- 保留天数/条数的持久化是 app 专属配置（MiPush `PreferenceRepository`、xinyi `SettingsRepository`），通过 provider lambda 回传即可，符合现有 `logRetentionDaysProvider` 范式。
- 触发时机各异（xinyi 插入 trim、MiPush 启动+入库节流、smscode 定时），封装在各 app，不强求统一。

## 语义差异需先对齐（评审决策点）

xinyi 是**按条数**、MiPush/smscode 是**按天数**。抽取时 `RetentionCleaner` 应同时支持两种策略（`ByDays(cutoffMillis)` / `ByCount(limit)`），或先只抽「按天数」这条公共路径，xinyi 的按条数暂不并入。**建议先抽按天数**，落地 MiPush + XposedSmsCode 两个直接受益方，xinyi 的按条数作为第二阶段。

## 建议落地顺序

1. **先合入本次 MiPush 本地实现**（已编译 + 单测通过），验证范式在真机可用。
2. 抽 `magisk-runtime-kit` 的 `RetentionCleaner` + `RetentionConfig`，MiPush 先切过去（改动最小，行为不变）。
3. XposedSmsCode 的日志 retention 从 smscode-core 迁到（或桥接）runtime-kit，消除重复。
4. xinyi 视情况把按条数策略并入，或保留独立。
5. ui-kit 视需要补 stateless `RetentionDialog`。

## 关联记忆

见 `[[wqk-shared-extraction-deferred]]`、`[[logging-neutralization-five-repo]]`——共享抽取的落点权衡与五仓联动的既有经验。
