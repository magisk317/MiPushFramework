package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.runtime.Immutable
import io.github.magisk317.mipush.manager.application.ManagerEvent
import java.util.Date

@Immutable
data class EventInfoForDisplay(
    val id: Long,
    val packageName: String,
    val configOptions: Set<String>,
    val channel: String,
    val receiveDate: Date,
    val title: String,
    val content: String,
    val appName: String? = null,
    val event: ManagerEvent = ManagerEvent(
        id = id,
        packageName = packageName,
        configOptions = configOptions,
        channel = channel,
        receiveDateMs = receiveDate.time,
        title = title,
        content = content,
        appName = appName,
    ),
)
