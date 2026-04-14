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
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.platform.support.InternalMessenger
import io.github.magisk317.mipush.utils.RegistrationHelper
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.smack.ConnectionConfiguration
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Singleton
class SettingsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val configCenter: ConfigCenter
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(
        io.github.magisk317.mipush.common.utils.Utils.getApplication()!!,
        io.github.magisk317.mipush.common.utils.Singleton.instance<ConfigCenter>()
    )

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
    }

    val mClearingHistory: AtomicBoolean = AtomicBoolean(false)

    fun clearLog(context: Context) {
        Toast.makeText(context, context.getString(R.string.settings_clear_log) + " " + context.getString(R.string.start), Toast.LENGTH_SHORT).show()
        LogUtils.clearLog(context)
        Toast.makeText(context, context.getString(R.string.settings_clear_log) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT).show()
    }

    fun clearHistory(context: Context, scope: CoroutineScope) {
        if (mClearingHistory.compareAndSet(false, true)) {
            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.start), Toast.LENGTH_SHORT)
                }
                EventDb.deleteHistoryAsync()
                withContext(Dispatchers.Main) {
                    Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT)
                }
                mClearingHistory.set(false)
            }
        }
    }

    fun startMiPushServiceAsForegroundService(context: Context) {
        InternalMessenger(context).send(Intent(XMPushServiceMessenger.IntentStartForeground))
    }

    fun notifyMockNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (context is Activity) {
                    ActivityCompat.requestPermissions(context, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                } else {
                    Toast.makeText(context, context.getString(R.string.permission_notifications_denied), Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        val packageName = BuildConfig.APPLICATION_ID
        val date = Date()
        val title = context.getString(R.string.debug_test_title)
        val description = context.getString(R.string.debug_test_content) + date.toString()
        NotificationController.test(context, packageName, title, description)
        runCatching {
            val type = NotificationType("mock:$title", packageName, null).apply {
                this.type = Event.Type.SendMessage
            }
            runBlocking { EventDb.insertEventAsync(Event.ResultType.OK, type) }
        }
    }

    fun tryForceRegisterAllApplications(context: Context) {
        var successCount = 0
        var failedCount = 0
        var unsupportedCount = 0
        val unsupportedReasons = linkedMapOf<String, Int>()
        val unsupportedSamples = linkedMapOf<String, MutableList<String>>()
        val failureTypes = linkedMapOf<String, Int>()
        fun logSnapshot(stage: String) {
            val unsupportedSummary = unsupportedReasons.entries.joinToString(",") { "${it.key}:${it.value}" }
            val sampleSummary = unsupportedSamples.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }
            val failureSummary = failureTypes.entries.joinToString(",") { "${it.key}:${it.value}" }
            PushHealthSnapshotLogger.log(
                context,
                "SettingsManager.tryForceRegisterAllApplications",
                "stage=$stage success=$successCount failed=$failedCount unsupported=$unsupportedCount unsupportedReasons=$unsupportedSummary unsupportedSamples=$sampleSummary failureTypes=$failureSummary"
            )
        }

        val uid = runCatching { Shell.cmd("id -u").exec().out.firstOrNull()?.trim() }.getOrNull()
        if (uid != "0") {
            Toast.makeText(context, R.string.force_register_requires_root, Toast.LENGTH_LONG).show()
            logSnapshot("root_missing")
            return
        }

        val miPushApplications: ApplicationPageOperation.MiPushApplications = ApplicationPageOperation.getMiPushApplications()
        for (registeredApplication: RegisteredApplication in miPushApplications.res) {
            val packageName = registeredApplication.packageName
            val plan = RegistrationHelper.inspectForceRegisterPlan(packageName)
            if (!plan.supportsServiceDispatch && !plan.supportsReceiverFallback) {
                unsupportedCount++
                unsupportedReasons[plan.reason] = (unsupportedReasons[plan.reason] ?: 0) + 1
                unsupportedSamples.getOrPut(plan.reason) { mutableListOf() }.apply {
                    if (size < 5) add(packageName)
                }
                continue
            }
            try {
                val success = if (plan.supportsServiceDispatch) {
                    RegistrationHelper.tryForceRegister(packageName)
                    true
                } else {
                    RegistrationHelper.tryForceRegisterFallback(packageName)
                }
                if (success) {
                    successCount++
                } else {
                    failedCount++
                    failureTypes["FallbackDispatchFailed"] = (failureTypes["FallbackDispatchFailed"] ?: 0) + 1
                }
            } catch (e: UnsupportedOperationException) {
                unsupportedCount++
                unsupportedReasons[plan.reason] = (unsupportedReasons[plan.reason] ?: 0) + 1
                unsupportedSamples.getOrPut(plan.reason) { mutableListOf() }.apply {
                    if (size < 5) add(packageName)
                }
            } catch (_: NoClassDefFoundError) {
                failedCount++
                failureTypes["NoClassDefFoundError"] = (failureTypes["NoClassDefFoundError"] ?: 0) + 1
            } catch (_: ClassNotFoundException) {
                failedCount++
                failureTypes["ClassNotFoundException"] = (failureTypes["ClassNotFoundException"] ?: 0) + 1
            } catch (t: Throwable) {
                failedCount++
                val key = t::class.java.simpleName.ifBlank { "Throwable" }
                failureTypes[key] = (failureTypes[key] ?: 0) + 1
            }
        }

        if (successCount == 0 && (failedCount > 0 || unsupportedCount > 0)) {
            Toast.makeText(context, R.string.force_register_unavailable, Toast.LENGTH_LONG).show()
            logSnapshot("all_failed")
            return
        }

        val resultMessage = if (failedCount == 0 && unsupportedCount == 0) {
            context.getString(R.string.force_register_done, successCount)
        } else {
            context.getString(R.string.force_register_partial, successCount, failedCount + unsupportedCount)
        }
        Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
        logSnapshot("completed")
    }

    fun sendXMPPReconnectRequest(context: Context) {
        InternalMessenger(context).send(Intent(PushConstants.ACTION_RESET_CONNECTION))
    }

    fun setXMPPServer(context: Context, newHost: String) {
        runBlocking { configCenter.setXMPPServerAsync(newHost) }
        NetworkPolicyCompat.applyXmppHostOverride(context.applicationContext)
        sendXMPPReconnectRequest(context)
    }

    fun getXMPPServerHint(): String {
        return ConnectionConfiguration.getXmppServerHost() + ":" + PushServiceConstants.XMPP_SERVER_PORT
    }

    fun getXMPPServer(context: Context): String? = runBlocking { configCenter.getXMPPServerAsync() }

    fun getConfigurationDirectory(context: Context): Uri? = runBlocking { configCenter.getConfigurationDirectoryAsync() }

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
        runBlocking { configCenter.setConfigurationDirectoryAsync(uri) }
        configCenter.loadConfigurations(context)
    }
}
