package com.xiaomi.push.service

import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.protobuf.micro.CodedInputStreamMicro
import com.google.protobuf.micro.CodedOutputStreamMicro
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor
import com.xiaomi.network.HttpUtils
import com.xiaomi.push.protobuf.ChannelConfig
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.smack.util.TaskExecutor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

class ServiceConfig private constructor() {
    abstract class Listener {
        open fun onConfigChange(pushServiceConfig: ChannelConfig.PushServiceConfig) = Unit
        open fun onConfigMsgReceive(pushServiceConfigMsg: ChannelMessage.PushServiceConfigMsg) = Unit
    }

    companion object {
        private const val CLOUDCONFIG = "XMCloudCfg"
        private const val CONFIG_URL = "https://resolver.msg.xiaomi.net/psc/?t=a"
        private const val CONFIG_URL_GLOBAL = "https://resolver.msg.global.xiaomi.net/psc/?t=a"
        private const val PREF_NAME = "XMPushServiceConfig"
        private const val PREF_UUID = "DeviceUUID"

        @Volatile
        private var deviceUUID: String? = null

        @JvmField
        val instance: ServiceConfig = ServiceConfig()

        @JvmStatic
        fun getDeviceUUID(): String? {
            synchronized(ServiceConfig::class.java) {
                if (deviceUUID == null) {
                    val sharedPreferences = SystemUtils.context!!.getSharedPreferences(PREF_NAME, 0)
                    deviceUUID = sharedPreferences.getString(PREF_UUID, null)
                    if (deviceUUID == null) {
                        deviceUUID = DeviceInfo.getDeviceId(SystemUtils.context!!, false)
                        if (deviceUUID != null) {
                            sharedPreferences.edit().putString(PREF_UUID, deviceUUID).commit()
                        }
                    }
                }
                return deviceUUID
            }
        }

        @JvmStatic
        fun getInstance(): ServiceConfig = instance
    }

    private var config: ChannelConfig.PushServiceConfig? = null
    private val listeners = arrayListOf<Listener>()
    private var pendingFetchTask: SerializedAsyncTaskProcessor.SerializedAsyncTask? = null

    private fun checkLoad() {
        if (config == null) {
            load()
        }
    }

    private fun fetchConfig() {
        if (pendingFetchTask != null) {
            return
        }
        val task = object : SerializedAsyncTaskProcessor.SerializedAsyncTask() {
            private var success = false

            override fun process() {
                try {
                    val region = AppRegionStorage.getInstance(SystemUtils.context!!).getRegion()
                    val url = if (TextUtils.isEmpty(region) || Region.China.name == region) CONFIG_URL else CONFIG_URL_GLOBAL
                    val remoteConfig = ChannelConfig.PushServiceConfig.parseFrom(
                        Base64.decode(HttpUtils.get(SystemUtils.context!!, url, null), 10),
                    )
                    if (remoteConfig != null) {
                        config = remoteConfig
                        success = true
                        save()
                    }
                } catch (e: Exception) {
                    MyLog.w("fetch config failure: ${e.message}")
                }
            }

            override fun postProcess() {
                pendingFetchTask = null
                if (success) {
                    val snapshot = synchronized(this@ServiceConfig) {
                        listeners.toTypedArray()
                    }
                    snapshot.forEach { listener ->
                        config?.let(listener::onConfigChange)
                    }
                }
            }
        }
        pendingFetchTask = task
        TaskExecutor.execute(task)
    }

    private fun load() {
        var input: BufferedInputStream? = null
        try {
            input = BufferedInputStream(SystemUtils.context!!.openFileInput(CLOUDCONFIG))
            config = ChannelConfig.PushServiceConfig.parseFrom(CodedInputStreamMicro.newInstance(input))
        } catch (e: Exception) {
            MyLog.w("load config failure: ${e.message}")
        } finally {
            IOUtils.closeQuietly(input)
        }
        if (config == null) {
            config = ChannelConfig.PushServiceConfig()
        }
    }

    private fun save() {
        try {
            val currentConfig = config ?: return
            val output = BufferedOutputStream(SystemUtils.context!!.openFileOutput(CLOUDCONFIG, 0))
            output.use {
                val codedOutput = CodedOutputStreamMicro.newInstance(it)
                currentConfig.writeTo(codedOutput)
                codedOutput.flush()
            }
        } catch (e: Exception) {
            MyLog.w("save config failure: ${e.message}")
        }
    }

    fun addListener(listener: Listener) {
        synchronized(this) {
            listeners.add(listener)
        }
    }

    fun clear() {
        synchronized(this) {
            listeners.clear()
        }
    }

    fun getBoolSetting(key: String, defaultValue: Boolean): Boolean {
        return SystemUtils.context!!.getSharedPreferences(PREF_NAME, 0).getBoolean(key, defaultValue)
    }

    fun getConfig(): ChannelConfig.PushServiceConfig? {
        checkLoad()
        return config
    }

    fun getConfigVersion(): Int {
        checkLoad()
        return config?.configVersion ?: 0
    }

    fun handle(pushServiceConfigMsg: ChannelMessage.PushServiceConfigMsg) {
        if (pushServiceConfigMsg.hasCloudVersion() && pushServiceConfigMsg.cloudVersion > getConfigVersion()) {
            fetchConfig()
        }
        val snapshot = synchronized(this) {
            listeners.toTypedArray()
        }
        snapshot.forEach { it.onConfigMsgReceive(pushServiceConfigMsg) }
    }

    fun removeListener(listener: Listener) {
        synchronized(this) {
            listeners.remove(listener)
        }
    }

    fun setSetting(key: String, value: Boolean) {
        SystemUtils.context!!.getSharedPreferences(PREF_NAME, 0).edit().putBoolean(key, value).commit()
    }
}
