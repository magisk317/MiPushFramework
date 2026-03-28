# 应用类型说明
应用列表中的“类型”用于说明当前应用与 Xiaomi Push 组件面的匹配方式。它主要帮助判断为什么某个应用容易注册、为什么某个应用经常强制注册失败。

## direct_sdk
- 这类应用自身带有官方 Xiaomi Push 的核心 service 或 handler。
- 一般包含 `com.xiaomi.push.service.XMPushService`、`XMJobService`、`PushMessageHandler` 之类组件。
- 这是最完整、最容易适配的一类。
- 如果要做强制注册、下行回放、注册链路回归，优先看这类应用。

## receiver_only
- 这类应用只保留了官方 receiver，缺少可直接分发注册消息的核心 service。
- 常见表现是能看到 `PingReceiver` 或 `PushServiceReceiver`，但没有 `XMPushService` / `PushMessageHandler`。
- 它们通常更依赖应用自身启动后在进程内完成初始化和注册。
- 因此从框架外部“硬塞一条强制注册”成功率会明显低于 `direct_sdk`。

## bridge_wrapper
- 这类应用通常没有直接集成官方 Xiaomi Push 主链，而是通过个推、友盟、厂商聚合 SDK 等桥接 Xiaomi 通道。
- 常见组件名会出现 `MiuiPushReceiver`、`com.igexin.*`、`HeytapPush`、`HmsMessageService` 等。
- 它们是否注册、如何刷新 token，往往取决于第三方桥 SDK 的内部逻辑，而不是官方 Xiaomi 组件。
- 所以这类应用即使出现在列表里，也不代表可以按 `direct_sdk` 的方式做强制注册。

## 怎么理解“已注册/未注册”和“类型”的关系
- “已注册/未注册”描述的是当前观察到的注册状态。
- “类型”描述的是应用本身的接入方式。
- 一个 `bridge_wrapper` 应用可以显示为“已注册”，但它并不一定支持官方 Xiaomi 直连式强制注册。
- 一个 `receiver_only` 应用也可能最终注册成功，只是更依赖应用自己启动后的初始化流程。

## 建议怎么排查
1. 先看类型。
2. `direct_sdk` 优先排查 appId/appKey、注册状态、下行与 regSec。
3. `receiver_only` 优先尝试启动应用、观察它是否自行发起注册。
4. `bridge_wrapper` 优先确认具体桥接厂商，再决定是否值得单独适配。
