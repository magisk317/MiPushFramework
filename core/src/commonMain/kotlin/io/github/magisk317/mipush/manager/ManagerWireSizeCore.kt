package io.github.magisk317.mipush.manager

data class ApplicationSummaryWireSizeInput(
    val idPresent: Boolean,
    val packageName: String,
    val appName: String,
    val appNamePinYin: String,
)

data class ApplicationPageWireSizeInput(
    val items: List<ApplicationSummaryWireSizeInput>,
    val nextPageToken: String?,
)

data class EventSummaryWireSizeInput(
    val packageName: String,
    val configOptions: List<String>,
    val channel: String,
    val title: String,
    val content: String,
    val appName: String?,
    val info: String?,
    val payloadBytes: Int,
    val regSec: String?,
)

data class NotificationChannelSummaryWireSizeInput(
    val id: String,
    val name: String,
    val groupId: String?,
    val description: String?,
)

data class NotificationChannelGroupWireSizeInput(
    val id: String,
    val name: String,
)

data class NotificationChannelPageWireSizeInput(
    val items: List<NotificationChannelSummaryWireSizeInput>,
    val groups: List<NotificationChannelGroupWireSizeInput>,
    val nextPageToken: String?,
)

/** Parcelable-compatible size arithmetic without Android or Binder types. */
object ManagerWireSizeCore {
    fun stringBytes(value: String?): Long {
        if (value == null) return INTEGER_BYTES
        val charsWithTerminator = value.length.toLong() + 1L
        val bytes = INTEGER_BYTES + charsWithTerminator * 2L
        return ((bytes + 3L) / 4L) * 4L
    }

    fun estimateApplicationPage(input: ApplicationPageWireSizeInput): Long =
        APPLICATION_PAGE_FIXED_BYTES + APPLICATION_STATS_FRAME_BYTES +
            input.items.sumOf { item ->
                APPLICATION_SUMMARY_FRAME_BYTES +
                    if (item.idPresent) LONG_BYTES else 0L
            } +
            input.items.sumOf { item ->
                stringBytes(item.packageName) + stringBytes(item.appName) + stringBytes(item.appNamePinYin)
            } + stringBytes(input.nextPageToken)

    fun estimateEventPage(items: List<EventSummaryWireSizeInput>): Long =
        EVENT_PAGE_FIXED_BYTES + items.sumOf { summary ->
            EVENT_SUMMARY_FRAME_BYTES +
                stringBytes(summary.packageName) +
                summary.configOptions.sumOf(::stringBytes) +
                stringBytes(summary.channel) +
                stringBytes(summary.title) +
                stringBytes(summary.content) +
                stringBytes(summary.appName) +
                stringBytes(summary.info) +
                summary.payloadBytes.toLong() +
                stringBytes(summary.regSec)
        }

    fun estimateNotificationChannelPage(input: NotificationChannelPageWireSizeInput): Long =
        NOTIFICATION_CHANNEL_PAGE_FIXED_BYTES +
            input.items.sumOf { summary ->
                NOTIFICATION_CHANNEL_SUMMARY_FRAME_BYTES +
                    stringBytes(summary.id) +
                    stringBytes(summary.name) +
                    stringBytes(summary.groupId) +
                    stringBytes(summary.description)
            } +
            input.groups.sumOf { group ->
                NOTIFICATION_CHANNEL_GROUP_FRAME_BYTES +
                    stringBytes(group.id) +
                    stringBytes(group.name)
            } + stringBytes(input.nextPageToken)

    private const val INTEGER_BYTES = 4L
    private const val LONG_BYTES = 8L
    private const val APPLICATION_PAGE_FIXED_BYTES = 4L + 4L + 4L + 4L
    // Includes the userId and trailing clickFallbackEnabled fields in every summary frame.
    private const val APPLICATION_SUMMARY_FRAME_BYTES = 4L + 4L + 4L + 28L + 8L + 4L + 4L
    private const val APPLICATION_STATS_FRAME_BYTES = 4L + 6L * 4L
    private const val EVENT_PAGE_FIXED_BYTES = 4L + 4L
    private const val EVENT_SUMMARY_FRAME_BYTES = 4L + 8L + 8L + 8L + 8L + 8L
    private const val NOTIFICATION_CHANNEL_PAGE_FIXED_BYTES = 4L + 4L + 4L + 4L + 4L
    private const val NOTIFICATION_CHANNEL_SUMMARY_FRAME_BYTES = 4L + 4L + 12L
    private const val NOTIFICATION_CHANNEL_GROUP_FRAME_BYTES = 4L + 4L
}
