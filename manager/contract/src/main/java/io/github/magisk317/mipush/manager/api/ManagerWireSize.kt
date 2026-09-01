package io.github.magisk317.mipush.manager.api

import io.github.magisk317.mipush.manager.ApplicationPageWireSizeInput as CoreApplicationPageWireSizeInput
import io.github.magisk317.mipush.manager.ApplicationSummaryWireSizeInput as CoreApplicationSummaryWireSizeInput
import io.github.magisk317.mipush.manager.EventSummaryWireSizeInput as CoreEventSummaryWireSizeInput
import io.github.magisk317.mipush.manager.ManagerWireSizeCore
import io.github.magisk317.mipush.manager.NotificationChannelGroupWireSizeInput as CoreNotificationChannelGroupWireSizeInput
import io.github.magisk317.mipush.manager.NotificationChannelPageWireSizeInput as CoreNotificationChannelPageWireSizeInput
import io.github.magisk317.mipush.manager.NotificationChannelSummaryWireSizeInput as CoreNotificationChannelSummaryWireSizeInput

internal typealias ApplicationSummaryWireSizeInput = CoreApplicationSummaryWireSizeInput
internal typealias ApplicationPageWireSizeInput = CoreApplicationPageWireSizeInput
internal typealias EventSummaryWireSizeInput = CoreEventSummaryWireSizeInput
internal typealias NotificationChannelSummaryWireSizeInput = CoreNotificationChannelSummaryWireSizeInput
internal typealias NotificationChannelGroupWireSizeInput = CoreNotificationChannelGroupWireSizeInput
internal typealias NotificationChannelPageWireSizeInput = CoreNotificationChannelPageWireSizeInput

/** Android contract facade for the platform-neutral wire-size policy. */
internal object ManagerWireSize {
    fun stringBytes(value: String?): Long = ManagerWireSizeCore.stringBytes(value)

    fun estimateApplicationPage(input: ApplicationPageWireSizeInput): Long =
        ManagerWireSizeCore.estimateApplicationPage(input)

    fun estimateEventPage(items: List<EventSummaryWireSizeInput>): Long =
        ManagerWireSizeCore.estimateEventPage(items)

    fun estimateNotificationChannelPage(input: NotificationChannelPageWireSizeInput): Long =
        ManagerWireSizeCore.estimateNotificationChannelPage(input)
}
