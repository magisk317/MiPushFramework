package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import kotlinx.coroutines.runBlocking

/**
 * Projects stored events into display DTOs without mutating history. Payload and regSec are copied
 * only for comparison fidelity; writes remain out of this reader.
 */
class ManagerEventRuntimeReader(
    private val context: Context,
    private val eventRepository: EventRepository,
    private val configCenter: ConfigCenter,
    private val maxPageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    private val maxPayloadBytes: Int = ManagerProtocol.MAX_EVENT_PAYLOAD_BYTES,
) {
    constructor(context: Context) : this(
        context = context,
        eventRepository = AppDependencies.get(context),
        configCenter = AppDependencies.get(context),
    )

    fun readPage(query: ManagerEventReadQuery): ManagerEventReadPage {
        val pageSize = query.pageSize.coerceIn(1, maxPageSize)
        val types: Set<Int>? = if (!runBlocking { configCenter.isShowAllEventsAsync() }) {
            setOf(
                Event.Type.SendMessage,
                Event.Type.Registration,
                Event.Type.RegistrationResult,
                Event.Type.UnRegistration,
            )
        } else {
            null
        }
        val events = runBlocking {
            io.github.magisk317.mipush.runtime.store.db.EventDb.queryByIdAsync(
                lastId = query.lastId,
                size = pageSize,
                types = types,
                pkg = query.packageName.ifBlank { null },
                text = query.query.ifBlank { null },
            )
        }
        return ManagerEventReadPage(items = events.map { it.toReadSummary() })
    }

    private fun Event.toReadSummary(): ManagerEventReadSummary {
        val eventType = TypeFactory.createForDisplay(this)
        val container = RegSecUtils.getContainerWithRegSec(this)
        val summary = eventType.getSummary(context).toString()
        val content = if (container != null) {
            eventRepository.getDecoratedSummary(summary, container)
        } else {
            summary
        }
        val rawPayload = payload
        val safePayload = when {
            rawPayload == null -> null
            rawPayload.size > maxPayloadBytes -> rawPayload.copyOf(maxPayloadBytes)
            else -> rawPayload.copyOf()
        }
        return ManagerEventReadSummary(
            id = id ?: 0L,
            packageName = pkg,
            configOptions = eventRepository.getStatus(container).toList().sorted(),
            channel = eventRepository.getStatusDescription(this),
            receiveDateMs = date,
            title = eventType.getTitle(context).toString(),
            content = content,
            appName = Global.applicationNameCache().getAppName(context, pkg)?.toString(),
            type = type,
            result = result,
            info = info,
            payload = safePayload,
            regSec = regSec,
        )
    }
}
