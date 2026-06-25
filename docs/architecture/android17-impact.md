# MiPushFramework Android 17 影响分析

> 项目：MiPushFramework (com.xiaomi.xmsf)
> targetSdk：37
> 分析日期：2026-06-17

---

## 总览

| 影响等级 | 数量 |
|---------|------|
| 🔴 严重 | 1 |
| ⚠️ 高 | 2 |
| ⚠️ 中 | 3 |
| ⚠️ 低 | 2 |

---

## 🔴 严重问题

### 1. Hooker.kt 反射修改静态字段

**文件**: `xmsf/src/main/java/io/github/magisk317/mipush/utils/Hooker.kt:118-126`

```kotlin
private fun hookField(klass: Class<*>, field: String, value: Any) {
    val target = klass.getDeclaredField(field)
    target.isAccessible = true
    target.set(null, value)  // Android 17: IllegalAccessException for static final
}
```

**影响**: Android 17 中，反射修改 `static final` 字段会抛出 `IllegalAccessException`。JNI 修改会直接崩溃。

**修复方案**:
- 检查被修改的字段是否为 `static final`
- 如果是，需要寻找替代方案（如通过系统 API 或 Intent 传递配置）
- 考虑使用 `Unsafe` 类（不推荐）或重构代码避免修改静态字段

---

## ⚠️ 高风险问题

### 2. 应用内存限制

**影响**: Android 17 基于设备总 RAM 限制应用内存。

**修复方案**:
- 建立内存基准
- 遵循内存最佳实践
- 在受限环境中测试
- 使用 `adb shell am memory-limiter` 调试

### 3. 后台音频安全加固

**影响**: 后台音频 API 调用会静默失败。

**修复方案**:
- 检查是否有后台音频播放需求
- 确保前台服务在需要时运行
- 验证音频焦点请求逻辑

---

## ⚠️ 中风险问题

### 4. SMS/OTP 保护扩展

**影响**: WebOTP 格式的 OTP 短信 3 小时延迟。

**当前状态**: 项目未发现直接使用 SMS Retriever 或 OTP 读取的代码。

**修复方案**:
- 确认是否有 OTP 相关功能
- 如有，迁移到 SMS Retriever 或 SMS User Consent API

### 5. usesCleartextTraffic 弃用计划

**当前状态**: 项目已正确配置网络安全配置文件：
```xml
<base-config cleartextTrafficPermitted="false" />
<domain-config cleartextTrafficPermitted="true">
    <!-- Xiaomi push domains -->
</domain-config>
```

**修复方案**: ✅ 已正确配置，无需修改。

### 6. 密钥库限制

**影响**: targetSdk 37+ 最多 50,000 个密钥。

**当前状态**: 项目未发现大量密钥创建。

**修复方案**: ✅ 无需修改。

---

## ⚠️ 低风险问题

### 7. 隐式 URI 授权限制（Android 18）

**当前状态**: 项目未发现使用 `ACTION_SEND`/`ACTION_SEND_MULTIPLE`/`ACTION_IMAGE_CAPTURE`。

**修复方案**: ✅ 无需修改。

### 8. 跨资料环回流量

**影响**: 默认不再允许跨个人资料环回流量。

**当前状态**: 项目未发现跨资料通信。

**修复方案**: ✅ 无需修改。

---

## 建议优先级

1. **立即处理**: Hooker.kt 反射修改静态字段问题
2. **短期处理**: 内存限制测试
3. **中期处理**: 后台音频安全加固验证
4. **长期关注**: Android 18 隐式 URI 授权限制
