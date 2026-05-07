package com.xiaomi.xmsf.push.service

import android.content.Context
import com.xiaomi.xmsf.account.DefaultAccountCloudBridge

class XMAccountManager private constructor(context: Context) {
    private var appCtx: Context = context.applicationContext ?: context
    private var uid: String = ""
    private val bridge by lazy { DefaultAccountCloudBridge.getInstance(appCtx) }

    fun setAccountAsAlias() {
        val result = bridge.syncAlias(uid)
        if (result.changed) {
            uid = result.currentAlias.orEmpty()
        }
    }

    fun currentAccountName(): String? = bridge.currentAccountName()

    fun getServiceToken(sid: String) = bridge.getServiceToken(sid)

    fun availability() = bridge.availability()

    fun currentAlias(): String = uid

    fun syncAliasForCurrentState() {
        uid = bridge.currentAccountName().orEmpty()
    }

    companion object {
        @Volatile
        private var instance: XMAccountManager? = null

        @JvmStatic
        fun getInstance(context: Context): XMAccountManager {
            return instance ?: synchronized(this) {
                instance ?: XMAccountManager(context).also { instance = it }
            }
        }
    }
}
