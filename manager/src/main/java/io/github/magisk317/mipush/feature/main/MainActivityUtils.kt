package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.mipush.runtime.core.ConnectionStatus
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import io.github.magisk317.mipush.platform.support.InternalMessenger
import io.github.magisk317.mipush.platform.support.PushServiceBroadcastActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivityUtils(
    private val settingsManager: SettingsManager,
) {
    private val tag = "MainActivityUtils"
    private var messenger: InternalMessenger? = null

    fun interface ConnectionStatusChanged {
        fun onChange(status: ConnectionStatus)
    }

    fun initOnCreate(
        context: Context,
        loadConfigurations: suspend (Context) -> Unit,
        connectionStatusChanged: ConnectionStatusChanged,
        scope: CoroutineScope,
    ) {
        val appContext = context.applicationContext
        close()
        messenger = InternalMessenger(appContext).apply {
            register(IntentFilter(PushServiceBroadcastActions.SET_CONNECTION_STATUS))
            addListener { intent ->
                val status = intent.getStringExtra("status") ?: return@addListener
                connectionStatusChanged.onChange(ConnectionStatus.valueOf(status))
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                printHookResultForCheck()
            } catch (error: RuntimeReadUnavailableException) {
                logW(
                    "runtime snapshot unavailable during startup " +
                        "op=${error.operation} status=${error.status}",
                    error,
                )
            } catch (error: Throwable) {
                logE("runtime snapshot check failed during startup", error)
            }
        }
        scope.launch(Dispatchers.IO) {
            loadConfigurations(appContext)
        }
        messenger?.send(Intent(PushServiceBroadcastActions.GET_CONNECTION_STATUS))
    }

    suspend fun printHookResultForCheck() {
        val snapshot = settingsManager.getRuntimeEnvironmentSnapshot(Utils.getApplication() ?: return)
        safeRuntimeSnapshotLines(snapshot).forEach(::logI)
    }

    fun close() {
        messenger?.unregister()
        messenger = null
    }

    companion object {
        internal fun safeRuntimeSnapshotLines(snapshot: ManagerRuntimeEnvironmentSnapshot): List<String> = listOf(
            "[hook_res] isMiui=${snapshot.isMiui}",
            "[hook_res] imeiPresent=${!snapshot.imei.isNullOrBlank()}",
            "[hook_res] macAddressPresent=${!snapshot.macAddress.isNullOrBlank()}",
            "[hook_res] xmppServerConfigured=${snapshot.xmppServerHost.isNotBlank()}",
        )
    }
}
