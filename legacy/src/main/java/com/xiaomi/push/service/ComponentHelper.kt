package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xiaomi.channel.commonutils.logger.MyLog

object ComponentHelper {
    @JvmStatic
    fun checkActivity(context: Context, componentName: ComponentName): Boolean {
        return try {
            Intent().setComponent(componentName)
            context.packageManager.getActivityInfo(componentName, 128)
            true
        } catch (e: Exception) {
            MyLog.w("checkActivity componentName: $componentName, $e")
            false
        }
    }

    @JvmStatic
    fun checkActivity(context: Context, packageName: String, action: String): Boolean {
        return try {
            val intent = Intent(action).setPackage(packageName)
            context.packageManager.resolveActivity(intent, 65536) != null
        } catch (e: Exception) {
            MyLog.w("checkActivity action: $action, $e")
            false
        }
    }

    @JvmStatic
    fun checkProvider(context: Context, authority: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < 19) {
                return true
            }
            context.packageManager
                .queryContentProviders(null, 0, 8)
                .any { provider ->
                    provider.enabled && provider.exported && provider.authority == authority
                } == true
        } catch (e: Exception) {
            MyLog.w("checkProvider $e")
            false
        }
    }

    @JvmStatic
    fun checkService(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager
                .getPackageInfo(packageName, PackageManager.GET_SERVICES)
                .services
                ?.any { serviceInfo ->
                    serviceInfo.exported &&
                        serviceInfo.enabled &&
                        serviceInfo.name == "com.xiaomi.mipush.sdk.PushMessageHandler" &&
                        context.packageName != serviceInfo.packageName
                } == true
        } catch (e: PackageManager.NameNotFoundException) {
            MyLog.w("checkService $e")
            false
        }
    }

    @JvmStatic
    fun checkService(context: Context, packageName: String, action: String): Boolean {
        return try {
            val intent = Intent(action).setPackage(packageName)
            !context.packageManager.queryIntentServices(intent, 32).isNullOrEmpty()
        } catch (e: Exception) {
            MyLog.w("checkService action: $action, $e")
            false
        }
    }
}
