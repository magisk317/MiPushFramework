package io.github.magisk317.mipush.hook.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.magisk317.mipush.common.ISLAND_PREF_READ_PERMISSION
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.logging.MagiskOtel

internal object IslandDispatcherReceiver {
    private const val TAG = "IslandDispatcherReceiver"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val startedAt = System.nanoTime()
            val appContext = context.applicationContext ?: context
            when (intent.action) {
                IslandDispatchContract.ACTION_SHOW -> {
                    runCatching {
                        IslandDispatcher.post(appContext, IslandRequest.fromIntent(intent))
                    }.fold(
                        onSuccess = {
                            emitIsland(startedAt, result = "ok", reason = "show")
                        },
                        onFailure = {
                            XLog.e(TAG, "show request failed: ${it.message}", it)
                            emitIsland(
                                startedAt,
                                result = "error",
                                statusOk = false,
                                reason = it.javaClass.simpleName,
                            )
                        },
                    )
                }
                IslandDispatchContract.ACTION_CANCEL -> {
                    val notificationId = intent.getIntExtra(
                        IslandDispatchContract.EXTRA_NOTIFICATION_ID,
                        IslandDispatchContract.DEFAULT_NOTIFICATION_ID,
                    )
                    val userId = intent.getIntExtra(IslandDispatchContract.EXTRA_USER_ID, -1)
                    runCatching {
                        IslandDispatcher.cancel(appContext, notificationId, userId)
                    }.fold(
                        onSuccess = {
                            emitIsland(startedAt, result = "ok", reason = "cancel")
                        },
                        onFailure = {
                            emitIsland(
                                startedAt,
                                result = "error",
                                statusOk = false,
                                reason = it.javaClass.simpleName,
                            )
                        },
                    )
                }
                else -> emitIsland(startedAt, result = "skip", reason = "unhandled_action")
            }
        }

        private fun emitIsland(
            startedAt: Long,
            result: String,
            statusOk: Boolean = true,
            reason: String? = null,
        ) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "hook",
                "stage" to "broadcast_receive",
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "push.island", attributes = attrs, statusOk = statusOk)
        }
    }

    @Volatile
    private var registeredContext: Context? = null

    fun register(context: Context) {
        if (registeredContext != null) return
        val filter = IntentFilter(IslandDispatchContract.ACTION_SHOW).apply {
            addAction(IslandDispatchContract.ACTION_CANCEL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                filter,
                REQUIRED_SENDER_PERMISSION,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter, REQUIRED_SENDER_PERMISSION, null)
        }
        registeredContext = context
    }

    /** Drop the receiver registered by the old module ClassLoader before hot reload. */
    fun unregister() {
        val context = registeredContext ?: return
        registeredContext = null
        runCatching { context.unregisterReceiver(receiver) }
    }

    internal const val REQUIRED_SENDER_PERMISSION = ISLAND_PREF_READ_PERMISSION
}
