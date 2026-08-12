package io.github.magisk317.mipush.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult
import io.github.magisk317.mipush.common.manager.ZygiskModuleReadResult
import io.github.magisk317.mipush.common.manager.ZygiskPackageScanResult
import io.github.magisk317.mipush.common.utils.Utils

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsManager constructor(
    private val runtimeActions: ManagerRuntimeActions,
    private val logGateway: ManagerLogGateway,
    private val zygiskConfigGateway: io.github.magisk317.mipush.common.manager.ZygiskConfigGateway,
) {
    val mClearingHistory: AtomicBoolean = AtomicBoolean(false)

    fun clearHistory(context: Context, scope: CoroutineScope) {
        if (mClearingHistory.compareAndSet(false, true)) {
            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.start), Toast.LENGTH_SHORT)
                }
                try {
                    runtimeActions.clearHistory()
                    withContext(Dispatchers.Main) {
                        Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.fail), Toast.LENGTH_SHORT)
                    }
                } finally {
                    mClearingHistory.set(false)
                }
            }
        }
    }

    suspend fun startMiPushServiceAsForegroundService(context: Context) {
        runtimeActions.startMiPushServiceAsForegroundService(context)
    }

    suspend fun setRuntimeLogRetentionDays(days: Int) {
        runtimeActions.setRuntimeLogRetentionDays(days)
        logGateway.setRetentionDays(days)
    }

    /** 应用事件记录保留天数(触发一次即时清理);天数持久化由 ViewModel 写入 DataStore。 */
    suspend fun applyEventRetentionDays(days: Int) {
        runtimeActions.applyEventRetentionDays(days)
    }

    suspend fun buildRuntimeLogBundle(context: Context): ManagerLogExportResult {
        return logGateway.buildLogBundle(context)
    }

    fun buildRuntimeLogShareIntent(context: Context, file: File): Intent {
        return logGateway.buildShareIntent(context, file)
    }

    suspend fun clearRuntimeLogFolders(context: Context): ManagerLogClearResult {
        return logGateway.clearLogFolders(context)
    }

    suspend fun sendXMPPReconnectRequest(context: Context): Boolean =
        runtimeActions.sendXmppReconnectRequest(context)

    suspend fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return runtimeActions.getRuntimeEnvironmentSnapshot(context)
    }

    suspend fun getConnectionSnapshot(): ManagerConnectionSnapshot {
        return runtimeActions.getConnectionSnapshot()
    }

    suspend fun resetTopActivityCache() {
        runtimeActions.resetTopActivityCache()
    }

    fun shareLogs(context: Context) {
        context.startActivity(
            Intent().setComponent(
                ComponentName(Constants.SERVICE_APP_NAME, Constants.SHARE_LOG_COMPONENT_NAME)
            )
        )
    }

    suspend fun isZygiskModuleEnabled(): ZygiskModuleReadResult = zygiskConfigGateway.isZygiskModuleEnabled()

    fun getZygiskConfigPath(): String = zygiskConfigGateway.getZygiskConfigPath()

    suspend fun getZygiskConfig(): ZygiskConfigReadResult = zygiskConfigGateway.getZygiskConfig()

    suspend fun getZygiskSpoofPackages(): Set<String>? =
        zygiskSpoofPackagesOrNull(getZygiskConfig())

    suspend fun isZygiskSpoofEnabled(packageName: String): Boolean? =
        zygiskSpoofEnabledOrNull(getZygiskConfig(), packageName)

    suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean = zygiskConfigGateway.saveZygiskConfig(config)

    suspend fun scanZygiskPackages(): ZygiskPackageScanResult = zygiskConfigGateway.scanZygiskPackages()

    suspend fun setZygiskSpoofEnabled(packageName: String, enabled: Boolean): Boolean {
        val current = getZygiskConfig() as? ZygiskConfigReadResult.Available ?: return false
        val updated = current.config.withPackageEnabled(packageName, enabled)
        val saved = saveZygiskConfig(updated)
        if (saved) {
            zygiskConfigGateway.forceStopApp(packageName)
        }
        return saved
    }
}

internal fun zygiskSpoofPackagesOrNull(result: ZygiskConfigReadResult): Set<String>? =
    (result as? ZygiskConfigReadResult.Available)?.config?.enabledPackages()

internal fun zygiskSpoofEnabledOrNull(
    result: ZygiskConfigReadResult,
    packageName: String,
): Boolean? = (result as? ZygiskConfigReadResult.Available)
    ?.config
    ?.isEnabledForPackage(packageName)
