package com.xiaomi.xmsf.push.control

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
import com.oasisfeng.condom.CondomContext
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.xmsf.FirstRegister
import com.xiaomi.xmsf.RetryRegister
import com.magisk317.service.PushServiceStarter
import com.xiaomi.xmsf.push.service.receivers.BootReceiver
import com.xiaomi.xmsf.push.service.receivers.KeepAliveReceiver
import java.util.Objects
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.Constants.TAG_CONDOM

@SuppressLint("WrongConstant")
object PushControllerUtils {
    private val TAG = "PushControllerUtils"
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun w(msg: String) = Napier.w(msg, tag = TAG)
        fun e(msg: String) = Napier.e(msg, tag = TAG)
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = TAG)
        fun e(t: Throwable) = Napier.e(t.message ?: "Unknown error", t, tag = TAG)
    }
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
            logger.d("Starting...")
            if (isAppMainProc(context)) {
                runCatching {
                    val wrappedContext = wrapContext(context)
                    ScheduledJobManager.getInstance(wrappedContext)
                        .addOneShootJob(FirstRegister(wrappedContext))
                }.onFailure {
                    logger.e("ScheduledJobManager unavailable, skip FirstRegister scheduling", it)
                }
            }
            try {
                resolveClass("com.xiaomi.push.service.XMPushService")?.let { serviceClass ->
                    val serviceIntent = Intent(context, serviceClass)
                    serviceIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, System.currentTimeMillis())
                    serviceIntent.action = PushServiceConstants.ACTION_TIMER
                    PushServiceStarter.start(context, serviceIntent)
                } ?: logger.w("XMPushService class is unavailable, skip startForegroundService")
            } catch (e: Throwable) {
                logger.e(e)
            }
            try {
                val filter = IntentFilter()
                filter.addAction(Intent.ACTION_SCREEN_ON)
                context.registerReceiver(liveReceiver, filter)
            } catch (e: Throwable) {
                logger.e(e)
            }
        } else {
            logger.d("Stopping...")
            try {
                context.unregisterReceiver(liveReceiver)
            } catch (e: Throwable) {
                logger.e(e)
            }
            MiPushClient.unregisterPush(wrapContext(context))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java),
            if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    @JvmStatic
    fun wrapContext(context: Context): Context =
        CondomContext.wrap(context, TAG_CONDOM, XMOutbound.create(context, TAG_CONDOM))

    private fun resolveClass(className: String): Class<*>? =
        runCatching { Class.forName(className) }.getOrNull()
}
