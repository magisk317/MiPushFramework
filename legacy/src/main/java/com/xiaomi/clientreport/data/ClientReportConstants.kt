package com.xiaomi.clientreport.data
import io.github.magisk317.mipush.protocol.model.*

object ClientReportConstants {
    const val BIAS: Int = 5
    const val CATEGORY: String = "category_client_report_data"
    const val DEFAULT_EVENT_UPLOAD_LAST_TIME: Long = 0
    const val DEFAULT_PERF_UPLOAD_LAST_TIME: Long = 0
    const val ERROR: Int = 5001
    const val ERROR_EVENT_CACHE_FILE_TOO_BIG: String = "24"
    const val ERROR_EVENT_CHAIN_INTERFACE_ID: String = "E100004"
    const val EVENT_LAST_UPLOAD_TIME: String = "event_last_upload_time"
    const val NAME: String = "quality_support"
    const val PERF_LAST_UPLOAD_TIME: String = "perf_last_upload_time"
    const val REPORT_TYPE_EVENT: Int = 1001
    const val REPORT_TYPE_PERF: Int = 1000
    const val SEPARATOR: String = "%%%"
    const val SEPARATOR_SHARP: String = "#"
    const val SLEEP_NUM: Int = 25
    const val SP_FILE_STATUS: String = "sp_client_report_status"
    const val SP_KEY_EVENT_FREQUENCY_KEY: String = "sp_client_report_event_frequency_key"
    const val SP_KEY_EVENT_SWITCH_KEY: String = "sp_client_report_event_switch_key"
    const val SP_KEY_FILE_LENGTH_KEY: String = "sp_client_report_file_length_key"
    const val SP_KEY_KEY: String = "sp_client_report_key"
    const val SP_KEY_PERF_FREQUENCY_KEY: String = "sp_client_report_perf_frequency_key"
    const val SP_KEY_PERF_SWITCH_KEY: String = "sp_client_report_perf_switch_key"
    const val SP_KEY_SWITCH_KEY: String = "sp_client_report_switch_key"
    const val XMSF_UPLOAD: String = "com.xiaomi.xmsf.push.XMSF_UPLOAD_ACTIVE"
    const val XMSF_UPLOAD_PERMISSION: String = "com.xiaomi.xmsf.permission.USE_XMSF_UPLOAD"
}
