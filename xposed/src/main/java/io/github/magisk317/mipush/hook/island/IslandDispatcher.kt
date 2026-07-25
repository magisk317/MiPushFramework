package io.github.magisk317.mipush.hook.island

import android.content.Context
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.logging.MagiskOtel

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
        val startedAt = System.nanoTime()
        // showNotification belongs to the focus payload and controls shade visibility. The
        // notification still has to reach SystemUI when false so the island itself can render.
        runCatching {
            IslandDispatcherNotifier.post(context.applicationContext ?: context, request)
            IslandDispatchState.markPosted(request.notificationId)
        }.fold(
            onSuccess = {
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                MagiskOtel.event(
                    name = "push.island",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to durationMs.toString(),
                        "process" to "hook",
                        "reason" to "post",
                    ),
                    statusOk = true,
                )
            },
            onFailure = { error ->
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                MagiskOtel.event(
                    name = "push.island",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to durationMs.toString(),
                        "process" to "hook",
                        "reason" to error.javaClass.simpleName,
                    ),
                    statusOk = false,
                )
                throw error
            },
        )
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
