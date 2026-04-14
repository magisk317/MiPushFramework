# Current Runtime Call Flow

This document captures the current product-owned runtime chain after the `runtime-core` and `legacy-runtime` boundary split.

It is the reference for future stock-XMSF ports: new compatibility features should attach to one of these stages instead of bypassing the runtime spine.

## 1. App Init

- Entry point: `MiPushFrameworkApp`
- Main work:
  - initialize DB and app context
  - install logger and crash logger
  - install hook layer
  - initialize notification compatibility
  - attach runtime execution bridge
  - attach channel tracker
  - enable push controller and wake activation service

Key source:

- `push/src/main/java/io/github/magisk317/mipush/app/MiPushFrameworkApp.kt`

## 2. Bridge Entry

- Entry point: `com.xiaomi.xmsf.push.service.MiPushFacadeService`
- Main work:
  - receive XMSF-facing intents
  - record routing state into `PushRuntime`
  - refresh config state when required
  - forward business intents into legacy `com.xiaomi.push.service.XMPushService`

Key source:

- `push/src/main/java/com/xiaomi/xmsf/push/service/MiPushFacadeService.kt`

## 3. Runtime Spine

- Entry point: `runtime-core` `PushRuntime`
- Main work:
  - queue and drain bridge intents
  - track registration, connection, and channel state
  - replay registration work on boot, network, and account changes
  - track downstream delivery and notification counters

Key source:

- `runtime-core/src/main/java/com/xiaomi/xmsf/runtime/PushRuntime.kt`

## 4. Execution Host

- Entry point: `PushRuntimeExecutionBridge`
- Main work:
  - framework self-registration
  - forced app registration
  - pending register task processing
  - Xiaomi-account alias sync through the product-owned account/cloud bridge
  - downstream payload dispatch
  - notification cancel dispatch
  - connection ensure/reset

Key source:

- `push/src/main/java/io/github/magisk317/mipush/runtime/PushRuntimeExecutionBridge.kt`

## 5. Legacy Long Connection

- Entry point: legacy `com.xiaomi.push.service.XMPushService`
- Main work:
  - own the long-lived connection stack
  - run reconnect, packet sync, and intent delegates
  - expose lifecycle state back to product-owned code

Key source:

- `legacy-runtime/src/main/java/com/xiaomi/push/service/XMPushService.kt`

## 6. Downstream Delivery

- Entry point: `com.xiaomi.push.sdk.PushMessageProcessor`
- Main work:
  - optionally foreground the target app
  - deliver to target `PushMessageHandler`
  - fall back to broadcast delivery if direct service start fails

Key source:

- `push/src/main/java/com/xiaomi/push/sdk/PushMessageProcessor.kt`

## 7. Notification Publish

- Entry point: `MyMIPushNotificationHelper`
- Main work:
  - unpack and dedupe payload
  - apply package-config operations
  - publish, ignore, wake, or open based on resolved policy

Supporting layers:

- `NotificationController`
- `NotificationManagerEx`
- `NotificationIdentityBridge`

Key sources:

- `push/src/main/java/io/github/magisk317/mipush/framework/notification/MyMIPushNotificationHelper.kt`
- `push/src/main/java/io/github/magisk317/mipush/notification/NotificationManagerEx.kt`
- `legacy-runtime/src/main/java/com/xiaomi/push/service/NotificationIdentityBridge.kt`

## 8. Stock Compatibility Surfaces

- Entry points:
  - `com.xiaomi.xmsf.provider.ChannelProvider`
  - `com.xiaomi.xmsf.provider.PushProfileIdProvider`
  - `com.xiaomi.push.provider.PushCommonProvider`
  - `com.xiaomi.push.provider.PushSupportProvider`
  - `com.xiaomi.xmsf.pushcontrol.PushControlProvider`
  - `com.xiaomi.xmsf.services.MainProcBridgeService`
  - `com.xiaomi.xmsf.services.ServiceBoxService`
  - `com.xiaomi.xmsf.services.keepalive.strategy.KeepAliveConfigService`
  - `com.xiaomi.xmsf.push.service.notificationcollection.NotificationListener`
  - `com.xiaomi.xmsf.push.service.StatService`
  - `com.xiaomi.xmsf.push.service.receivers.XMSFUploadReceiver`
  - `com.xiaomi.xmsf.pushprocess.PushInnerReceiver`
- Main work:
  - expose stock provider authorities and service names expected by callers
  - bridge stock-facing calls into `PushRuntime`, notification helpers, online config, and app DB state
  - keep subprocess and keepalive coordination inside product-owned glue instead of pushing it down into `legacy-runtime`

Key source:

- `push/src/main/java/com/xiaomi/xmsf/stock/StockSurfaceSupport.kt`

## 9. Account / Cloud Bridge

- Entry point: `DefaultAccountCloudBridge`
- Main work:
  - discover Xiaomi-account presence
  - synchronize alias state
  - retrieve service tokens through `AccountManager` when available
  - expose stock-restored cloud/bind surfaces with defined degraded behavior

Key source:

- `push/src/main/java/com/xiaomi/xmsf/account/AccountCloudBridge.kt`

## Porting Rule

When porting stock XMSF behavior, first classify the feature into one of these stages:

- app init
- bridge entry
- runtime spine
- execution host
- legacy long connection
- downstream delivery
- notification publish
- stock compatibility surfaces
- account / cloud bridge

Only bypass `PushRuntime` when the stock feature is strictly self-contained and does not participate in routing, lifecycle, or shared compatibility state.
