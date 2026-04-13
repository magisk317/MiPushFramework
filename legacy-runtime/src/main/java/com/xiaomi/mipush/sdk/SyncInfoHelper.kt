package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.android.PreferenceUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.CollectionUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.msa.MsaIdManager
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushVersionInfo
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import java.text.Collator
import java.util.Locale

object SyncInfoHelper {
    private const val DEFAULT_LAST_SYNC_INFO = -1L
    private const val DEFAULT_PERIOD_IN_SECOND = 1209600
    private const val LAST_SYNC_INFO = "last_sync_info"
    private const val SUMMARY_LENGTH = 4

    @JvmStatic
    fun doSyncInfoAsync(context: Context, fullSync: Boolean) {
        ScheduledJobManager.getInstance(context).addOneShootJob {
            MyLog.w("do sync info")
            val xmPushActionNotification = XmPushActionNotification(PacketHelper.generatePacketID(), false)
            val appInfoHolder = AppInfoHolder.getInstance(context)
            xmPushActionNotification.type = NotificationType.SyncInfo.value
            xmPushActionNotification.setAppId(appInfoHolder.appID)
            xmPushActionNotification.packageName = context.packageName
            xmPushActionNotification.extra = HashMap()
            val actualVersionName = AppInfoUtils.getVersionName(context, context.packageName)
            val actualVersionCode = AppInfoUtils.getVersionCode(context, context.packageName)
            PreferenceUtils.putNotNullExtra(
                xmPushActionNotification.extra,
                Constants.EXTRA_KEY_APP_VERSION,
                PushVersionInfo.reportedAppVersionName(context.packageName, actualVersionName),
            )
            PreferenceUtils.putNotNullExtra(
                xmPushActionNotification.extra,
                Constants.EXTRA_KEY_APP_VERSION_CODE,
                PushVersionInfo.reportedAppVersionCode(context.packageName, actualVersionCode).toString(),
            )
            PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, PushConstants.KEY_PUSH_SDK_VERSION_NAME, PushConstants.PUSH_VERSION_NAME)
            PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, PushConstants.KEY_PUSH_SDK_VERSION_CODE, PushConstants.PUSH_VERSION_CODE.toString())
            PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, "token", appInfoHolder.appToken)
            DeviceInfo.fillLocalVirtDevId(context, xmPushActionNotification.extra)
            if (!MIUIUtils.isGlobalRegion()) {
                val md5Digest = XMStringUtils.getMd5Digest(DeviceInfo.blockingGetIMEI(context))
                val strBlockingGetSubIMEISMd5 = DeviceInfo.blockingGetSubIMEISMd5(context)
                val imeiMd5 = if (!TextUtils.isEmpty(strBlockingGetSubIMEISMd5)) "$md5Digest,$strBlockingGetSubIMEISMd5" else md5Digest
                if (!TextUtils.isEmpty(imeiMd5)) {
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_IMEI_MD5, imeiMd5)
                }
            }
            MsaIdManager.getInstance(context).fillData(xmPushActionNotification.extra)
            PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_REG_ID, appInfoHolder.regID)
            PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_REG_SECRET, appInfoHolder.regSecret)
            PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ACCEPT_TIME, MiPushClient.getAcceptTime(context).replace(",", Constants.ACCEPT_TIME_SEPARATOR_SERVER))
            if (fullSync) {
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ALIASES_MD5, getMd5Summary(MiPushClient.getAllAlias(context)))
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_TOPICS_MD5, getMd5Summary(MiPushClient.getAllTopic(context)))
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ACCOUNTS_MD5, getMd5Summary(MiPushClient.getAllUserAccount(context)))
            } else {
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ALIASES, formatList(MiPushClient.getAllAlias(context)))
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_TOPICS, formatList(MiPushClient.getAllTopic(context)))
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ACCOUNTS, formatList(MiPushClient.getAllUserAccount(context)))
            }
            PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, false, null)
        }
    }

    private fun formatList(list: List<String>): String {
        if (CollectionUtils.isEmpty(list)) return ""
        val arrayList = ArrayList(list)
        arrayList.sortWith(Collator.getInstance(Locale.CHINA))
        return arrayList.joinToString(",")
    }

    private fun getMd5Summary(list: List<String>): String {
        val md5Digest = XMStringUtils.getMd5Digest(formatList(list))
        return if (TextUtils.isEmpty(md5Digest) || md5Digest.length <= 4) "" else md5Digest.substring(0, 4).lowercase()
    }

    @JvmStatic
    fun saveInfo(context: Context, xmPushActionNotification: XmPushActionNotification) {
        MyLog.w("need to update local info with: ${xmPushActionNotification.extra}")
        val str = xmPushActionNotification.extra[Constants.EXTRA_KEY_ACCEPT_TIME]
        if (str != null) {
            MiPushClient.removeAcceptTime(context)
            val strArrSplit = str.split(Constants.ACCEPT_TIME_SEPARATOR_SERVER)
            if (strArrSplit.size == 2) {
                MiPushClient.addAcceptTime(context, strArrSplit[0], strArrSplit[1])
                AppInfoHolder.getInstance(context).setPaused(strArrSplit[0] == "00:00" && strArrSplit[1] == "00:00")
            }
        }
        val str2 = xmPushActionNotification.extra[Constants.EXTRA_KEY_ALIASES]
        if (str2 != null) {
            MiPushClient.removeAllAliases(context)
            if (str2 != "") {
                for (str3 in str2.split(",")) {
                    MiPushClient.addAlias(context, str3)
                }
            }
        }
        val str4 = xmPushActionNotification.extra[Constants.EXTRA_KEY_TOPICS]
        if (str4 != null) {
            MiPushClient.removeAllTopics(context)
            if (str4 != "") {
                for (str5 in str4.split(",")) {
                    MiPushClient.addTopic(context, str5)
                }
            }
        }
        val str6 = xmPushActionNotification.extra[Constants.EXTRA_KEY_ACCOUNTS]
        if (str6 != null) {
            MiPushClient.removeAllAccounts(context)
            if (str6 == "") return
            for (str7 in str6.split(",")) {
                MiPushClient.addAccount(context, str7)
            }
        }
    }

    @JvmStatic
    fun tryToSyncInfo(context: Context) {
        val sharedPreferences = context.getSharedPreferences("mipush_extra", 0)
        val j = sharedPreferences.getLong(LAST_SYNC_INFO, DEFAULT_LAST_SYNC_INFO)
        val jCurrentTimeMillis = System.currentTimeMillis() / 1000
        val intValue = OnlineConfig.getInstance(context).getIntValue(ConfigKey.SyncInfoFrequency.value, DEFAULT_PERIOD_IN_SECOND)
        if (j == DEFAULT_LAST_SYNC_INFO) {
            sharedPreferences.edit().putLong(LAST_SYNC_INFO, jCurrentTimeMillis).commit()
        } else if (Math.abs(jCurrentTimeMillis - j) > intValue) {
            doSyncInfoAsync(context, true)
            sharedPreferences.edit().putLong(LAST_SYNC_INFO, jCurrentTimeMillis).commit()
        }
    }
}
