# Vendor 边界审计报告

> 生成日期：2026-08-22
> 更新日期：2026-08-31
> 更新日期：2026-09-12（D1 扫描覆盖扩展）
> 对应文档：`docs/architecture/vendor-xmsf-boundary-compliance.md`
> 退出条件：每条保留的 vendor policy 都有 stock 来源、影响说明和验证状态

## 审计范围

`vendor/src/main/java/` 中所有 `io.github.magisk317.` 引用（当前共 82 条：17 条 import
与 65 条行内全限定引用/typealias 表/文档注释行，由 `scripts/vendor_boundary_baseline.txt`
维护，必须保持无新增）。自 2026-09-12 起，扫描器对整行任意位置的 FQCN 生效（不再仅匹配
`^import`），行内 plan 委托与 typealias 桥接表对“拒绝新增”检查可见，详见下文 D1 章节。

## 分类汇总

### 类别 1：MagiskOtel 结构化遥测（10 条）

| 文件 | 行为 | 影响 |
| :--- | :--- | :--- |
| `MiPushClient.kt` | push.register 事件上报 | 替代 stock MiLog，输出结构化 JSONL |
| `MiTinyDataClient.kt` | tiny-data 事件上报 | 同上 |
| `PushMessageHandler.kt` | 消息处理事件 | 同上 |
| `PushServiceClient.kt` | 服务调用事件 | 同上 |
| `ClientEventDispatcher.kt` | 客户端事件分发 | 同上 |
| `MIPushAckDispatcher.kt` | ACK 应答事件 | 同上 |
| `MIPushEventProcessor.kt` | 事件处理流水线 | 同上 |
| `NotificationIdentityBridge.kt` | 通知身份桥接 | 同上 |
| `PacketSync.kt` | 包同步事件 | 同上 |
| `XMPushServicePacketDelegate.kt` | 包委托事件 | 同上 |

**Stock 来源**：原始代码使用 `com.xiaomi.channel.commonutils.logger.MyLog`（vendor 自带日志）。
**影响说明**：替换为 `MagiskOtel.event()` 结构化事件，支持 JSONL、Sink、Xposed 传输，不改变业务逻辑。
**验证状态**：`:xmsf:shell:testNormalDebugUnitTest` 中 runtime JSONL 输出验证和 `PushPacketRuntimeTest` reason code 验证。

### 类别 2：LoggerExtensions 结构化日志（5 条）

| 文件 | 行为 |
| :--- | :--- |
| `NotificationIdentityBridge.kt` | logD/logE/logI/logV/logW 用于通知通道管理日志 |

**Stock 来源**：原始代码使用 `MyLog`。
**影响说明**：替换为 Kermit 结构化日志 facade，支持脱敏和级别映射。
**验证状态**：`LogSanitizerTest`、`XposedLogClientSanitizationTest` 验证脱敏输出。

### 类别 3：DefaultLogSanitizer（1 条）

| 文件 | 行为 |
| :--- | :--- |
| `AssemblePushHelper.kt` | 推送 token 编解码时脱敏 |

**Stock 来源**：原始代码无脱敏（直接 log token）。
**影响说明**：增加 token/secret 脱敏，防止敏感信息泄漏到 logcat。
**验证状态**：`LogSanitizerTest`、`XposedLogClientSanitizationTest`。

### 类别 4：PushServiceBroadcastActions 常量契约（1 条）

| 文件 | 行为 |
| :--- | :--- |
| `XMPushServiceMessenger.kt` | 注册 GET_CONNECTION_STATUS / START_FOREGROUND 广播 action |

**Stock 来源**：原始代码使用 `PushConstants` 中硬编码字符串。
**影响说明**：共享契约常量，无行为变更。
**验证状态**：`XMPushServiceAppIntentDelegateContractTest`。

## 结论

所有 baseline 条目均有明确 stock 来源、影响说明和验证状态。无新增 vendor product-layer
import debt；baseline 不是允许新增 product 行为的豁免列表。

## D1 扫描覆盖扩展（2026-09-12）

> 对应审计发现：D1 —— vendor 边界扫描此前仅匹配 `^import io\.github\.magisk317\.`，
> 行内全限定引用与 typealias 桥接对“拒绝新增”检查不可见，导致边界扫描低报。

### 扫描器行为变更

