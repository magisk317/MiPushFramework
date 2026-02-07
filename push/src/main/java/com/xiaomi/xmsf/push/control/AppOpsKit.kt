package com.xiaomi.xmsf.push.control

import android.app.AppOpsManager
import android.app.AppOpsManagerExtender
import android.content.Context
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.oasisfeng.condom.CondomKit
import top.trumeet.common.override.AppOpsManagerOverride
import top.trumeet.common.override.ManifestOverride

class AppOpsKit : CondomKit, CondomKit.SystemServiceSupplier {
    private val logger: Logger = XLog.tag("AppOpsKit").build()

    override fun onRegister(registry: CondomKit.CondomKitRegistry) {
        registry.addPermissionSpoof(ManifestOverride.permission.GET_APP_OPS_STATS)
        registry.registerSystemService(Context.APP_OPS_SERVICE, this)
    }

    override fun getSystemService(context: Context, name: String): Any? {
        return if (Context.APP_OPS_SERVICE == name) CondomAppOpsManager(context) else null
    }

    inner class CondomAppOpsManager(context: Context) : AppOpsManagerExtender(context) {
        override fun checkOpNoThrow(op: Int, uid: Int, packageName: String): Int {
            if (op == AppOpsManagerOverride.OP_POST_NOTIFICATION) {
                logger.d("check post notification op: $uid, $packageName")
                return AppOpsManager.MODE_ALLOWED
            }
            return super.checkOpNoThrow(op, uid, packageName)
        }
    }
}
