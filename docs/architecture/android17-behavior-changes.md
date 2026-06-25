# Android 17 (API 37) 行为变更汇总

> 来源：https://developer.android.com/about/versions/17/
> 整理日期：2026-06-17
> 包含：所有应用变更 + 仅 targetSdk 37 应用变更

---

## 一、所有应用均受影响的变更

### 1. 核心功能

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **应用内存限制** | 基于设备总 RAM 限制应用内存，防止内存泄漏导致系统不稳定 | ⚠️ 高 |

- 系统会保守地设置限制以建立基准
- 可通过 `ApplicationExitInfo.getDescription()` 检测是否受影响
- 退出原因为 `REASON_OTHER`，说明包含 `"MemoryLimiter:AnonSwap"`
- 可通过 `adb shell am memory-limiter` 调整限制

### 2. 隐私权

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **动态短信密码保护扩展** | 所有应用 | ⚠️ 高 |

- OTP 保护从 SMS Retriever 格式扩展到 **WebOTP 格式**
- 非预期接收者的应用在收到消息后 **3 小时内无法访问**
- 影响 `SMS_RECEIVED_ACTION` 广播和短信提供商数据库查询
- 默认短信助理、关联设备配套应用不受影响
- **建议**：迁移到 SMS Retriever 或 SMS User Consent API

### 3. 安全

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **usesCleartextTraffic 弃用计划** | 所有应用 | ⚠️ 中 |
| **限制隐式 URI 授权** | 所有应用（Android 18 生效） | ⚠️ 中 |
| **每个应用的密钥库限制** | 所有应用 | ⚠️ 中 |
| **阻止跨资料环回流量** | 所有应用 | ⚠️ 低 |

#### usesCleartextTraffic 弃用
- 计划在未来版本弃用 `usesCleartextTraffic` 元素
- 需要 HTTP 连接的应用应迁移到网络安全配置文件
- 最低 API < 24 的应用需同时设置 `usesCleartextTraffic=true` 和网络配置文件

#### 限制隐式 URI 授权
- Android 18 开始，系统不再自动授予 `ACTION_SEND`/`ACTION_SEND_MULTIPLE`/`ACTION_IMAGE_CAPTURE` 的 URI 权限
- 需要显式添加 `FLAG_GRANT_READ_URI_PERMISSION`
- 可用 `StrictMode.detectImplicitUriPermissionGrant()` 检测

#### 密钥库限制
- targetSdk 37+：非系统应用最多 **50,000** 个密钥
- 其他应用：最多 **200,000** 个密钥
- 超出限制会抛出 `KeyStoreException`

#### 跨资料环回流量
- 默认不再允许跨个人资料环回流量
- 同一个人资料内的环回流量不受影响

### 4. 用户体验和系统界面

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **旋转后恢复默认 IME 可见性** | 所有应用 | ⚠️ 中 |

- 设备旋转等配置变化后，系统不再恢复之前的 IME 可见性
- 需要显示键盘的应用需设置 `android:windowSoftInputMode="stateAlwaysVisible"` 或在 `onCreate()` 中编程请求

### 5. 人工输入

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **触控板默认传递相对事件** | 使用指针捕获的应用 | ⚠️ 低 |

- 使用 `View.requestPointerCapture()` 时，触控板手势以相对模式报告
- 需要绝对数据的应用改用 `View.POINTER_CAPTURE_MODE_ABSOLUTE`

### 6. 媒体

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **后台音频安全加固** | 所有应用 | ⚠️ 高 |

- 后台音频 API（播放、焦点请求、音量更改）强制限制
- 未处于有效生命周期时调用会静默失败
- 音频焦点 API 返回 `AUDIOFOCUS_REQUEST_FAILED`

### 7. 连接

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **蓝牙绑定丢失的自主重新配对** | 蓝牙应用 | ⚠️ 低 |

