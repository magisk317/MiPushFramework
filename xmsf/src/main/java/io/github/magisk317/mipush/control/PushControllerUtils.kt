package io.github.magisk317.mipush.control

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.TextUtils
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.push.service.PushServiceConstants
import io.github.magisk317.mipush.app.FirstRegister
import io.github.magisk317.mipush.app.RetryRegister
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.receiver.BootReceiver
import io.github.magisk317.mipush.receiver.KeepAliveReceiver
import java.util.Objects
import io.github.magisk317.mipush.common.Constants

@SuppressLint("WrongConstant")
object PushControllerUtils {
    private val TAG = "PushControllerUtils"
    private val liveReceiver: BroadcastReceiver = KeepAliveReceiver()
    private val retryInterval = intArrayOf(3600000, 7200000, 14400000, 28800000, 86400000)

    @JvmStatic
    fun registerPush(context: Context, i: Int) {
        Objects.requireNonNull(context)
        val length = retryInterval.size
        val intervalMs = if (i < length) retryInterval[i] else retryInterval[length - 1]
        MyLog.i("for make sure xmsf register push succ, schedule register after ${intervalMs / 1000} sec")
        Handler(Looper.getMainLooper()).postDelayed(RetryRegister(context, i), intervalMs.toLong())
    }

    @JvmStatic
    fun pushRegistered(context: Context): Boolean = !TextUtils.isEmpty(MiPushClient.getRegId(context))

    private fun getPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val prefName = appContext.packageName + "_preferences"
        return appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun isPrefsEnable(context: Context): Boolean = getPrefs(context).getBoolean(Constants.KEY_ENABLE_PUSH, true)

    @JvmStatic
    fun setPrefsEnable(value: Boolean, context: Context) {
        getPrefs(context).edit().putBoolean(Constants.KEY_ENABLE_PUSH, value).apply()
    }

    @JvmStatic
    fun isAppMainProc(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        for (runningAppProcessInfo in runningProcesses) {
            if (runningAppProcessInfo.pid == Process.myPid() &&
                runningAppProcessInfo.processName == context.packageName
            ) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun setServiceEnable(enable: Boolean, context: Context) {
        if (enable) {
            logD("Starting...")
            if (isAppMainProc(context)) {
                runCatching {
                    val wrappedContext = wrapContext(context)
                    ScheduledJobManager.getInstance(wrappedContext)
                        .addOneShootJob(FirstRegister(wrappedContext))
                }.onFailure {
                    logE("ScheduledJobManager unavailable, skip FirstRegister scheduling", it)
                }
            }
            try {
                resolveClass("com.xiaomi.push.service.XMPushService")?.let { serviceClass ->
                    val serviceIntent = Intent(context, serviceClass)
                    serviceIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, System.currentTimeMillis())
                    serviceIntent.action = PushServiceConstants.ACTION_TIMER
                    PushServiceStarter.start(context, serviceIntent)
                } ?: logW("XMPushService class is unavailable, skip startForegroundService")
            } catch (e: Throwable) {
                logE(e)
            }
            try {
                val filter = IntentFilter()
                filter.addAction(Intent.ACTION_SCREEN_ON)
                context.registerReceiver(liveReceiver, filter)
            } catch (e: Throwable) {
                logE(e)
            }
        } else {
            logD("Stopping...")
            try {
                context.unregisterReceiver(liveReceiver)
            } catch (e: Throwable) {
                logE(e)
            }
            MiPushClient.unregisterPush(wrapContext(context))
            run {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                scheduler?.cancelAll()
            }
            resolveClass("com.xiaomi.push.service.XMPushService")?.let { serviceClass ->
                context.stopService(Intent(context, serviceClass))
            }
        }
    }

    @JvmStatic
    fun setAllEnable(enable: Boolean, context: Context) {
        setPrefsEnable(enable, context)
        setServiceEnable(enable, context)
        setBootReceiverEnable(enable, context)
    }

    @SuppressLint("WrongConstant")
    private fun setBootReceiverEnable(enable: Boolean, context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootReceiver::class.java),
                if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: IllegalArgumentException) {
            // Component may not exist in the host package (e.g. when injected via LSPosed
            // into com.xiaomi.xmsf which has a different manifest).
            logW("setBootReceiverEnable failed: ${e.message}")
        }
    }

    @JvmStatic
    fun wrapContext(context: Context): Context = context

    private fun resolveClass(className: String): Class<*>? =
        runCatching { Class.forName(className) }.getOrNull()
}
