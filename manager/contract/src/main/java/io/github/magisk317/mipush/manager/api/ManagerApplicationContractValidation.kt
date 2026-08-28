package io.github.magisk317.mipush.manager.api

/** Internal application query and page-contract validation domain. */
internal object ManagerApplicationContractValidation {
    fun validateQuery(query: ManagerApplicationQueryDto, negotiatedMaxPageSize: Int): String? = when {
        query.schemaVersion < 1 -> "invalid_application_query_schema"
        negotiatedMaxPageSize !in 1..ManagerProtocol.MAX_NEGOTIATED_PAGE_SIZE -> "invalid_negotiated_page_size"
        query.query.length > ManagerProtocol.MAX_APPLICATION_QUERY_LENGTH -> "application_query_too_long"
        query.filterMode !in ManagerProtocol.APPLICATION_FILTER_ALL..ManagerProtocol.APPLICATION_FILTER_UNREGISTERED ->
            "invalid_application_filter_mode"
        query.pageSize !in 1..negotiatedMaxPageSize -> "invalid_application_page_size"
        query.userId < 0 -> "invalid_application_user_id"
        query.pageToken?.length?.let { it > ManagerProtocol.MAX_PAGE_TOKEN_LENGTH } == true ->
            "application_page_token_too_long"
        else -> null
    }

    fun validatePage(
        page: ManagerApplicationPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int,
    ): String? {
        if (page.schemaVersion < 1) return "invalid_application_page_schema"
        if (page.userId < 0 || page.items.any { it.userId != page.userId }) return "invalid_application_user_id"
        if (negotiatedMaxPageSize !in 1..ManagerProtocol.MAX_NEGOTIATED_PAGE_SIZE) return "invalid_negotiated_page_size"
        if (negotiatedMaxPayloadBytes !in 1..ManagerProtocol.MAX_NEGOTIATED_PAYLOAD_BYTES) return "invalid_negotiated_payload_bytes"
        if (page.items.size > negotiatedMaxPageSize) return "too_many_application_page_items"
        if (page.nextPageToken?.length?.let { it > ManagerProtocol.MAX_PAGE_TOKEN_LENGTH } == true) {
            return "application_next_page_token_too_long"
        }
        page.items.forEach { validateSummary(it)?.let { reason -> return reason } }
        validateStats(page.stats)?.let { return it }
        return if (estimatePageWireBytes(page) > negotiatedMaxPayloadBytes.toLong()) {
            "application_page_payload_too_large"
        } else null
    }

    fun estimatePageWireBytes(page: ManagerApplicationPageDto): Long {
        val items = page.items.sumOf { summary ->
            val nullableIdBytes = if (summary.id == null) 0L else java.lang.Long.BYTES.toLong()
            APPLICATION_SUMMARY_FRAME_BYTES + nullableIdBytes + wireStringBytes(summary.packageName) +
                wireStringBytes(summary.appName) + wireStringBytes(summary.appNamePinYin)
        }
        return APPLICATION_PAGE_FIXED_BYTES + items + APPLICATION_STATS_FRAME_BYTES + wireStringBytes(page.nextPageToken)
    }

    fun validateSummary(summary: ManagerApplicationSummaryDto): String? =
        if (summary.userId < 0) "invalid_application_user_id" else validateFields(
            summary.schemaVersion, summary.packageName, summary.appName, summary.appNamePinYin,
            "invalid_application_summary_schema",
        )

    fun validateStats(stats: ManagerApplicationStatsDto): String? = when {
        stats.schemaVersion < 1 -> "invalid_application_stats_schema"
        stats.total < 0 || stats.usingMiPush < 0 || stats.notUsingMiPush < 0 ||
            stats.registered < 0 || stats.notRegistered < 0 -> "invalid_application_stats_count"
        stats.usingMiPush.toLong() + stats.notUsingMiPush.toLong() != stats.total.toLong() ->
            "inconsistent_application_stats_total"
        stats.registered.toLong() + stats.notRegistered.toLong() != stats.usingMiPush.toLong() ->
            "inconsistent_application_stats_registration"
        else -> null
    }

    fun validateDetail(detail: ManagerApplicationDetailDto): String? =
        if (detail.userId < 0) "invalid_application_user_id" else validateFields(
            detail.schemaVersion, detail.packageName, detail.appName, detail.appNamePinYin,
            "invalid_application_detail_schema",
        )

    fun validateDiagnostics(diagnostics: ManagerApplicationDiagnosticsDto): String? = when {
        diagnostics.schemaVersion < 1 -> "invalid_application_diagnostics_schema"
        diagnostics.userId < 0 -> "invalid_application_user_id"
        diagnostics.regSecCount < 0 -> "invalid_application_diagnostics_reg_sec_count"
        diagnostics.inferenceReason.length > ManagerProtocol.MAX_WIRE_STRING_LENGTH ->
            "application_diagnostics_inference_reason_too_long"
        else -> null
    }

    fun validatePackageName(packageName: String): String? = when {
        packageName.isBlank() || packageName.length > ManagerProtocol.MAX_PACKAGE_NAME_LENGTH ->
            "invalid_application_package_name"
        packageName.any { !it.isLetterOrDigit() && it != '.' && it != '_' } ->
            "invalid_application_package_name"
        else -> null
    }

    fun validateDiagnosticsRequest(packageName: String, registeredType: Int): String? =
        validatePackageName(packageName) ?: if (registeredType !in 0..2) "invalid_application_registered_type" else null

    private fun validateFields(schemaVersion: Int, packageName: String, appName: String, appNamePinYin: String, schemaError: String): String? = when {
        schemaVersion < 1 -> schemaError
        validatePackageName(packageName) != null -> "invalid_application_package_name"
        appName.length > ManagerProtocol.MAX_WIRE_STRING_LENGTH -> "application_name_too_long"
        appNamePinYin.length > ManagerProtocol.MAX_WIRE_STRING_LENGTH -> "application_name_pinyin_too_long"
        else -> null
    }

    private fun wireStringBytes(value: String?): Long {
        if (value == null) return Integer.BYTES.toLong()
        val bytes = Integer.BYTES.toLong() + (value.length.toLong() + 1L) * 2L
        return ((bytes + 3L) / 4L) * 4L
    }

    private const val APPLICATION_PAGE_FIXED_BYTES = 4L + 4L + 4L + 4L
    private const val APPLICATION_SUMMARY_FRAME_BYTES = 4L + 4L + 4L + 28L + 8L
    private const val APPLICATION_STATS_FRAME_BYTES = 4L + 6L * 4L
}
