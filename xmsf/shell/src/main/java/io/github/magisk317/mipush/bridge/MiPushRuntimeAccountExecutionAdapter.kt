package io.github.magisk317.mipush.bridge

import android.content.Context
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.push.service.IPushRuntimeObserver
import com.xiaomi.push.service.MIPushAccount
import com.xiaomi.push.service.MIPushAccountUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.service.runtime.MIPushAccountUtilsRuntime
import java.io.IOException

internal class MiPushRuntimeAccountExecutionAdapter(
    private val observer: IPushRuntimeObserver,
) {
    fun applyStoredAccountEnvironment(context: Context): MIPushAccount? {
        val account = MIPushAccountUtils.getMIPushAccount(context.applicationContext) ?: return null
        BuildSettings.setEnvType(account.envType)
        PushRuntime.observeAccountEvent(
            "account_env_applied",
            "MiPushRuntimeObserverBridge.applyStoredAccountEnvironment",
        )
        return account
    }

    fun envType(context: Context): Int =
        MIPushAccountUtils.getMIPushAccount(context.applicationContext)?.envType ?: 0

    fun loadAccount(context: Context, source: String): MIPushAccount? {
        val account = MIPushAccountUtils.getMIPushAccount(context.applicationContext)
        PushRuntime.observeAccountEvent(
            action = if (account == null) "account_missing" else "account_loaded",
            source = source,
        )
        return account
    }

    fun registerAccount(
        context: Context,
        packageName: String,
        appId: String,
        appToken: String,
        source: String,
    ): MIPushAccount? {
        return try {
            MIPushAccountUtils.register(context, packageName, appId, appToken, observer).also { account ->
                PushRuntime.observeAccountEvent(
                    action = if (account == null) "account_register_empty" else "account_registered",
                    source = source,
                )
            }
        } catch (_: IOException) {
            PushRuntime.observeAccountEvent("account_register_failed_io", source)
            null
        } catch (_: Exception) {
            PushRuntime.observeAccountEvent("account_register_failed_runtime", source)
            null
        }
    }

    fun resolveAccountUrl(
        region: String?,
        oneBoxBuild: Boolean,
        oneBoxHost: String,
        sandBoxBuild: Boolean,
    ): String = MIPushAccountUtilsRuntime.resolveAccountUrl(region, oneBoxBuild, oneBoxHost, sandBoxBuild)

    fun clearAccount(context: Context, packageName: String) {
        MIPushAccountUtils.clearAccount(context)
        PushRuntime.observeAccountEvent(
            "account_cleared",
            "MiPushRuntimeObserverBridge.clearAccount:$packageName",
        )
    }

    fun onAccountEvent(packageName: String, event: String) {
        PushRuntime.observeAccountEvent(event, "MiPushRuntimeObserverBridge.onAccountEvent:$packageName")
    }
}
