package io.github.magisk317.mipush.manager

import android.content.Context
import android.widget.Toast
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult
import io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult
import io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriter
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult

import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsManager constructor(
    private val runtimeActions: ManagerRuntimeActions,
    private val logGateway: ManagerLogGateway,
    private val zygiskConfigGateway: io.github.magisk317.mipush.manager.application.ZygiskConfigGateway,
) {
    private companion object {
        const val DEFAULT_LOG_COPY_BUFFER_SIZE = 64 * 1024
    }

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

    private suspend fun buildRuntimeLogBundle(context: Context): ManagerLogExportResult {
        return logGateway.buildLogBundle(context)
    }

    suspend fun saveRuntimeLogBundle(
        context: Context,
        destination: OutputStream,
    ): ManagerLogBundleWriteResult {
        (logGateway as? ManagerLogBundleWriter)?.let { writer ->
            return writer.writeLogBundle(context, destination)
        }
        val result = buildRuntimeLogBundle(context)
        val archivePath = result.archivePath
            ?: return ManagerLogBundleWriteResult(success = false, details = result.details)
        return runCatching {
            File(archivePath).inputStream().use { input ->
                input.copyTo(destination, bufferSize = DEFAULT_LOG_COPY_BUFFER_SIZE)
            }
            ManagerLogBundleWriteResult(success = true, details = result.details)
        }.getOrElse { error ->
            ManagerLogBundleWriteResult(
                success = false,
                details = "log_export_save_failed:${error.message ?: error.javaClass.simpleName}",
            )
        }
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
