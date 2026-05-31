package io.github.magisk317.mipush.hook.island.template

import android.app.Notification
import android.content.Context
import io.github.magisk317.mipush.common.NotificationClassifier
import io.github.magisk317.mipush.hook.island.IslandPayloadBuilder
import io.github.magisk317.mipush.hook.island.IslandOptions

object NotificationIslandTemplate : IslandTemplate {
    override fun inject(context: Context, notification: Notification, data: NotifData) {
        inject(context, notification, data, IslandOptions())
    }

    fun inject(
        context: Context,
        notification: Notification,
        data: NotifData,
        options: IslandOptions,
    ) {
        if (!options.canInjectFocusPayload) return
        val viewModel = data.toViewModel(options)
        val style = NotificationClassifier.classify(viewModel.title, viewModel.content, data.packageName)
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
                style = style,
            ),
        )
    }

    private fun NotifData.toViewModel(options: IslandOptions): IslandViewModel =
        IslandViewModel(
            title = title,
            content = content,
            icon = icon,
            actions = actions,
            timeoutSecs = options.timeoutSecs,
            firstFloat = options.firstFloat,
            enableFloat = options.enableFloat,
            showNotification = options.showNotification,
        )
}
