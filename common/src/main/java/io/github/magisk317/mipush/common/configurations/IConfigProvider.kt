package io.github.magisk317.mipush.common.configurations

interface IConfigProvider {
    suspend fun isShowConfigurationListOnLoadedAsync(): Boolean
}
