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
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter.ForceRegisterStage
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
    private val configCenter: ConfigCenter,
    private val runtimeSettingsAdapter: RuntimeSettingsAdapter,
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(
        io.github.magisk317.mipush.common.utils.Singleton.instance<ConfigCenter>(),
        io.github.magisk317.mipush.common.utils.Singleton.instance<RuntimeSettingsAdapter>(),
    )

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (t: Throwable) {
            io.github.aakira.napier.Napier.w("Singleton.reset failed for SettingsManager", t, tag = "SettingsManager")
        }
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
        runtimeSettingsAdapter.startMiPushServiceAsForegroundService(context)
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

    fun tryForceRegisterAllApplications(context: Context): String {
        val outcome = runtimeSettingsAdapter.tryForceRegisterAllApplications(
            context = context,
            applications = ApplicationPageOperation.getMiPushApplications().res,
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

    fun sendXMPPReconnectRequest(context: Context) {
        runtimeSettingsAdapter.sendXmppReconnectRequest(context)
    }

    fun setXMPPServer(context: Context, newHost: String) {
        runtimeSettingsAdapter.setXmppServer(context, newHost)
    }

    fun getXMPPServerHint(): String {
        return runtimeSettingsAdapter.getXmppServerHint()
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
