package io.github.magisk317.mipush.hook.island

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.xposed.logging.MagiskOtel

object IslandDispatcherBroadcaster {
    fun show(context: Context, request: IslandRequest) {
        context.sendBroadcast(
            Intent(IslandDispatchContract.ACTION_SHOW).apply {
                putExtras(request.toBundle())
                setPackage(IslandDispatchContract.SYSTEM_UI_PACKAGE)
            },
        )
        MagiskOtel.event(
            name = "push.island",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "broadcast_show",
                "reason" to "show_sent",
                "target_package" to IslandDispatchContract.SYSTEM_UI_PACKAGE,
            ),
            statusOk = true,
        )
    }

    fun cancel(
        context: Context,
        notificationId: Int = IslandDispatchContract.DEFAULT_NOTIFICATION_ID,
        userId: Int = Utils.requireValidUserId(Utils.myUserId()),
    ) {
        context.sendBroadcast(
            Intent(IslandDispatchContract.ACTION_CANCEL).apply {
                putExtra(IslandDispatchContract.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(
                    IslandDispatchContract.EXTRA_USER_ID,
                    Utils.requireValidUserId(userId),
                )
                setPackage(IslandDispatchContract.SYSTEM_UI_PACKAGE)
            },
        )
        MagiskOtel.event(
            name = "push.island",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "broadcast_cancel",
                "reason" to "cancel_sent",
                "target_package" to IslandDispatchContract.SYSTEM_UI_PACKAGE,
            ),
            statusOk = true,
        )
    }
}
