package io.github.magisk317.mipush.hook.island

import android.content.Context
import android.content.Intent

object IslandDispatcherBroadcaster {
    fun show(context: Context, request: IslandRequest) {
        context.sendBroadcast(
            Intent(IslandDispatchContract.ACTION_SHOW).apply {
                putExtras(request.toBundle())
                setPackage(IslandDispatchContract.SYSTEM_UI_PACKAGE)
            },
        )
    }

    fun cancel(
        context: Context,
        notificationId: Int = IslandDispatchContract.DEFAULT_NOTIFICATION_ID
    ) {
        context.sendBroadcast(
            Intent(IslandDispatchContract.ACTION_CANCEL).apply {
                putExtra(IslandDispatchContract.EXTRA_NOTIFICATION_ID, notificationId)
                setPackage(IslandDispatchContract.SYSTEM_UI_PACKAGE)
            },
        )
    }
}
