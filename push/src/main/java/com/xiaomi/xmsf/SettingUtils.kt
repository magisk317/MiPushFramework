package com.xiaomi.xmsf

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
import com.catchingnow.icebox.sdk_client.IceBox
import com.magisk317.Global
import com.magisk317.InternalMessenger
import com.magisk317.utils.RegistrationHelper
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.utils.LogUtils
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.type.NotificationType
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipushframework.main.subpage.ApplicationPageOperation
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.magisk317.data.DataStoreManager

object SettingUtils {
    const val requestIceBoxCode: Int = 0x233
    @JvmField
    val mClearingHistory: AtomicBoolean = AtomicBoolean(false)

    @JvmStatic
    fun requestIceBoxPermission(activity: Activity) {
        ActivityCompat.requestPermissions(activity, arrayOf(IceBox.SDK_PERMISSION), requestIceBoxCode)
    }

    @JvmStatic
    fun iceBoxPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, IceBox.SDK_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun clearLog(context: Context) {
        Toast.makeText(context, context.getString(R.string.settings_clear_log) + " " + context.getString(R.string.start), Toast.LENGTH_SHORT).show()
        LogUtils.clearLog(context)
        Toast.makeText(context, context.getString(R.string.settings_clear_log) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun clearHistory(context: Context) {
        if (mClearingHistory.compareAndSet(false, true)) {
            MiPushFrameworkApp.applicationScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.start), Toast.LENGTH_SHORT)
                }
                EventDb.deleteHistory()
                withContext(Dispatchers.Main) {
                    Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT)
                }
                mClearingHistory.set(false)
            }
        }
    }

    @JvmStatic
    fun startMiPushServiceAsForegroundService(context: Context) {
        InternalMessenger(context).send(Intent(XMPushServiceMessenger.IntentStartForeground))
    }

    @JvmStatic
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
            EventDb.insertEvent(
                Event.ResultType.OK,
                type
            )
        }
    }

    @JvmStatic
    fun isIceBoxInstalled(): Boolean = Utils.isAppInstalled(IceBox.PACKAGE_NAME)

    @JvmStatic
    fun tryForceRegisterAllApplications(context: Context) {
        val uid = runCatching { Shell.cmd("id -u").exec().out.firstOrNull()?.trim() }.getOrNull()
        if (uid != "0") {
            Toast.makeText(context, R.string.force_register_requires_root, Toast.LENGTH_LONG).show()
            return
        }

        val miPushApplications: ApplicationPageOperation.MiPushApplications = ApplicationPageOperation.getMiPushApplications()
        var successCount = 0
        var failedCount = 0
        for (registeredApplication: RegisteredApplication in miPushApplications.res) {
            try {
                RegistrationHelper.tryForceRegister(registeredApplication.packageName)
                successCount++
            } catch (_: NoClassDefFoundError) {
                failedCount++
            } catch (_: ClassNotFoundException) {
                failedCount++
            } catch (_: Throwable) {
                failedCount++
            }
        }

        if (successCount == 0 && failedCount > 0) {
            Toast.makeText(context, R.string.force_register_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        val resultMessage = if (failedCount == 0) {
            context.getString(R.string.force_register_done, successCount)
        } else {
            context.getString(R.string.force_register_partial, successCount, failedCount)
        }
        Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    fun sendXMPPReconnectRequest(context: Context) {
        InternalMessenger(context).send(Intent(PushConstants.ACTION_RESET_CONNECTION))
    }

    @JvmStatic
    fun setXMPPServer(context: Context, newHost: String) {
        Global.ConfigCenter().setXMPPServer(context, newHost)
    }

    @JvmStatic
    fun getXMPPServerHint(): String {
        return ConnectionConfiguration.getXmppServerHost() + ":" + PushServiceConstants.XMPP_SERVER_PORT
    }

    @JvmStatic
    fun getXMPPServer(context: Context): String? = Global.ConfigCenter().getXMPPServer(context)

    @JvmStatic
    fun getConfigurationDirectory(context: Context): Uri? = Global.ConfigCenter().getConfigurationDirectory(context)

    @JvmStatic
    fun shareLogs(context: Context) {
        context.startActivity(
            Intent().setComponent(
                ComponentName(Constants.SERVICE_APP_NAME, Constants.SHARE_LOG_COMPONENT_NAME)
            )
        )
    }

    @JvmStatic
    fun saveConfigurationUri(context: Context, data: Intent): Uri {
        val uri = data.data!!
        setConfigurationDirectory(context, uri)
        return uri
    }

    @JvmStatic
    fun setConfigurationDirectory(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        Global.ConfigCenter().setConfigurationDirectory(context, uri)
        Global.ConfigCenter().loadConfigurations(context)
    }
}
