package io.github.magisk317.mipush.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
import io.github.magisk317.mipush.common.manager.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileContent
import io.github.magisk317.mipush.common.manager.ManagerRuntimeLogFileSummary
import io.github.magisk317.mipush.common.utils.Utils

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SettingsManager constructor(
    private val configGateway: ManagerConfigGateway,
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
                runtimeActions.clearHistory()
                withContext(Dispatchers.Main) {
                    Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT)
                }
                mClearingHistory.set(false)
            }
        }
    }

    fun startMiPushServiceAsForegroundService(context: Context) {
        runtimeActions.startMiPushServiceAsForegroundService(context)
    }

    fun setRuntimeLogRetentionDays(days: Int) {
        runtimeActions.setRuntimeLogRetentionDays(days)
        logGateway.setRetentionDays(days)
    }

    fun summarizeRuntimeLogFiles(context: Context): ManagerRuntimeLogFileSummary {
        return logGateway.summarizeFiles(context)
    }

    fun readRuntimeLogFile(context: Context, fileName: String): ManagerRuntimeLogFileContent? {
        return logGateway.readLogFile(context, fileName)
    }


    fun buildRuntimeLogBundle(context: Context): ManagerLogExportResult {
        return logGateway.buildLogBundle(context)
    }

    fun buildRuntimeLogShareIntent(context: Context, file: File): Intent {
        return logGateway.buildShareIntent(context, file)
    }

    fun clearRuntimeLogFolders(context: Context): ManagerLogClearResult {
        return logGateway.clearLogFolders(context)
    }

    fun sendXMPPReconnectRequest(context: Context) {
        runtimeActions.sendXmppReconnectRequest(context)
    }

    fun setXMPPServer(context: Context, newHost: String) {
        runtimeActions.setXmppServer(context, newHost)
    }

    fun getXMPPServerHint(): String {
        return runtimeActions.getXmppServerHint()
    }

    fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return runtimeActions.getRuntimeEnvironmentSnapshot(context)
    }

    fun resetTopActivityCache() {
        runtimeActions.resetTopActivityCache()
    }

    fun getXMPPServer(context: Context): String? = runBlocking { configGateway.getXmppServer() }

    fun getConfigurationDirectory(context: Context): Uri? = runBlocking { configGateway.getConfigurationDirectory() }

    fun shareLogs(context: Context) {
        context.startActivity(
            Intent().setComponent(
                ComponentName(Constants.SERVICE_APP_NAME, Constants.SHARE_LOG_COMPONENT_NAME)
            )
        )
    }

    fun saveConfigurationUri(context: Context, data: Intent): Uri {
        val uri = data.data!!
        setConfigurationDirectory(context, uri)
        return uri
    }

    fun setConfigurationDirectory(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        runBlocking { configGateway.setConfigurationDirectory(uri) }
        configGateway.loadConfigurations(context)
    }

    fun isZygiskModuleEnabled(): Boolean = zygiskConfigGateway.isZygiskModuleEnabled()

    fun getZygiskConfigPath(): String = zygiskConfigGateway.getZygiskConfigPath()

    fun getZygiskConfig(): ZygiskConfig = zygiskConfigGateway.getZygiskConfig()

    fun getZygiskSpoofPackages(): Set<String> = getZygiskConfig().enabledPackages()

    fun isZygiskSpoofEnabled(packageName: String): Boolean = getZygiskConfig().isEnabledForPackage(packageName)

    fun saveZygiskConfig(config: ZygiskConfig): Boolean = zygiskConfigGateway.saveZygiskConfig(config)

    fun setZygiskSpoofEnabled(packageName: String, enabled: Boolean): Boolean {
        val updated = getZygiskConfig().withPackageEnabled(packageName, enabled)
        val saved = saveZygiskConfig(updated)
        if (saved) {
            zygiskConfigGateway.forceStopApp(packageName)
        }
        return saved
    }
}
