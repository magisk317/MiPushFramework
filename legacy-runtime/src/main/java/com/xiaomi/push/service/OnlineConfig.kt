package com.xiaomi.push.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Pair
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.CollectionUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.xmpush.thrift.ConfigKey
import java.util.HashSet

class OnlineConfig private constructor(context: Context) {
    abstract class OCUpdateCallback(
        private val id: Int,
        private val description: String,
    ) : Runnable {
        override fun equals(other: Any?): Boolean {
            return other is OCUpdateCallback && id == other.id
        }

        override fun hashCode(): Int = id

        protected abstract fun onCallback()

        final override fun run() {
            onCallback()
        }
    }

    companion object {
        private const val CUSTOM_OC_PREFIX = "custom_oc_"
        private const val NORMAL_OC_PREFIX = "normal_oc_"

        @Volatile
        private var instance: OnlineConfig? = null

        @JvmStatic
        fun getInstance(context: Context): OnlineConfig {
            return instance ?: synchronized(this) {
                instance ?: OnlineConfig(context.applicationContext).also { instance = it }
            }
        }
    }

    @JvmField
    val preferences: SharedPreferences = context.getSharedPreferences("mipush_oc", 0)

    private val callbacks = HashSet<OCUpdateCallback>()

    private fun getCustomOcKey(id: Int): String = CUSTOM_OC_PREFIX + id

    private fun getNormalOcKey(id: Int): String = NORMAL_OC_PREFIX + id

    private fun putConfig(editor: SharedPreferences.Editor, pair: Pair<Int, Any?>, key: String) {
        when (val value = pair.second) {
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is String -> {
                if (key == getNormalOcKey(ConfigKey.AppIsInstalledList.value)) {
                    editor.putString(key, Base64Coder.encodeString(value))
                } else {
                    editor.putString(key, value)
                }
            }
            is Boolean -> editor.putBoolean(key, value)
        }
    }

    fun addOCUpdateCallbacks(callback: OCUpdateCallback) {
        synchronized(this) {
            callbacks.add(callback)
        }
    }

    fun clearCallbacks() {
        synchronized(this) {
            callbacks.clear()
        }
    }

    fun getBooleanValue(id: Int, defaultValue: Boolean): Boolean {
        val customKey = getCustomOcKey(id)
        if (preferences.contains(customKey)) {
            return preferences.getBoolean(customKey, false)
        }
        val normalKey = getNormalOcKey(id)
        return if (preferences.contains(normalKey)) preferences.getBoolean(normalKey, false) else defaultValue
    }

    fun getIntValue(id: Int, defaultValue: Int): Int {
        val customKey = getCustomOcKey(id)
        if (preferences.contains(customKey)) {
            return preferences.getInt(customKey, 0)
        }
        val normalKey = getNormalOcKey(id)
        return if (preferences.contains(normalKey)) preferences.getInt(normalKey, 0) else defaultValue
    }

    fun getLongValue(id: Int, defaultValue: Long): Long {
        val customKey = getCustomOcKey(id)
        if (preferences.contains(customKey)) {
            return preferences.getLong(customKey, 0L)
        }
        val normalKey = getNormalOcKey(id)
        return if (preferences.contains(normalKey)) preferences.getLong(normalKey, 0L) else defaultValue
    }

    fun getStringValue(id: Int, defaultValue: String): String? {
        val customKey = getCustomOcKey(id)
        if (preferences.contains(customKey)) {
            return preferences.getString(customKey, null)
        }
        val normalKey = getNormalOcKey(id)
        return if (preferences.contains(normalKey)) preferences.getString(normalKey, null) else defaultValue
    }

    fun runCallback() {
        MyLog.v("OC_Callback : receive new oc data")
        val snapshot = hashSetOf<OCUpdateCallback>()
        synchronized(this) {
            snapshot.addAll(callbacks)
        }
        snapshot.forEach { it.run() }
        snapshot.clear()
    }

    fun updateCustomConfigs(pairs: List<Pair<Int, Any?>>?) {
        if (CollectionUtils.isEmpty(pairs)) {
            return
        }
        val editor = preferences.edit()
        for (pair in pairs.orEmpty()) {
            val keyId = pair.first ?: continue
            val customKey = getCustomOcKey(keyId)
            if (pair.second == null) {
                editor.remove(customKey)
            } else {
                putConfig(editor, pair, customKey)
            }
        }
        editor.commit()
    }

    fun updateNormalConfigs(pairs: List<Pair<Int, Any?>>?) {
        if (CollectionUtils.isEmpty(pairs)) {
            return
        }
        val editor = preferences.edit()
        for (pair in pairs.orEmpty()) {
            val keyId = pair.first ?: continue
            if (pair.second != null) {
                putConfig(editor, pair, getNormalOcKey(keyId))
            }
        }
        editor.commit()
    }
}
