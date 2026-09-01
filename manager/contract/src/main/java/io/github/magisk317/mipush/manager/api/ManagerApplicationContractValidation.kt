package io.github.magisk317.mipush.manager.api

import io.github.magisk317.mipush.manager.ApplicationDiagnosticsValidationInput
import io.github.magisk317.mipush.manager.ApplicationDetailValidationInput
import io.github.magisk317.mipush.manager.ApplicationPageValidationInput
import io.github.magisk317.mipush.manager.ApplicationQueryValidationInput
import io.github.magisk317.mipush.manager.ApplicationStatsValidationInput
import io.github.magisk317.mipush.manager.ApplicationSummaryValidationInput
import io.github.magisk317.mipush.manager.ManagerContractValidationCore

/** Parcelable facade over the platform-neutral application contract validation. */
internal object ManagerApplicationContractValidation {
    fun validateQuery(query: ManagerApplicationQueryDto, negotiatedMaxPageSize: Int): String? =
        ManagerContractValidationCore.validateApplicationQuery(
            ApplicationQueryValidationInput(
                schemaVersion = query.schemaVersion,
                query = query.query,
                filterMode = query.filterMode,
                pageSize = query.pageSize,
                pageToken = query.pageToken,
                userId = query.userId,
                negotiatedMaxPageSize = negotiatedMaxPageSize,
            ),
        )

    fun validatePage(
        page: ManagerApplicationPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int,
    ): String? = ManagerContractValidationCore.validateApplicationPage(
        ApplicationPageValidationInput(
            schemaVersion = page.schemaVersion,
            userId = page.userId,
            itemUserIds = page.items.map { it.userId },
            summaries = page.items.map(::summaryInput),
            stats = ApplicationStatsValidationInput(
                schemaVersion = page.stats.schemaVersion,
                total = page.stats.total,
                usingMiPush = page.stats.usingMiPush,
                notUsingMiPush = page.stats.notUsingMiPush,
                registered = page.stats.registered,
                notRegistered = page.stats.notRegistered,
            ),
            nextPageToken = page.nextPageToken,
            negotiatedMaxPageSize = negotiatedMaxPageSize,
            negotiatedMaxPayloadBytes = negotiatedMaxPayloadBytes,
            wireSize = ApplicationPageWireSizeInput(
                items = page.items.map { summary ->
                    ApplicationSummaryWireSizeInput(
                        idPresent = summary.id != null,
                        packageName = summary.packageName,
                        appName = summary.appName,
                        appNamePinYin = summary.appNamePinYin,
                    )
                },
                nextPageToken = page.nextPageToken,
            ),
        ),
    )

    fun estimatePageWireBytes(page: ManagerApplicationPageDto): Long =
        ManagerWireSize.estimateApplicationPage(
            ApplicationPageWireSizeInput(
                items = page.items.map { summary ->
                    ApplicationSummaryWireSizeInput(
                        idPresent = summary.id != null,
                        packageName = summary.packageName,
                        appName = summary.appName,
                        appNamePinYin = summary.appNamePinYin,
                    )
                },
                nextPageToken = page.nextPageToken,
            ),
        )

    fun validateSummary(summary: ManagerApplicationSummaryDto): String? =
        ManagerContractValidationCore.validateApplicationSummary(summaryInput(summary))

    fun validateStats(stats: ManagerApplicationStatsDto): String? =
        ManagerContractValidationCore.validateApplicationStats(
            ApplicationStatsValidationInput(
                schemaVersion = stats.schemaVersion,
                total = stats.total,
                usingMiPush = stats.usingMiPush,
                notUsingMiPush = stats.notUsingMiPush,
                registered = stats.registered,
                notRegistered = stats.notRegistered,
            ),
        )

    fun validateDetail(detail: ManagerApplicationDetailDto): String? =
        ManagerContractValidationCore.validateApplicationDetail(
            ApplicationDetailValidationInput(
                schemaVersion = detail.schemaVersion,
                packageName = detail.packageName,
                appName = detail.appName,
                appNamePinYin = detail.appNamePinYin,
                userId = detail.userId,
            ),
        )

    fun validateDiagnostics(diagnostics: ManagerApplicationDiagnosticsDto): String? =
        ManagerContractValidationCore.validateApplicationDiagnostics(
            ApplicationDiagnosticsValidationInput(
                schemaVersion = diagnostics.schemaVersion,
                regSecCount = diagnostics.regSecCount,
                inferenceReason = diagnostics.inferenceReason,
                userId = diagnostics.userId,
            ),
        )

    fun validatePackageName(packageName: String): String? =
        ManagerContractValidationCore.validatePackageName(packageName)

    fun validateDiagnosticsRequest(packageName: String, registeredType: Int): String? =
        ManagerContractValidationCore.validateDiagnosticsRequest(packageName, registeredType)

    private fun summaryInput(summary: ManagerApplicationSummaryDto) = ApplicationSummaryValidationInput(
        schemaVersion = summary.schemaVersion,
        packageName = summary.packageName,
        appName = summary.appName,
        appNamePinYin = summary.appNamePinYin,
        userId = summary.userId,
    )
}
