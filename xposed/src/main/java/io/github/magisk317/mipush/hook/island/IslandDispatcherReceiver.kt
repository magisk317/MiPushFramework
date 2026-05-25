package io.github.magisk317.mipush.hook.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.magisk317.mipush.hook.XLog

internal object IslandDispatcherReceiver {
    private const val TAG = "IslandDispatcherReceiver"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val appContext = context.applicationContext ?: context
            when (intent.action) {
                IslandDispatchContract.ACTION_SHOW -> {
                    runCatching {
                        IslandDispatcher.post(appContext, IslandRequest.fromIntent(intent))
                    }.onFailure {
                        XLog.e(TAG, "show request failed: ${it.message}", it)
                    }
                }
                IslandDispatchContract.ACTION_CANCEL -> {
                    val notificationId = intent.getIntExtra(
                        IslandDispatchContract.EXTRA_NOTIFICATION_ID,
                        IslandDispatchContract.DEFAULT_NOTIFICATION_ID,
                    )
                    IslandDispatcher.cancel(appContext, notificationId)
                }
            }
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter(IslandDispatchContract.ACTION_SHOW).apply {
            addAction(IslandDispatchContract.ACTION_CANCEL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }
}
