package com.xiaomi.xmsf

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.catchingnow.icebox.sdk_client.IceBox
import com.nihility.Global
import com.nihility.InternalMessenger
import com.nihility.utils.RegistrationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.utils.LogUtils
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipushframework.main.subpage.ApplicationPageOperation
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

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
            Thread {
                Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.start), Toast.LENGTH_SHORT)
                EventDb.deleteHistory()
                Utils.makeText(context, context.getString(R.string.settings_clear_history) + " " + context.getString(R.string.end), Toast.LENGTH_SHORT)
                mClearingHistory.set(false)
            }.start()
        }
    }

    @JvmStatic
    fun startMiPushServiceAsForegroundService(context: Context) {
        InternalMessenger(context).send(Intent(XMPushServiceMessenger.IntentStartForeground))
    }

    @JvmStatic
    fun notifyMockNotification(context: Context) {
        val packageName = BuildConfig.APPLICATION_ID
        val date = Date()
        val title = context.getString(R.string.debug_test_title)
        val description = context.getString(R.string.debug_test_content) + date.toString()
        NotificationController.test(context, packageName, title, description)
    }

    @JvmStatic
    fun isIceBoxInstalled(): Boolean = Utils.isAppInstalled(IceBox.PACKAGE_NAME)

    @JvmStatic
    fun tryForceRegisterAllApplications() {
        val miPushApplications: ApplicationPageOperation.MiPushApplications = ApplicationPageOperation.getMiPushApplications()
        for (registeredApplication: RegisteredApplication in miPushApplications.res) {
            RegistrationHelper.tryForceRegister(registeredApplication.packageName)
        }
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
