package com.xiaomi.xmsf.push.control

import android.content.Context
import com.oasisfeng.condom.CondomKit

class NotificationManagerKit : CondomKit {
    override fun onRegister(registry: CondomKit.CondomKitRegistry) {
        registry.registerSystemService(
            Context.NOTIFICATION_SERVICE,
            object : CondomKit.SystemServiceSupplier {
                override fun getSystemService(context: Context, name: String): Any? {
                    return null
                }
            }
        )
    }
}
