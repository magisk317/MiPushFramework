package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

enum class RetryType {
    DISABLE_PUSH,
    ENABLE_PUSH,
    UPLOAD_HUAWEI_TOKEN,
    UPLOAD_FCM_TOKEN,
    UPLOAD_COS_TOKEN,
    UPLOAD_FTOS_TOKEN,
}
