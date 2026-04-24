package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent
import com.xiaomi.clientreport.data.Config
import com.xiaomi.push.service.PushConstants

object ClientReportHelper {
    @JvmStatic
    fun <T> sendConfigInfo(context: Context, config: Config?) {
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
