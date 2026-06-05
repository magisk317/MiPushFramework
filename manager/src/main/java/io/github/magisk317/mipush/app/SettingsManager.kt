package io.github.magisk317.mipush.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.manager.ForceRegisterStage
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.manager.ManagerConfigGateway
import io.github.magisk317.mipush.common.manager.ManagerLogClearResult
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.common.manager.ManagerRuntimeActions
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
    private val applicationGateway: ManagerApplicationGateway,
    private val logGateway: ManagerLogGateway,
) {
    companion object {
        private const val TAG = "SettingsManager"
        private const val MOCK_NOTIFICATION_SOURCE = "SettingsManager.notifyMockNotification"
    }

    // No-arg fallback resolving gateways through Koin (ManagerGatewayAccess).
    constructor() : this(
        io.github.magisk317.mipush.app.di.ManagerGatewayAccess.get<ManagerConfigGateway>(),
        io.github.magisk317.mipush.app.di.ManagerGatewayAccess.get<ManagerRuntimeActions>(),
        io.github.magisk317.mipush.app.di.ManagerGatewayAccess.get<ManagerApplicationGateway>(),
        io.github.magisk317.mipush.app.di.ManagerGatewayAccess.get<ManagerLogGateway>(),
    )

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

    fun notifyMockNotification(context: Context) {
        notifyMockNotification(context, io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.BIG_TEXT, Constants.SERVICE_APP_NAME)
    }

    fun notifyMockNotification(
        context: Context,
        kind: io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind,
        packageName: String
    ) {
        Napier.i("mock test request kind=${kind.name} pkg=$packageName", tag = TAG)
        runtimeActions.observeNotificationEvent(packageName, "mock_test_request", MOCK_NOTIFICATION_SOURCE)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Napier.w("mock test blocked by POST_NOTIFICATIONS permission kind=${kind.name} pkg=$packageName", tag = TAG)
                runtimeActions.observeNotificationEvent(packageName, "mock_test_permission_missing", MOCK_NOTIFICATION_SOURCE)
                if (context is Activity) {
                    ActivityCompat.requestPermissions(context, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                } else {
                    Toast.makeText(context, context.getString(R.string.permission_notifications_denied), Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        runtimeActions.notifyMockNotification(context, kind, packageName)
        Napier.i("mock test dispatched kind=${kind.name} pkg=$packageName", tag = TAG)
        runtimeActions.observeNotificationEvent(packageName, "mock_test_dispatched", MOCK_NOTIFICATION_SOURCE)
    }

    fun tryForceRegisterAllApplications(context: Context): String {
        val outcome = runtimeActions.tryForceRegisterAllApplications(
            context = context,
            packageNames = applicationGateway.loadApplications(context).items.map { it.packageName },
        )
        if (outcome.stage == ForceRegisterStage.ROOT_MISSING) {
            return context.getString(R.string.force_register_requires_root)
        }
        if (outcome.stage == ForceRegisterStage.ALL_FAILED) {
            return context.getString(R.string.force_register_unavailable)
        }
        return if (outcome.nonSuccessCount == 0) {
            context.getString(R.string.force_register_done, outcome.successCount)
        } else {
            context.getString(R.string.force_register_partial, outcome.successCount, outcome.nonSuccessCount)
        }
    }

    fun updateAllNotificationOnRegister(enabled: Boolean): Int {
        return applicationGateway.updateAllNotificationOnRegister(enabled)
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
}
