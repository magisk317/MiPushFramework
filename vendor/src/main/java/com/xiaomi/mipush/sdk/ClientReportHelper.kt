package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import com.xiaomi.clientreport.data.Config
import com.xiaomi.push.service.PushConstants

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object ClientReportHelper {
    @JvmStatic
    fun sendConfigInfo(context: Context, config: Config?) {
        if (config == null) return
        Intent().apply {
            action = PushConstants.ACTION_CLIENT_REPORT_CONFIG
            putExtra(PushConstants.EXTRA_CR_EVENT_SWITCH, config.isEventUploadSwitchOpen)
            putExtra(PushConstants.EXTRA_CR_EVENT_FREQUENCY, config.eventUploadFrequency)
            putExtra(PushConstants.EXTRA_CR_PREF_SWITCH, config.isPerfUploadSwitchOpen)
            putExtra(PushConstants.EXTRA_CR_PREF_FREQUENCY, config.perfUploadFrequency)
            putExtra(PushConstants.EXTRA_CR_EVENT_ENCRYPTED, config.isEventEncrypted)
            putExtra(PushConstants.EXTRA_CR_MAX_FILE_SIZE, config.maxFileLength)
            PushServiceClient.getInstance(context).sendDataCommon(this)
        }
    }
}
