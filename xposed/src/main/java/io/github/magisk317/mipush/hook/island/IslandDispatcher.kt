package io.github.magisk317.mipush.hook.island

import android.content.Context
import io.github.magisk317.mipush.hook.XLog

object IslandDispatcher {
    const val CHANNEL_ID = IslandDispatchContract.CHANNEL_ID
    const val DEFAULT_NOTIFICATION_ID = IslandDispatchContract.DEFAULT_NOTIFICATION_ID

    fun register(context: Context) {
        if (IslandDispatchState.registered) return
        synchronized(IslandDispatchState) {
            if (IslandDispatchState.registered) return
            val appContext = context.applicationContext ?: context
            IslandDispatcherNotifier.ensureChannel(appContext)
            IslandDispatcherReceiver.register(appContext)
            IslandDispatchState.registered = true
            XLog.i(TAG, "registered island dispatcher receiver")
        }
    }

    fun post(context: Context, request: IslandRequest) {
        IslandDispatcherNotifier.post(context.applicationContext ?: context, request)
        IslandDispatchState.markPosted(request.notificationId)
    }

    fun cancel(context: Context, notificationId: Int = DEFAULT_NOTIFICATION_ID) {
        IslandDispatcherNotifier.cancel(context.applicationContext ?: context, notificationId)
        IslandDispatchState.markCancelled(notificationId)
    }

    fun sendBroadcast(context: Context, request: IslandRequest) {
        IslandDispatcherBroadcaster.show(context, request)
    }

    fun cancelBroadcast(
        context: Context,
        notificationId: Int = DEFAULT_NOTIFICATION_ID
    ) {
        IslandDispatcherBroadcaster.cancel(context, notificationId)
    }

    fun postedIdsForTest(): Set<Int> = IslandDispatchState.postedIds()

    private const val TAG = "IslandDispatcher"
}
