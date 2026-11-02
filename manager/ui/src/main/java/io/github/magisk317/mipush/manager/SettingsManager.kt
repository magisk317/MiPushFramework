package io.github.magisk317.mipush.manager

import android.content.Context
import android.widget.Toast
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
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

    suspend fun getZygiskSpoofPackages(): Set<String>? = runtimeActions.getZygiskSpoofPackages()
}
