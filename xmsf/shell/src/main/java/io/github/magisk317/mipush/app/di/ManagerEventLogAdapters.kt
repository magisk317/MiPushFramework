package io.github.magisk317.mipush.app.di

import android.content.Context
import android.net.Uri
import android.os.Process
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.MiPushFrameworkApp
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.utils.DiagnosticExportModes
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerNotificationChannelCommandGateway
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.config.ConfigNavigationHelper
import io.github.magisk317.mipush.configuration.ConfigEditorSnapshot
import io.github.magisk317.mipush.configuration.ConfigSyncRepository
import io.github.magisk317.mipush.configuration.toSummary
import io.github.magisk317.mipush.common.utils.ElapsedTimer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.compat.RegistrationStateCompat
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.PermissionUtils
import io.github.magisk317.mipush.platform.support.ShellUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.EventRetentionManager
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.EventRowType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.adapter.container
import io.github.magisk317.mipush.runtime.store.event.type.NotificationType
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.manager.runtime.read.AndroidManagerApplicationReadSource
import io.github.magisk317.mipush.manager.runtime.read.InstalledApplicationSnapshot
import io.github.magisk317.mipush.manager.runtime.read.ManagerApplicationReadPolicy
import io.github.magisk317.mipush.manager.runtime.read.RegistrationEventSnapshot
import io.github.magisk317.mipush.manager.runtime.read.toManagerApplication
import io.github.magisk317.mipush.manager.runtime.read.toStoredApplicationSnapshot
import io.github.magisk317.mipush.service.runtime.RuntimeSettingsAdapter
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.LogUtils
import io.github.magisk317.mipush.utils.RegistrationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.Locale

class XmsfManagerEventGateway(
    private val context: Context,
    private val eventRepository: EventRepository,
) : ManagerEventGateway {
    override suspend fun getEventsById(lastId: Long?, size: Int, packageName: String, query: String): List<ManagerEvent> =
        eventRepository.getEventsById(lastId, size, packageName, query).map { it.toManagerEvent() }

    override fun startManagePermissions(packageName: String, ignoreNotRegistered: Boolean) {
        eventRepository.startManagePermissions(packageName, ignoreNotRegistered)
    }

    override suspend fun startConfigPreview(packageName: String) {
        eventRepository.startConfigPreview(packageName)
    }

    override fun copyToClipboard(content: String) {
        eventRepository.copyToClipboard(content)
    }

    override suspend fun mockMessage(event: ManagerEvent): MockReplayOutcome {
        val resolved = resolveEventForMock(event) ?: return MockReplayOutcome.FailedEventNotFound
        val payload = resolved.payload
        if (payload == null || payload.isEmpty()) {
            return MockReplayOutcome.FailedPayloadMissing
        }
        val container = RegSecUtils.getContainerWithRegSec(payload, resolved.regSec)
            ?: return MockReplayOutcome.FailedPayloadMissing
        return eventRepository.mockMessage(container)
    }

    private suspend fun resolveEventForMock(event: ManagerEvent): ManagerEvent? {
        if (event.id <= 0L || event.packageName.isBlank()) return null
        val stored = EventDb.getByIdAsync(event.id, event.userId) ?: return null
        if (stored.pkg != event.packageName) return null
        return stored.toManagerEvent()
    }

    override suspend fun getJson(event: ManagerEvent): String? {
        val owned = if (event.id > 0L) resolveEventForMock(event) ?: return null else event
        return eventRepository.getJson(owned.toEventRow())?.toString()
            ?: runCatching { EventDebugJson.format(owned) }.getOrNull()
    }

    override suspend fun getContent(event: ManagerEvent): String? {
        val resolved = if (event.id > 0L) resolveEventForMock(event) ?: return null else event
        val container = RegSecUtils.getContainerWithRegSec(resolved.payload, resolved.regSec)
            ?: return null
        return eventRepository.getContent(resolved.toEventRow(), container)
    }

    override suspend fun deleteEvent(event: ManagerEvent): Boolean =
        eventRepository.deleteEvent(event.toEventRow())

    override suspend fun restoreEvent(event: ManagerEvent): ManagerEvent? {
        val restoredId = eventRepository.restoreEvent(event.toEventRow())
        return restoredId.takeIf { it > 0L }?.let { event.copy(id = it) }
    }

    override suspend fun countEventsByDay(): List<io.github.magisk317.mipush.manager.application.ManagerDayCount> =
        eventRepository.countEventsByDay().map {
            io.github.magisk317.mipush.manager.application.ManagerDayCount(day = it.day, count = it.count)
        }

    override suspend fun clearHistoryBefore(cutoffMillis: Long): Int =
        eventRepository.deleteHistoryBefore(cutoffMillis)

    override suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int =
        eventRepository.deleteHistoryInRange(startMillis, endMillis)

    private fun RuntimeEventRow.toManagerEvent(): ManagerEvent {
        val eventType = TypeFactory.createForDisplay(this)
        val container = RegSecUtils.getContainerWithRegSec(this)
        val summary = eventType.getSummary(context).toString()
        val content = if (container != null) {
            eventRepository.getDecoratedSummary(summary, container)
        } else {
            summary
        }
        return ManagerEvent(
            id = id ?: 0L,
            userId = userId,
            packageName = pkg,
            configOptions = eventRepository.getStatus(container),
            channel = eventRepository.getStatusDescription(this),
            receiveDateMs = date,
            title = eventType.getTitle(context).toString(),
            content = content,
            appName = Global.applicationNameCache().getAppName(context, pkg)?.toString(),
            type = type,
            result = result,
            info = info,
            payload = payload,
            regSec = regSec,
        )
    }

    private fun ManagerEvent.toEventRow(): RuntimeEventRow =
        RuntimeEventRow(
            id = id.takeIf { it > 0L },
            userId = userId,
            pkg = packageName,
            type = type,
            date = receiveDateMs,
            result = result,
            info = info,
            payload = payload,
            regSec = regSec,
        )
}

class XmsfManagerLogGateway : ManagerLogGateway {
    override suspend fun setRetentionDays(days: Int) {
        LogUtils.setRetentionDays(days)
    }

    override suspend fun buildLogBundle(context: Context): ManagerLogExportResult {
        val result = LogBundleExporter.buildLogBundle(
            context = context,
            mode = DiagnosticExportModes.fromDebugLoggingSetting(context),
        )
        return ManagerLogExportResult(archivePath = result.file?.absolutePath, details = result.details)
    }

    override suspend fun clearLogFolders(context: Context): ManagerLogClearResult {
        val result = LogBundleExporter.clearLogFolders(context)
        return ManagerLogClearResult(success = result.success, details = result.details)
    }
}
