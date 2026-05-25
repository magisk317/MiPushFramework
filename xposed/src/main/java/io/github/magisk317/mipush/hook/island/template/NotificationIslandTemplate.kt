package io.github.magisk317.mipush.hook.island.template

import android.app.Notification
import android.content.Context
import io.github.magisk317.mipush.hook.island.IslandPayloadBuilder

object NotificationIslandTemplate : IslandTemplate {
    override fun inject(context: Context, notification: Notification, data: NotifData) {
        val viewModel = data.toViewModel()
        notification.extras.putAll(
            IslandPayloadBuilder.buildExtras(
                context = context,
                title = viewModel.title,
                content = viewModel.content,
                icon = viewModel.icon,
                timeoutSecs = viewModel.timeoutSecs,
                firstFloat = viewModel.firstFloat,
                enableFloat = viewModel.enableFloat,
                showNotification = viewModel.showNotification,
                sourcePackage = data.packageName,
                sourceChannelId = data.channelId,
                actions = viewModel.actions,
            ),
        )
    }

    private fun NotifData.toViewModel(): IslandViewModel =
        IslandViewModel(
            title = title,
            content = content,
            icon = icon,
            actions = actions,
            timeoutSecs = DEFAULT_TIMEOUT_SECS,
            firstFloat = true,
            enableFloat = true,
            showNotification = true,
        )

    private const val DEFAULT_TIMEOUT_SECS = 5
}
