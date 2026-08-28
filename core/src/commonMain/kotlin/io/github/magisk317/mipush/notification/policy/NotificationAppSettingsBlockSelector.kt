package io.github.magisk317.mipush.notification.policy

/** Selects one package/UID-scoped AppSettings block from a full notification service dump. */
object NotificationAppSettingsBlockSelector {
    private val appSettingsHeader = Regex(
        pattern = """(?m)^[\t ]*AppSettings:\s+([^\s]+)(?:\s+\((\d+)\))?.*$""",
    )

    fun select(
        notificationDump: String,
        packageName: String,
        preferredUid: Int? = null,
    ): String? {
        if (notificationDump.isBlank() || packageName.isBlank()) return null

        val headers = appSettingsHeader.findAll(notificationDump).toList()
        val matchingBlocks = headers.mapIndexedNotNull { index, header ->
            if (header.groupValues[1] != packageName) return@mapIndexedNotNull null
            val end = headers.getOrNull(index + 1)?.range?.first ?: notificationDump.length
            AppSettingsBlock(
                uid = header.groupValues[2].toIntOrNull(),
                value = notificationDump.substring(header.range.first, end),
            )
        }
        if (matchingBlocks.isEmpty()) return null

        val preferred = preferredUid
            ?.let { uid -> matchingBlocks.firstOrNull { block -> block.uid == uid } }
        if (preferred != null) return preferred.value

        // A few HyperOS dumps include an empty uid-1000 namespace before the real app uid.
        // If the caller cannot resolve the uid, skip that empty namespace while preserving the
        // dump order for multiple populated user records.
        return matchingBlocks.firstOrNull { it.hasNotificationEntries }?.value
            ?: matchingBlocks.first().value
    }

    private data class AppSettingsBlock(
        val uid: Int?,
        val value: String,
    ) {
        val hasNotificationEntries: Boolean
            get() = value.contains("NotificationChannel")
    }
}
