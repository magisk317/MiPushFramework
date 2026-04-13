package io.github.magisk317.mipush.framework.lifecycle.runtime

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.MIPushAccount
import com.xiaomi.push.service.MIPushAccountUtils
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.XMPushService
import io.github.magisk317.mipush.service.runtime.MIPushAccountUtilsRuntime
import io.github.magisk317.mipush.runtime.PushChannelState
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import org.json.JSONException
import java.io.IOException

object PushAccountRuntime {
    @JvmStatic
    fun loadAccount(context: Context, source: String, observer: com.xiaomi.push.service.IPushRuntimeObserver? = null): MIPushAccount? {
        val account = MIPushAccountUtils.getMIPushAccount(context.applicationContext)
        PushRuntime.observeAccountEvent(
            action = if (account == null) "account_missing" else "account_loaded",
            source = source
        )
        return account
    }

    @JvmStatic
    fun applyStoredAccountEnvironment(context: Context, source: String): MIPushAccount? {
        val account = loadAccount(context, source) ?: return null
        BuildSettings.setEnvType(account.envType)
        PushRuntime.observeAccountEvent("account_env_applied", source)
        return account
    }

    @JvmStatic
    fun attachAccountClient(
        pushAction: IPushServiceAction,
        context: Context,
        account: MIPushAccount,
        source: String
    ): PushClientsManager.ClientLoginInfo {
        val client = if (pushAction is XMPushService) {
            account.toClientLoginInfo(pushAction)
        } else {
            account.toClientLoginInfo(context)
        }
        if (pushAction is XMPushService) {
            MIPushHelper.prepareClientLoginInfo(pushAction, client)
        }
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
        source: String,
        observer: com.xiaomi.push.service.IPushRuntimeObserver? = null
    ): MIPushAccount? {
        val resolvedObserver = observer ?: com.xiaomi.push.service.XMPushServiceProxy.get()?.runtimeObserver ?: return null
        return try {
            MIPushAccountUtils.register(context, packageName, appId, appToken, resolvedObserver).also { account ->
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
        val changeListener = listener?.let {
            MIPushAccountUtils.PushAccountChangeListener { it.run() }
        }
        MIPushAccountUtils.setAccountChangeListener(changeListener)
        PushRuntime.observeAccountEvent("account_listener_updated", source)
    }

    @JvmStatic
    fun clearAccount(context: Context, source: String) {
        MIPushAccountUtils.clearAccount(context)
        PushRuntime.observeAccountEvent("account_cleared", source)
    }

    @JvmStatic
    fun getAccountURL(region: String?, oneBoxBuild: Boolean, oneBoxHost: String, sandBoxBuild: Boolean): String {
        return MIPushAccountUtilsRuntime.resolveAccountUrl(region, oneBoxBuild, oneBoxHost, sandBoxBuild)
    }
}
