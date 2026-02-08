@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.push.sdk

import android.app.IntentService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.catchingnow.icebox.sdk_client.IceBox
import com.elvishew.xlog.XLog
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import java.util.function.Consumer
import top.trumeet.common.Constants
import top.trumeet.common.ita.ITopActivity
import top.trumeet.common.ita.TopActivityFactory
import top.trumeet.common.utils.Utils

class MyPushMessageHandler : IntentService("my mipush message handler") {
    override fun onHandleIntent(intent: Intent?) {
        val safeIntent = intent ?: return
        val payload = safeIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        if (payload == null) {
            logger.e("mipush_payload is null")
            return
        }
        val container = XMPushUtils.packToContainer(payload) ?: return
        MiPushRuntimeBridge.onPayloadFromServer(
            this,
            payload,
            payload.size.toLong(),
            "MyPushMessageHandler.onHandleIntent"
        )
        try {
            if (startService(this, container, payload) != null) {
                cancelNotification(this, safeIntent.extras ?: Bundle(), container)
            }
        } catch (e: Exception) {
            logger.e(e.localizedMessage, e)
        }
    }

    private fun runWithAppStateElevatedToForeground(pkg: String, task: Consumer<Boolean>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            task.accept(false)
            return
        }
        val intent = Intent().setClassName(pkg, MyMIPushNotificationHelper.CLASS_NAME_PUSH_MESSAGE_HANDLER)
        val appContext = applicationContext
        val successful = appContext.bindService(
            intent,
            object : ServiceConnection {
                private fun runTaskAndUnbind() {
                    task.accept(true)
                    appContext.unbindService(this)
                }

                @RequiresApi(Build.VERSION_CODES.P)
                override fun onNullBinding(name: ComponentName) {
                    runTaskAndUnbind()
                }

                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    runTaskAndUnbind()
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            },
            BIND_AUTO_CREATE or BIND_IMPORTANT or BIND_ABOVE_CLIENT
        )
        if (!successful) {
            task.accept(false)
        }
    }

    companion object {
        private val logger = XLog.tag("MyPushMessageHandler").build()

        private const val APP_CHECK_FRONT_MAX_RETRY = 8
        private const val APP_CHECK_SLEEP_DURATION_MS = 500L
        private const val APP_CHECK_SLEEP_MAX_TIMEOUT_MS =
            APP_CHECK_FRONT_MAX_RETRY * APP_CHECK_SLEEP_DURATION_MS

        @JvmStatic
        var iTopActivity: ITopActivity? = null

        @JvmStatic
        fun cancelNotification(context: Context, bundle: Bundle) {
            val payload = bundle.getByteArray(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            if (payload == null) {
                logger.e("mipush_payload is null")
                return
            }
            val container = XMPushUtils.packToContainer(payload) ?: return
            cancelNotification(context, bundle, container)
        }

        @JvmStatic
        fun cancelNotification(context: Context, bundle: Bundle, container: XmPushActionContainer) {
            val notificationId = bundle.getInt(Constants.INTENT_NOTIFICATION_ID, 0)
            val notificationGroup = bundle.getString(Constants.INTENT_NOTIFICATION_GROUP)
            try {
                Configurations.getInstance().handle(container.packageName, container)
            } catch (e: Exception) {
                logger.e("cancelNotification", e)
            }
            val custom = XMPushUtils.getConfiguration(container)
            NotificationController.cancel(
                context,
                container,
                notificationId,
                notificationGroup,
                custom.clearGroup(false)
            )
        }

        @JvmStatic
        fun launchApp(context: Context, container: XmPushActionContainer) {
            if (iTopActivity == null) {
                iTopActivity = TopActivityFactory.newInstance(Global.ConfigCenter().getAccessMode(context))
            }
            val topActivity = iTopActivity ?: return
            if (!topActivity.isEnabled(context)) {
                topActivity.guideToEnable(context)
                return
            }
            val targetPackage = container.packageName
            activeApp(context, targetPackage)
            pullUpApp(context, targetPackage, container)
        }

        @JvmStatic
        fun startService(
            context: Context,
            container: XmPushActionContainer,
            payload: ByteArray
        ): ComponentName? {
            launchApp(context, container)
            return forwardToTargetApplication(context, payload)
        }

        @JvmStatic
        fun forwardToTargetApplication(context: Context, payload: ByteArray): ComponentName? {
            val container = XMPushUtils.packToContainer(payload) ?: return null
            val metaInfo = container.metaInfo ?: return null
            val targetPackage = container.packageName

            MiPushRuntimeBridge.onTransferToApplication(container)
            val localIntent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
            localIntent.component = ComponentName(targetPackage, "com.xiaomi.mipush.sdk.PushMessageHandler")
            localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
            localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true)
            localIntent.addCategory(metaInfo.notifyId.toString())
            logger.d(packageInfo(targetPackage, "send to service"))
            return context.startService(localIntent)
        }

        private fun activeApp(context: Context, targetPackage: String) {
            if (Global.ConfigCenter().isIceboxSupported(context) && Utils.isAppInstalled(IceBox.PACKAGE_NAME)) {
                try {
                    if (ContextCompat.checkSelfPermission(context, IceBox.SDK_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                        val enabledSetting = IceBox.getAppEnabledSetting(context, targetPackage)
                        if (enabledSetting != 0) {
                            logger.w(packageInfo(targetPackage, "active app by IceBox SDK"))
                            IceBox.setAppEnabledSettings(context, true, targetPackage)
                            return
                        }
                    } else {
                        logger.w(packageInfo(targetPackage, "skip active app by IceBox SDK due to lack of permissions"))
                    }
                } catch (e: Throwable) {
                    logger.e(packageInfo(targetPackage, "activeApp failed ${e.localizedMessage}"), e)
                }
            }
            Shell.cmd("pm enable $targetPackage").exec()
        }

        private fun getJumpIntent(context: Context, container: XmPushActionContainer): Intent? {
            var intent = MyMIPushNotificationHelper.getSdkIntent(context, container)
            if (intent == null) {
                intent = getJumpIntentFromPkg(context, container.packageName)
            }
            return intent
        }

        private fun getJumpIntentFromPkg(context: Context, targetPackage: String): Intent? {
            var intent: Intent? = null
            try {
                intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                logger.d(packageInfo(targetPackage, intent.toString()))
            } catch (_: RuntimeException) {
            }
            return intent
        }

        private fun pullUpApp(context: Context, targetPackage: String, container: XmPushActionContainer): Long {
            val start = System.currentTimeMillis()
            try {
                val topActivity = iTopActivity ?: return 0
                if (!topActivity.isAppForeground(context, targetPackage)) {
                    logger.d(packageInfo(targetPackage, "app is not at front , let's pull up"))
                    var intent = getJumpIntent(context, container)
                    if (intent == null) {
                        throw RuntimeException("can not get default activity for $targetPackage")
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        context.startActivity(intent)
                        logger.d(packageInfo(targetPackage, "start activity"))
                    }
                    for (i in 0 until APP_CHECK_FRONT_MAX_RETRY) {
                        if (!topActivity.isAppForeground(context, targetPackage)) {
                            Thread.sleep(APP_CHECK_SLEEP_DURATION_MS)
                        } else {
                            break
                        }
                        if (i == (APP_CHECK_FRONT_MAX_RETRY / 2)) {
                            intent = getJumpIntentFromPkg(context, targetPackage)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            if (intent != null) {
                                context.startActivity(intent)
                            }
                        }
                    }
                    if ((System.currentTimeMillis() - start) >= APP_CHECK_SLEEP_MAX_TIMEOUT_MS) {
                        logger.w(packageInfo(targetPackage, "pull up app timeout"))
                    }
                } else {
                    logger.d(packageInfo(targetPackage, "app is at foreground"))
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: RuntimeException) {
                logger.e(packageInfo(targetPackage, "pullUpApp failed ${e.localizedMessage}"), e)
            }
            val end = System.currentTimeMillis()
            return end - start
        }

        private fun packageInfo(packageName: String, message: String): String = "[$packageName] $message"
    }
}