- 系统自动重新配对蓝牙设备
- `ACTION_PAIRING_REQUEST` 新增 `EXTRA_PAIRING_CONTEXT`
- `ACTION_KEY_MISSING` 仅在重新配对失败时广播

---

## 二、仅影响 targetSdk 37 应用的变更

### 1. 核心功能

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **MessageQueue 新无锁实现** | targetSdk 37+ | ⚠️ 高 |
| **static final 字段不可修改** | targetSdk 37+ | 🔴 严重 |

#### MessageQueue 无锁实现
- 新实现提升性能，但可能破坏反射私有字段/方法的客户端
- 需要检查是否依赖 MessageQueue 内部实现

#### static final 字段不可修改
- 反射修改会抛出 `IllegalAccessException`
- JNI 修改（如 `SetStaticLongField()`）会导致应用崩溃
- **必须检查所有反射修改 static final 字段的代码**

### 2. 无障碍

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **复杂 IME 实体键盘输入的无障碍支持** | IME 应用 | ⚠️ 低 |

- 新增 `AccessibilityEvent` 和 `TextAttribute` API
- 支持 CJKV 语言输入的语音反馈

### 3. 隐私权

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **启用 ECH（加密客户端 Hello）** | targetSdk 37+ | ⚠️ 低 |
| **本地网络权限** | targetSdk 37+ | ⚠️ 高 |
| **实体设备隐藏密码** | targetSdk 37+ | ⚠️ 低 |
| **标准短信 OTP 保护** | targetSdk 37+ | ⚠️ 高 |

#### ECH 加密
- TLS 连接默认启用 ECH，加密 SNI
- 可通过 `<domainEncryption>` 配置

#### 本地网络权限
- 新增 `ACCESS_LOCAL_NETWORK` 运行时权限
- 属于 `NEARBY_DEVICES` 权限组
- targetSdk 37+ 必须强制执行

#### 标准短信 OTP 保护
- 扩展到标准短信（非 WebOTP/SMS Retriever 格式）
- 收到后 3 小时内不提供
- 建议迁移到 SMS Retriever 或 SMS User Consent API

### 4. 安全

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **BAL 安全加固** | targetSdk 37+ | ⚠️ 高 |
| **默认启用 CT** | targetSdk 37+ | ⚠️ 低 |
| **更安全的原生 DCL** | targetSdk 37+ | ⚠️ 高 |
| **限制 CP2 数据视图 PII** | targetSdk 37+ | ⚠️ 中 |
| **CP2 严格 SQL 检查** | targetSdk 37+ | ⚠️ 中 |

#### BAL 安全加固
- 优化后台活动启动限制，扩展到 IntentSender
- 需从 `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` 迁移到 `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`

#### 更安全的原生 DCL
- 动态加载的原生文件必须标记为只读
- 否则抛出 `UnsatisfiedLinkError`

### 5. 媒体

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **后台音频安全加固（更严格）** | targetSdk 37+ | 🔴 严重 |

- targetSdk 37+ 的应用在后台音频互动时必须有前台服务
- 前台服务必须具有 WIU 权限，或具有精确闹钟权限并与 USAGE_ALARM 互动

### 6. 设备规格

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **大屏设备忽略方向/尺寸限制** | targetSdk 37+ | ⚠️ 中 |

- sw>=600dp 设备忽略屏幕方向、宽高比和尺寸调整限制
- targetSdk 36 可退出，但 targetSdk 37+ 不再可用

### 7. 连接

| 变更 | 影响范围 | 严重程度 |
|------|---------|---------|
| **RFCOMM BluetoothSocket read() 行为** | targetSdk 37+ | ⚠️ 中 |

- `read()` 方法在套接字关闭/断开时返回 -1
- 需检查返回值而非仅捕获 IOException

---

## 三、关键时间线

| 时间 | 事件 |
|------|------|
| Android 17 发布 | 所有应用变更立即生效 |
| targetSdk 37 | 仅 targetSdk 37 应用变更生效 |
| Android 18 | 隐式 URI 授权限制生效 |
