package com.xiaomi.xmsf.push.service

import android.accounts.AccountManager
import android.content.Context
import android.text.TextUtils
import com.xiaomi.mipush.sdk.MiPushClient

class XMAccountManager private constructor(context: Context) {
    private var appCtx: Context = context.applicationContext ?: context
    private var uid: String = ""

    fun setAccountAsAlias() {
        val xiaomiUserId = getXiaomiUserId(appCtx)
        if ((TextUtils.isEmpty(uid) && !TextUtils.isEmpty(xiaomiUserId)) ||
            (!TextUtils.isEmpty(uid) && uid != xiaomiUserId)
        ) {
            if (TextUtils.isEmpty(uid)) {
                MiPushClient.setAlias(appCtx, xiaomiUserId, null)
            } else {
                MiPushClient.unsetAlias(appCtx, uid, null)
            }
            uid = xiaomiUserId ?: ""
        }
    }

    private fun getXiaomiUserId(context: Context): String? {
        val accounts = AccountManager.get(context).accounts
        for (account in accounts) {
            if (account.type == "com.xiaomi") {
                val name = account.name
                if (!name.trim().isEmpty()) {
                    return name
                }
            }
        }
        return null
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
