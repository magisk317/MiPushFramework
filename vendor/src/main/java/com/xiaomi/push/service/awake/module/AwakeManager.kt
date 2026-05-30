package com.xiaomi.push.service.awake.module

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper
import org.json.JSONException
import org.json.JSONObject

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/awake/module/AwakeManager.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class AwakeManager private constructor(context: Context) {
    private val mModuleMap = HashMap<HelpType, IAwakeModule>()
    private val mContext: Context = context
    var appId: String? = null
        private set
    var onLineCmd = 0
        private set
    var packageName: String? = null
        private set
    var sendDataIml: IProcessData? = null
        private set

    init {
        mModuleMap[HelpType.SERVICE_ACTION] = ServiceActionAwakeModule()
        mModuleMap[HelpType.SERVICE_COMPONENT] = ServiceComponentAwakeModule()
        mModuleMap[HelpType.ACTIVITY] = ActivityActionAwakeModule()
        mModuleMap[HelpType.PROVIDER] = ProviderAwakeModule()
    }

    private fun doAwake(helpType: HelpType, context: Context, awakeInfo: AwakeInfo) {
        mModuleMap[helpType]?.doAwake(context, awakeInfo)
    }

    fun sendResult(helpType: HelpType?, context: Context, intent: Intent, str: String) {
        if (helpType != null) {
            mModuleMap[helpType]?.doSendAwakeResult(context, intent, str)
        } else {
            AwakeUploadHelper.uploadData(context, "null", 1008, "A receive a incorrect message with empty type")
        }
    }

    fun setAppId(appId: String?) {
        this.appId = appId
    }

    fun setOnLineCmd(onLineCmd: Int) {
        this.onLineCmd = onLineCmd
    }

    fun setPackageInfo(appId: String?, packageName: String?, onLineCmd: Int) {
        setAppId(appId)
        this.packageName = packageName
        setOnLineCmd(onLineCmd)
    }

    fun setPackageInfo(appId: String?, packageName: String?, onLineCmd: Int, processData: IProcessData?) {
        setAppId(appId)
        this.packageName = packageName
        setOnLineCmd(onLineCmd)
        sendDataIml = processData
    }

    fun setPackageName(packageName: String?) {
        this.packageName = packageName
    }

    fun setSendDataIml(sendDataIml: IProcessData?) {
        this.sendDataIml = sendDataIml
    }

    fun wakeup(context: Context, str: String, i: Int, str2: String, str3: String) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2) || TextUtils.isEmpty(str3)) {
            AwakeUploadHelper.uploadData(context, "$str", 1008, "A receive a incorrect message")
            return
        }

        setOnLineCmd(i)
        ScheduledJobManager.getInstance(mContext).addOneShootJob {
            if (TextUtils.isEmpty(str)) {
                AwakeUploadHelper.uploadData(context, "null", 1008, "A receive a incorrect message with empty info")
                return@addOneShootJob
            }
            try {
                AwakeUploadHelper.uploadData(context, str, 1001, "get message")
                val jsonObject = JSONObject(str)
                val action = jsonObject.optString("action")
                val awakenedPackageName = jsonObject.optString("awakened_app_packagename")
                val awakeAppPackageName = jsonObject.optString("awake_app_packagename")
                val awakeApp = jsonObject.optString("awake_app")
                val awakeType = jsonObject.optString("awake_type")
                val awakeForeground = jsonObject.optInt("awake_foreground", 0)

                if (str2 == awakeAppPackageName && str3 == awakeApp) {
                    if (TextUtils.isEmpty(awakeType) || TextUtils.isEmpty(awakeAppPackageName) ||
                        TextUtils.isEmpty(awakeApp) || TextUtils.isEmpty(awakenedPackageName)
                    ) {
                        AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message with empty type")
                        return@addOneShootJob
                    }

                    setPackageName(awakeAppPackageName)
                    setAppId(awakeApp)

                    val awakeInfo = AwakeInfo().apply {
                        setAction(action)
                        setTargetPackageName(awakenedPackageName)
                        setAwakeForeground(awakeForeground)
                        setAwakeInfo(str)
                    }

                    when {
                        "service" == awakeType -> {
                            if (TextUtils.isEmpty(action)) {
                                awakeInfo.setClassName("com.xiaomi.mipush.sdk.PushMessageHandler")
                                doAwake(HelpType.SERVICE_COMPONENT, context, awakeInfo)
                            } else {
                                doAwake(HelpType.SERVICE_ACTION, context, awakeInfo)
                            }
                        }
                        HelpType.ACTIVITY.typeValue == awakeType -> {
                            doAwake(HelpType.ACTIVITY, context, awakeInfo)
                        }
                        HelpType.PROVIDER.typeValue == awakeType -> {
                            doAwake(HelpType.PROVIDER, context, awakeInfo)
                        }
                        else -> {
                            AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message with unknown type $awakeType")
                        }
                    }
                } else {
                    AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message with incorrect package info$awakeAppPackageName")
                }
            } catch (e: JSONException) {
                MyLog.e(e)
                AwakeUploadHelper.uploadData(context, str, 1008, "A meet a exception when receive the message")
            }
        }
    }

    companion object {
        @Volatile
        private var sInstance: AwakeManager? = null

        @JvmStatic
        fun getInstance(context: Context): AwakeManager {
            return sInstance ?: synchronized(AwakeManager::class.java) {
                sInstance ?: AwakeManager(context).also { sInstance = it }
            }
        }

        @JvmStatic
        fun isMeForeground(context: Context): Boolean {
            return MIPushNotificationHelper.isApplicationForeground(context, context.packageName)
        }
    }
}
