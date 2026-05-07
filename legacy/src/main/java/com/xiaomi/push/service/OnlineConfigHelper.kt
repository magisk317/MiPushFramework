package com.xiaomi.push.service

import android.util.Pair
import com.xiaomi.channel.commonutils.misc.CollectionUtils
import com.xiaomi.xmpush.thrift.ConfigListType
import com.xiaomi.xmpush.thrift.ConfigType
import com.xiaomi.xmpush.thrift.NormalConfig
import com.xiaomi.xmpush.thrift.OnlineConfigItem
import com.xiaomi.xmpush.thrift.XmPushActionCustomConfig
import com.xiaomi.xmpush.thrift.XmPushActionNormalConfig

object OnlineConfigHelper {
    private const val OC_VERSION_PREFIX = "oc_version_"

    private fun convertMessage(
        items: List<OnlineConfigItem>?,
        allowClear: Boolean,
    ): List<Pair<Int, Any?>>? {
        if (CollectionUtils.isEmpty(items)) {
            return null
        }
        val pairs = arrayListOf<Pair<Int, Any?>>()
        for (item in items.orEmpty()) {
            val key = item.key
            val configType = ConfigType.findByValue(item.type) ?: continue
            if (allowClear && item.clear) {
                pairs.add(Pair(key, null))
                continue
            }
            val pair = when (configType) {
                ConfigType.INT -> Pair(key, item.intValue as Any?)
                ConfigType.LONG -> Pair(key, item.longValue as Any?)
                ConfigType.STRING -> Pair(key, item.stringValue as Any?)
                ConfigType.BOOLEAN -> Pair(key, item.isBoolValue as Any?)
            }
            pairs.add(pair)
        }
        return pairs
    }

    @JvmStatic
    fun getVersion(onlineConfig: OnlineConfig, configListType: ConfigListType): Int {
        val defaultVersion = when (configListType) {
            ConfigListType.MISC_CONFIG -> 1
            ConfigListType.PLUGIN_CONFIG -> 0
        }
        return onlineConfig.preferences.getInt(getVersionKey(configListType), defaultVersion)
    }

    private fun getVersionKey(configListType: ConfigListType): String {
        return OC_VERSION_PREFIX + configListType.value
    }

    @JvmStatic
    fun setVersion(onlineConfig: OnlineConfig, configListType: ConfigListType, version: Int) {
        onlineConfig.preferences.edit().putInt(getVersionKey(configListType), version).commit()
    }

    @JvmStatic
    fun updateCustomConfigs(onlineConfig: OnlineConfig, customConfig: XmPushActionCustomConfig) {
        onlineConfig.updateCustomConfigs(convertMessage(customConfig.customConfigs, true))
        onlineConfig.runCallback()
    }

    @JvmStatic
    fun updateNormalConfigs(onlineConfig: OnlineConfig, normalConfig: XmPushActionNormalConfig) {
        for (config in normalConfig.normalConfigs.orEmpty()) {
            if (config.version > getVersion(onlineConfig, config.type)) {
                setVersion(onlineConfig, config.type, config.version)
                onlineConfig.updateNormalConfigs(convertMessage(config.configItems, false))
            }
        }
        onlineConfig.runCallback()
    }
}
