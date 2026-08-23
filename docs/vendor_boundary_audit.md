# Vendor 边界审计报告

> 生成日期：2026-08-22
> 对应文档：modernization_and_architecture_recommendations_refined.md § 阶段 3
> 退出条件：每条保留的 vendor policy 都有 stock 来源、影响说明和验证状态

## 审计范围

`vendor/src/main/java/` 中所有 `io.github.magisk317.*` import（共 20 条，含 4 个类别）。

## 分类汇总

### 类别 1：MagiskOtel 结构化遥测（13 条）

| 文件 | 行为 | 影响 |
| :--- | :--- | :--- |
| `MiPushClient.kt` | push.register 事件上报 | 替代 stock MiLog，输出结构化 JSONL |
| `MiTinyDataClient.kt` | tiny-data 事件上报 | 同上 |
| `PushMessageHandler.kt` | 消息处理事件 | 同上 |
| `PushServiceClient.kt` | 服务调用事件 | 同上 |
| `AssemblePushHelper.kt` | 推送组装事件 | 同上 |
| `ClientEventDispatcher.kt` | 客户端事件分发 | 同上 |
| `MIPushAckDispatcher.kt` | ACK 应答事件 | 同上 |
| `MIPushEventProcessor.kt` | 事件处理流水线 | 同上 |
| `NotificationIdentityBridge.kt` | 通知身份桥接 | 同上 |
| `PacketSync.kt` | 包同步事件 | 同上 |
| `XMPushServicePacketDelegate.kt` | 包委托事件 | 同上 |

**Stock 来源**：原始代码使用 `com.xiaomi.channel.commonutils.logger.MyLog`（vendor 自带日志）。
**影响说明**：替换为 `MagiskOtel.event()` 结构化事件，支持 JSONL、Sink、Xposed 传输，不改变业务逻辑。
**验证状态**：`:xmsf:testNormalDebugUnitTest` 中 runtime JSONL 输出验证 + `PushPacketRuntimeTest` reason code 验证。

### 类别 2：LoggerExtensions 结构化日志（5 条）

| 文件 | 行为 |
| :--- | :--- |
| `NotificationIdentityBridge.kt` | logD/logE/logI/logV/logW 用于通知通道管理日志 |

**Stock 来源**：原始代码使用 `MyLog`。
**影响说明**：替换为 Kermit 结构化日志 facade，支持脱敏和级别映射。
**验证状态**：`LogSanitizationContractRobolectricTest` 验证脱敏输出。

### 类别 3：DefaultLogSanitizer（1 条）

| 文件 | 行为 |
| :--- | :--- |
| `AssemblePushHelper.kt` | 推送 token 编解码时脱敏 |

**Stock 来源**：原始代码无脱敏（直接 log token）。
**影响说明**：增加 token/secret 脱敏，防止敏感信息泄漏到 logcat。
**验证状态**：`LogSanitizationContractRobolectricTest`。

### 类别 4：PushServiceBroadcastActions 常量契约（1 条）

| 文件 | 行为 |
| :--- | :--- |
| `XMPushServiceMessenger.kt` | 注册 GET_CONNECTION_STATUS / START_FOREGROUND 广播 action |

**Stock 来源**：原始代码使用 `PushConstants` 中硬编码字符串。
**影响说明**：共享契约常量，无行为变更。
**验证状态**：`ExportedStockServiceContractTest` + `XMPushServiceAppIntentDelegateContractTest`。

## 结论

全部 20 条例外均有明确 stock 来源、影响说明和验证状态。无新增 vendor product-layer import debt。

## 运行验证

```bash
./gradlew verifyModuleBoundaries :xmsf:detekt :settings:detekt
./gradlew :xmsf:testNormalDebugUnitTest
```

---

## 附录：runtime-store-kmp 并行数据库（Plan B）

> 新增日期：2026-08-22
> 对应文档：modernization_and_architecture_recommendations_refined.md § Plan B

### 模块概述

`runtime-store-kmp/` 是一个 Kotlin Multiplatform 模块，作为 Plan B 并行数据库运行。它提供与 `xmsf` 模块中 `AppDatabase` 相同的 schema（version 9），但使用 KMP `room.generateKotlin = true` 生成类型安全的 Kotlin 查询。

### 与 vendor 边界的关系

runtime-store-kmp **不引入新的 vendor 边界穿透**。它的数据来源于：
- `xmsf` 模块的 `EventRepository`（通过 Koin 注入）
- 自身的 `RuntimeArchiveRepository`（试点）

所有 stock SDK ingress 仍通过 vendor → xmsf 路径处理。KMP 模块仅作为并行存储层，不影响现有 vendor 边界契约。

### 文件清单

| 文件 | 用途 |
| :--- | :--- | 
| `commonMain/RuntimeStoreDatabase.kt` | KMP Room 数据库定义（v9 schema） |
| `commonMain/RuntimeStoreDaos.kt` | 类型安全 DAO 接口 |
| `commonMain/RuntimeStoreRows.kt` | 查询结果数据类 |
| `androidMain/RuntimeStoreDatabase.android.kt` | Android 平台驱动实现 |

### 验证状态

- `:xmsf:testNormalDebugUnitTest` — `AppDatabaseBackupRestoreTest` ✅ (5/5 PASSED)
- KMP 模块编译验证 — 待 `:runtime-store-kmp:compileKotlin` 确认

### 退出条件

1. KMP 模块通过 `:runtime-store-kmp:build` 编译
2. 并行运行期间数据一致性验证（xmsf Room vs KMP Room 输出对比）
3. 归档查询性能基准（KMP 归档查询 vs xmsf EventRepository 查询）
