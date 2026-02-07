package com.xiaomi.xmsf.push.control

import android.content.Context
import android.content.Intent
import androidx.annotation.Nullable
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.oasisfeng.condom.CondomOptions
import com.oasisfeng.condom.OutboundJudge
import com.oasisfeng.condom.OutboundType
import com.oasisfeng.condom.kit.NullDeviceIdKit

class XMOutbound private constructor(
    private val context: Context,
    tag: String
) : OutboundJudge {
    private val logger: Logger = XLog.tag(tag).build()

    override fun shouldAllow(type: OutboundType, @Nullable intent: Intent?, target_package: String): Boolean {
        logger.d("shouldAllow ->" + type.toString())
        return true
    }

    companion object {
        @JvmStatic
        fun create(context: Context, tag: String, enableKit: Boolean): CondomOptions {
            val options = CondomOptions()
                .preventBroadcastToBackgroundPackages(false)
                .setOutboundJudge(XMOutbound(context, tag))
            if (enableKit) {
                options.addKit(NullDeviceIdKit())
                    .addKit(AppOpsKit())
                    .addKit(NotificationManagerKit())
            }
            return options
        }

        @JvmStatic
        fun create(context: Context, tag: String): CondomOptions {
            return create(context, tag, true)
        }
    }
}
