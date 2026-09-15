package com.xiaomi.push.service.awake.module

import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.awake.AwakeUploadHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
@android.annotation.SuppressLint("StaticFieldLeak")
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
        if (str.isEmpty() || str2.isEmpty() || str3.isEmpty()) {
            AwakeUploadHelper.uploadData(context, str, 1008, "A receive a incorrect message")
            return
        }

        setOnLineCmd(i)
        ScheduledJobManager.getInstance(mContext).addOneShootJob {
            if (str.isEmpty()) {
                AwakeUploadHelper.uploadData(context, "null", 1008, "A receive a incorrect message with empty info")
                return@addOneShootJob
            }
            try {
                AwakeUploadHelper.uploadData(context, str, 1001, "get message")
                val root = Json.parseToJsonElement(str).jsonObject
                val action = root["action"]?.jsonPrimitive?.content.orEmpty()
                val awakenedPackageName = root["awakened_app_packagename"]?.jsonPrimitive?.content.orEmpty()
                val awakeAppPackageName = root["awake_app_packagename"]?.jsonPrimitive?.content.orEmpty()
                val awakeApp = root["awake_app"]?.jsonPrimitive?.content.orEmpty()
                val awakeType = root["awake_type"]?.jsonPrimitive?.content.orEmpty()
                val awakeForeground = root["awake_foreground"]?.jsonPrimitive?.intOrNull ?: 0

                if (str2 == awakeAppPackageName && str3 == awakeApp) {
                    if (awakeType.isEmpty() || awakeAppPackageName.isEmpty() ||
                        awakeApp.isEmpty() || awakenedPackageName.isEmpty()
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
                            if (action.isEmpty()) {
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
            } catch (e: Exception) {
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
