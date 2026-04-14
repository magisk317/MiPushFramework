package com.xiaomi.tinyData

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.logger.LoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.DefaultConfig
import com.xiaomi.push.service.MIPushAccountUtils
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.TinyDataStorage
import com.xiaomi.push.service.PingCallBack
import java.io.File

class TinyDataCacheProcessor(private val mContext: Context) : PingCallBack {
    private var mPeriod: Int = 0
    private var mUploadSwitch: Boolean = false

    private fun canUpload(tinyDataUploader: TinyDataUploader?): Boolean {
        if (!Network.hasNetwork(mContext) ||
            tinyDataUploader == null ||
            TextUtils.isEmpty(getAppId(mContext.packageName)) ||
            !File(mContext.filesDir, TinyDataStorage.TINY_DATA_CACHE_FILE_NAME).exists() ||
            mIsTinyDataExtracting
        ) {
            return false
        }
        return !OnlineConfig.getInstance(mContext).getBooleanValue(
            com.xiaomi.xmpush.thrift.ConfigKey.ScreenOnOrChargingTinyDataUploadSwitch.value,
            false
        ) || DeviceInfo.isCharging(mContext) || DeviceInfo.isScreenOn(mContext)
    }

    private fun getAppId(str: String): String {
        return if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == str) {
            MIPushAccountUtils.MIPUSH_MIUI_APPID
        } else {
            mContext.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0).getString(str, null)
        } ?: ""
    }

    private fun readOnlineConfig(context: Context) {
        mUploadSwitch = OnlineConfig.getInstance(context).getBooleanValue(
            com.xiaomi.xmpush.thrift.ConfigKey.TinyDataUploadSwitch.value,
            true
        )
        val intValue = OnlineConfig.getInstance(context).getIntValue(
            com.xiaomi.xmpush.thrift.ConfigKey.TinyDataUploadFrequency.value,
            DefaultConfig.DEFAULT_TINY_DATA_UPLOAD_FREQUNECY
        )
        mPeriod = maxOf(60, intValue)
    }

    private fun verifyUploadPeriod(): Boolean {
        val lastUploadTime = mContext.getSharedPreferences("mipush_extra", 4)
            .getLong(LAST_TINY_DATA_UPLOAD_TIMESTAMP, DEFAULT_LAST_TINY_DATA_UPLOAD_TIMESTAMP.toLong())
        return kotlin.math.abs((System.currentTimeMillis() / 1000) - lastUploadTime) > mPeriod.toLong()
    }

    override fun pingFollowUpAction() {
        readOnlineConfig(mContext)
        if (mUploadSwitch && verifyUploadPeriod()) {
            MyLog.w("TinyData TinyDataCacheProcessor.pingFollowUpAction ts:${System.currentTimeMillis()}")
            val uploader = TinyDataManager.getInstance(mContext)?.uploader
            if (canUpload(uploader)) {
                mIsTinyDataExtracting = true
                TinyDataCacheReader.addTinyDataCacheReadJob(mContext, uploader)
            } else {
                MyLog.w("TinyData TinyDataCacheProcessor.pingFollowUpAction !canUpload(uploader) ts:${System.currentTimeMillis()}")
            }
        }
    }

    companion object {
        private const val DEFAULT_LAST_TINY_DATA_UPLOAD_TIMESTAMP = -1
        const val LAST_TINY_DATA_UPLOAD_TIMESTAMP = "last_tiny_data_upload_timestamp"
        private var mIsTinyDataExtracting = false

        fun setIsTinyDataExtracting(isExtracting: Boolean) {
            mIsTinyDataExtracting = isExtracting
        }
    }
}