`scripts/verify_module_boundaries.sh` vendor 检查的匹配模式由 `^import io\.github\.magisk317\.`
放宽为整行任意位置匹配 `io\.github\.magisk317\.`（扫描根 `vendor/src/main`，入库行去除前导
空白）。注释与字符串行保持在扫描范围内，与既有 baseline 的文档行处理一致。此次扩展一次性入账
65 条既有 sanctioned 引用（baseline 类别 5-7），此后同类新增将被拒绝。

### 新暴露类别（baseline 类别 5-7）

| 类别 | 文件 | 条数 | 惯用法 |
| :--- | :--- | :--- | :--- |
| 5 行内 FQCN plan 委托 | `smack/Connection.kt`、`slim/BlobReader.kt`、`slim/BlobWriter.kt`、`NetworkCheckup.kt`、`XMPushServiceIntentDelegate.kt` | 22 | 行内直调 runtime-core PlanFactory/Event/Command（plan-delegation/adapter 惯例） |
| 6 PushVersionInfo 行内 FQCN 委托 | `push/service/PushVersionInfo.kt` | 10 | 常量与方法薄委托至 runtime-core `PushVersionInfo` |
| 7 typealias 桥接表 | `push/service/PushRuntimeModels.kt` | 33 | 将 runtime-core 模型/工厂以 typealias 重导出到 stock 包名（含 1 行注释说明） |

### 反射缝（扫描器不可见，文档豁免）

`push/service/timers/Alarm.kt` 的 `createProductAlarm()` 通过
`Class.forName("com.xiaomi.push.service.timers.AlarmManagerTimer")` 加载产品侧定时器
（类文件位于 `xmsf/shell/src/main/java/com/xiaomi/push/service/timers/AlarmManagerTimer.kt`，
与 vendor 共享 `com.xiaomi` 包名空间），解析失败时回退 vendor 自带
`StockAlarmManagerTimer`。目标是 `com.xiaomi.*` 类名字符串，不在
`io.github.magisk317.` 扫描模式覆盖范围内，本扫描器有意不覆盖；作为 sanctioned
兼容缝记录于此（对齐 stock 7.4.67-C 选择 AlarmManager 后端的定时器身份语义）。

## 运行验证

```bash
./gradlew check --warning-mode=all --console=plain
./gradlew verifyModuleBoundaries :xmsf:shell:detekt :settings:detekt
./gradlew :vendor:testDebugUnitTest :xmsf:shell:testNormalDebugUnitTest
```

---

## 附录：`:xmsf:runtime:store` 生产数据库

> 新增日期：2026-08-22
> 对应文档：`docs/architecture/boundary-model.md` § runtime/storage boundaries

### 模块概述

`xmsf/runtime/store/` 是生产运行时存储的 Kotlin Multiplatform 模块。它维护 v9 schema，并使用 KMP `room.generateKotlin = true` 生成类型安全的 Kotlin 查询；xmsf 通过 Bundled SQLite driver 打开既有的 `db` 文件。

### 与 vendor 边界的关系

`:xmsf:runtime:store` **不引入新的 vendor 边界穿透**。它的数据来源于 xmsf 的运行时存储业务路径（通过 Koin 注入的 KMP DAO）。

所有 stock SDK ingress 仍通过 vendor → xmsf 路径处理。KMP 模块只负责存储实现，不改变现有 vendor 边界契约。

### 文件清单

| 文件 | 用途 |
| :--- | :--- |
| `commonMain/RuntimeStoreDatabase.kt` | KMP Room 数据库定义（v9 schema） |
| `commonMain/RuntimeStoreDaos.kt` | 类型安全 DAO 接口 |
| `commonMain/RuntimeStoreRows.kt` | 查询结果数据类 |
| `androidMain/RuntimeStoreDatabase.android.kt` | Android Bundled SQLite driver 与生产 `db` builder |
| `commonMain/RuntimeStoreMigrations.kt` | v1→v9 生产数据库迁移 |

### 验证状态

- `:xmsf:runtime:store:compileAndroidMain` — 已通过
- `:xmsf:runtime:compileDebugKotlin` — 已通过
- `:xmsf:runtime:testDebugUnitTest` — 已通过
- `:xmsf:shell:compileNormalDebugKotlin` — 已通过
- `RuntimeStoreMigrationContractTest` — 已通过

### 退出条件

1. KMP 模块通过 `:xmsf:runtime:store:compileAndroidMain` 编译
2. 生产 builder 使用 `db` 与 v9 migration registry
3. xmsf 业务路径只消费 KMP DAO，不再保留 Android Room 数据库壳
