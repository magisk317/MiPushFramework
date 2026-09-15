package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.service.runtime.AppPushMessageProcessor
import io.github.magisk317.mipush.freeze.FrozenAppCoordinator
import io.github.magisk317.mipush.utils.Configurations

object RuntimeProcessorBindings {
    fun createAppPushMessageProcessor(
        configurations: Configurations,
        frozenAppCoordinator: FrozenAppCoordinator?,
    ): AppPushMessageProcessor = AppPushMessageProcessor(configurations, frozenAppCoordinator)
}
