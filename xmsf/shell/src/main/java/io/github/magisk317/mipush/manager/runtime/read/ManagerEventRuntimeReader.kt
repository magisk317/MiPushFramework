package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.utils.RegSecUtils

/**
 * Projects stored events into display DTOs without mutating history. Payload and regSec are copied
 * only for comparison fidelity; writes remain out of this reader.
 *
 * Pages are size-bounded and wire-byte-bounded so Binder transactions stay under the negotiated
 * payload budget instead of failing the whole page.
 */
class ManagerEventRuntimeReader(
    private val context: Context,
    private val eventRepository: EventRepository,
    private val maxPageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    private val maxPayloadBytes: Int = ManagerProtocol.MAX_EVENT_PAYLOAD_BYTES,
    private val maxPageWireBytes: Int = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
) {
    constructor(context: Context) : this(
        context = context,
        eventRepository = AppDependencies.get(context),
    )

    suspend fun readPage(query: ManagerEventReadQuery): ManagerEventReadPage {
        require(query.userId >= 0) { "Invalid event query user id: ${query.userId}" }
        val pageSize = query.pageSize.coerceIn(1, maxPageSize)
        val events = eventRepository.getEventsById(
            lastId = query.lastId,
            size = pageSize,
            packetName = query.packageName.ifBlank { "" },
            query = query.query.ifBlank { "" },
            userId = query.userId,
        )
        return ManagerEventReadPage(items = takeBoundedSummaries(events))
    }

    private fun takeBoundedSummaries(events: List<RuntimeEventRow>): List<ManagerEventReadSummary> {
        val items = ArrayList<ManagerEventReadSummary>(events.size)
        var estimatedBytes = EVENT_PAGE_FIXED_BYTES
        for (event in events) {
            var summary = event.toReadSummary()
            var itemBytes = estimateSummaryWireBytes(summary)
            if (items.isNotEmpty() && estimatedBytes + itemBytes > maxPageWireBytes) {
                break
            }
            if (itemBytes > maxPageWireBytes) {
                // Prefer a display-only summary over failing the whole page.
                summary = summary.copy(payload = null, regSec = null)
                itemBytes = estimateSummaryWireBytes(summary)
                if (itemBytes > maxPageWireBytes) {
                    break
                }
            }
            items += summary
            estimatedBytes += itemBytes
        }
        return items
    }

    private fun RuntimeEventRow.toReadSummary(): ManagerEventReadSummary {
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
            userId = userId,
            packageName = pkg,
            configOptions = eventRepository.getStatus(container).toList().sorted(),
            channel = eventRepository.getStatusDescription(this, container),
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

    private fun estimateSummaryWireBytes(summary: ManagerEventReadSummary): Int {
        var total = EVENT_SUMMARY_FRAME_BYTES
        total += estimateWireStringBytes(summary.packageName)
        summary.configOptions.forEach { total += estimateWireStringBytes(it) }
        total += estimateWireStringBytes(summary.channel)
        total += estimateWireStringBytes(summary.title)
        total += estimateWireStringBytes(summary.content)
        total += estimateWireStringBytes(summary.appName)
        total += estimateWireStringBytes(summary.info)
        total += summary.payload?.size ?: 0
        total += estimateWireStringBytes(summary.regSec)
        return total
    }

    private fun estimateWireStringBytes(value: String?): Int =
        STRING_LENGTH_PREFIX_BYTES + ((value?.length ?: 0) + STRING_TERMINATOR_CHARS) * UTF16_BYTES_PER_CHAR

    private companion object {
        const val EVENT_PAGE_FIXED_BYTES = 64
        const val EVENT_SUMMARY_FRAME_BYTES = 96
        const val STRING_LENGTH_PREFIX_BYTES = 4
        const val STRING_TERMINATOR_CHARS = 1
        const val UTF16_BYTES_PER_CHAR = 2
    }
}
