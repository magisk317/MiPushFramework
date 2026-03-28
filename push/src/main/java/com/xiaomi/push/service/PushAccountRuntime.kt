package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.xmsf.runtime.PushChannelState
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.runtime.PushRuntimeChannelTracker
import org.json.JSONException
import java.io.IOException

object PushAccountRuntime {
    @JvmStatic
    fun loadAccount(context: Context, source: String): MIPushAccount? {
        val account = MIPushAccountUtils.getMIPushAccount(context.applicationContext)
        PushRuntime.observeAccountEvent(
            action = if (account == null) "account_missing" else "account_loaded",
            source = source
        )
        return account
    }

    @JvmStatic
    fun loadAccount(service: XMPushService, source: String): MIPushAccount? {
        return loadAccount(service.applicationContext, source)
    }

    @JvmStatic
    fun applyStoredAccountEnvironment(service: XMPushService, source: String): MIPushAccount? {
        val account = loadAccount(service.applicationContext, source) ?: return null
        BuildSettings.setEnvType(account.envType)
        PushRuntime.observeAccountEvent("account_env_applied", source)
        return account
    }

    @JvmStatic
    fun attachAccountClient(
        service: XMPushService,
        account: MIPushAccount,
        source: String
    ): PushClientsManager.ClientLoginInfo {
        val client = account.toClientLoginInfo(service)
        MIPushHelper.prepareClientLoginInfo(service, client)
        PushClientsManager.getInstance().addActiveClient(client)
        PushRuntime.observeAccountEvent("account_client_attached", source)
        PushRuntime.observeChannelState(
            packageName = client.pkgName,
            channelId = client.chid,
            userId = client.userId,
            session = client.session,
            state = PushChannelState.Unbound,
            source = source,
            reasonMessage = "account_client_attached"
        )
        PushRuntimeChannelTracker.syncNow("$source:sync")
        return client
    }

    @JvmStatic
    fun registerAccount(
        context: Context,
        packageName: String,
        appId: String,
        appToken: String,
        source: String
    ): MIPushAccount? {
        return try {
            MIPushAccountUtils.register(context, packageName, appId, appToken).also { account ->
                PushRuntime.observeAccountEvent(
                    action = if (account == null) "account_register_empty" else "account_registered",
                    source = source
                )
            }
        } catch (e: IOException) {
            MyLog.e(e)
            PushRuntime.observeAccountEvent("account_register_failed_io", source)
            null
        } catch (e: JSONException) {
            MyLog.e(e)
            PushRuntime.observeAccountEvent("account_register_failed_json", source)
            null
        }
    }

    @JvmStatic
    fun setAccountChangeListener(source: String, listener: Runnable?) {
        MIPushAccountUtils.setAccountChangeListener(
            if (listener == null) {
                null
            } else {
                MIPushAccountUtils.PushAccountChangeListener { listener.run() }
            }
        )
        PushRuntime.observeAccountEvent("account_listener_updated", source)
    }

    @JvmStatic
    fun clearAccount(service: XMPushService, source: String) {
        MIPushAccountUtils.clearAccount(service)
        PushRuntime.observeAccountEvent("account_cleared", source)
    }
}
