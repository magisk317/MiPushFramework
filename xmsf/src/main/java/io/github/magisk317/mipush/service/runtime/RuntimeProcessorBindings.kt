package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.sdk.PushMessageProcessor
import io.github.magisk317.mipush.utils.Configurations

object RuntimeProcessorBindings {
    fun createPushMessageProcessor(configurations: Configurations): PushMessageProcessor =
        PushMessageProcessor(configurations)
}
