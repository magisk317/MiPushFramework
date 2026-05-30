package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.android.PreferenceUtils
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.msa.MsaIdManager
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.clientreport.manager.ClientReportClient
import com.xiaomi.clientreport.manager.ClientReportLogicManager
import com.xiaomi.push.mpcd.CDActionProviderHolder
import com.xiaomi.push.mpcd.CDEntrance
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushVersionInfo
import com.xiaomi.push.service.clientReport.MIPushEventDataProcessor
import com.xiaomi.push.service.clientReport.MIPushPerfDataProcessor
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.push.service.receivers.NetworkStatusReceiver
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.RegistrationReason
import com.xiaomi.xmpush.thrift.XmPushActionCommand
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionSubscription
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscription
import java.util.TimeZone

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/MiPushClient.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
abstract class MiPushClient {
    class CodeResult {
        private var resultCode: Long = -1

        fun getResultCode(): Long = resultCode

        fun setResultCode(resultCode: Long) {
            this.resultCode = resultCode
        }
    }

    interface ICallbackResult<R> {
        fun onResult(result: R)
    }

    abstract class MiPushClientCallback {
        var category: String? = null

        open fun onCommandResult(command: String?, resultCode: Long, reason: String?, arguments: List<String>?) {}
        open fun onInitializeResult(resultCode: Long, reason: String?, regId: String?) {}
        open fun onReceiveMessage(miPushMessage: MiPushMessage?) {}
        open fun onReceiveMessage(content: String?, alias: String?, topic: String?, isNotified: Boolean) {}
        open fun onSubscribeResult(resultCode: Long, reason: String?, topic: String?) {}
        open fun onUnsubscribeResult(resultCode: Long, reason: String?, topic: String?) {}
    }

    class TokenResult {
        private var token: String? = null
        private var resultCode: Long = -1

        fun getResultCode(): Long = resultCode

        fun getToken(): String? = token

        fun setResultCode(resultCode: Long) {
            this.resultCode = resultCode
        }

        fun setToken(token: String?) {
            this.token = token
        }
    }

    interface UPSRegisterCallBack : ICallbackResult<TokenResult>
    interface UPSTurnCallBack : ICallbackResult<CodeResult>
    interface UPSUnRegisterCallBack : ICallbackResult<TokenResult>

    companion object {
        private const val CD_DELAY = 10
        private const val CHECK_VERSION_DELAY = 5
        const val COMMAND_REGISTER = "register"
        const val COMMAND_SET_ACCEPT_TIME = "accept-time"
        const val COMMAND_SET_ACCOUNT = "set-account"
        const val COMMAND_SET_ALIAS = "set-alias"
        const val COMMAND_SUBSCRIBE_TOPIC = "subscribe-topic"
        const val COMMAND_UNREGISTER = "unregister"
        const val COMMAND_UNSET_ACCOUNT = "unset-account"
        const val COMMAND_UNSET_ALIAS = "unset-alias"
        const val COMMAND_UNSUBSCRIBE_TOPIC = "unsubscibe-topic"
        private const val DEFAULT_ACCEPT_TIME = "00:00-23:59"
        private const val LAST_PULL_NOTIFICATION = "last_pull_notification"
        private const val LAST_REG_REQUEST = "last_reg_request"
        private const val PREFIX_ACCOUNT = "account_"
        private const val PREFIX_ALIAS = "alias_"
        private const val PREFIX_TOPIC = "topic_"
        const val PREF_EXTRA = "mipush_extra"
        private const val TOPIC_ALL = "**ALL**"

        private lateinit var sContext: Context
        private var sCurMsgId = System.currentTimeMillis()

        private fun acceptTimeSet(context: Context, start: String, end: String): Boolean {
            return TextUtils.equals(getAcceptTime(context), "$start,$end")
        }

        @JvmStatic
        fun accountSetTime(context: Context, account: String?): Long {
            return context.getSharedPreferences(PREF_EXTRA, 0).getLong(PREFIX_ACCOUNT + account, -1L)
        }

        @JvmStatic
        fun addAcceptTime(context: Context, start: String, end: String) {
            synchronized(MiPushClient::class.java) {
                val editor = context.getSharedPreferences(PREF_EXTRA, 0).edit()
                editor.putString(Constants.EXTRA_KEY_ACCEPT_TIME, "$start,$end")
                SharedPrefsCompat.apply(editor)
            }
        }

        @JvmStatic
        fun addAccount(context: Context, account: String?) {
            synchronized(MiPushClient::class.java) {
                context.getSharedPreferences(PREF_EXTRA, 0).edit()
                    .putLong(PREFIX_ACCOUNT + account, System.currentTimeMillis())
                    .commit()
            }
        }

        @JvmStatic
        fun addAlias(context: Context, alias: String?) {
            synchronized(MiPushClient::class.java) {
                context.getSharedPreferences(PREF_EXTRA, 0).edit()
                    .putLong(PREFIX_ALIAS + alias, System.currentTimeMillis())
                    .commit()
            }
        }

        private fun addPullNotificationTime(context: Context) {
            val editor = context.getSharedPreferences(PREF_EXTRA, 0).edit()
            editor.putLong(LAST_PULL_NOTIFICATION, System.currentTimeMillis())
            SharedPrefsCompat.apply(editor)
        }

        private fun addRegRequestTime(context: Context) {
            val editor = context.getSharedPreferences(PREF_EXTRA, 0).edit()
            editor.putLong(LAST_REG_REQUEST, System.currentTimeMillis())
            SharedPrefsCompat.apply(editor)
        }

        @JvmStatic
        fun addTopic(context: Context, topic: String?) {
            synchronized(MiPushClient::class.java) {
                context.getSharedPreferences(PREF_EXTRA, 0).edit()
                    .putLong(PREFIX_TOPIC + topic, System.currentTimeMillis())
                    .commit()
            }
        }

        @JvmStatic
        fun aliasSetTime(context: Context, alias: String?): Long {
            return context.getSharedPreferences(PREF_EXTRA, 0).getLong(PREFIX_ALIAS + alias, -1L)
        }

        @JvmStatic
        fun awakeApps(context: Context, packages: Array<String>) {
            ScheduledJobManager.getInstance(context).addOneShootJob {
                try {
                    for (pkg in packages) {
                        if (!TextUtils.isEmpty(pkg)) {
                            val packageInfo = context.packageManager.getPackageInfo(pkg, 4)
                            if (packageInfo != null) {
                                awakePushServiceByPackageInfo(context, packageInfo)
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    MyLog.e(throwable)
                }
            }
        }

        private fun awakePushServiceByPackageInfo(context: Context, packageInfo: PackageInfo) {
            val services = packageInfo.services
            if (services != null) {
                for (serviceInfo in services) {
                    if (
                        serviceInfo.exported &&
                        serviceInfo.enabled &&
                        "com.xiaomi.mipush.sdk.PushMessageHandler" == serviceInfo.name &&
                        context.packageName != serviceInfo.packageName
                    ) {
                        try {
                            Thread.sleep(((Math.random() * 2.0) + 1.0).toLong() * 1000L)
                            val intent = Intent().apply {
                                setClassName(serviceInfo.packageName, serviceInfo.name)
                                action = PushConstants.ACTION_WAKEUP
                                putExtra(PushConstants.ACTION_WAKER_PKGNAME, context.packageName)
                            }
                            PushMessageHandler.addJob(context, intent)
                            return
                        } catch (_: Throwable) {
                            return
                        }
                    }
                }
            }
        }

        private fun checkNotNull(obj: Any?, obj2: Any?) {
        }

        private fun checkNotNull(obj: Any?, name: String) {
            if (obj == null) {
                throw IllegalArgumentException("param $name is not nullable")
            }
        }

        @JvmStatic
        fun clearExtras(context: Context) {
            context.getSharedPreferences(PREF_EXTRA, 0).edit().clear().commit()
        }

        @JvmStatic
        fun clearLocalNotificationType(context: Context) {
            PushServiceClient.getInstance(context).clearLocalNotificationType()
        }

        @JvmStatic
        fun clearNotification(context: Context) {
            PushServiceClient.getInstance(context).clearNotification(-1)
        }

        @JvmStatic
        fun clearNotification(context: Context, notifyId: Int) {
            PushServiceClient.getInstance(context).clearNotification(notifyId)
        }

        @JvmStatic
        fun clearNotification(context: Context, title: String?, description: String?) {
            PushServiceClient.getInstance(context).clearNotification(title ?: "", description ?: "")
        }

        @JvmStatic
        fun disablePush(context: Context) {
            PushServiceClient.getInstance(context).sendPushEnableDisableMessage(true)
        }

        @JvmStatic
        fun enablePush(context: Context) {
            PushServiceClient.getInstance(context).sendPushEnableDisableMessage(false)
        }

        @JvmStatic
        fun getAcceptTime(context: Context): String {
            return context.getSharedPreferences(PREF_EXTRA, 0)
                .getString(Constants.EXTRA_KEY_ACCEPT_TIME, DEFAULT_ACCEPT_TIME) ?: DEFAULT_ACCEPT_TIME
        }

        @JvmStatic
        fun getAllAlias(context: Context): List<String> {
            val aliases = ArrayList<String>()
            for (key in context.getSharedPreferences(PREF_EXTRA, 0).all.keys) {
                if (key.startsWith(PREFIX_ALIAS)) {
                    aliases.add(key.substring(PREFIX_ALIAS.length))
                }
            }
            return aliases
        }

        @JvmStatic
        fun getAllTopic(context: Context): List<String> {
            val topics = ArrayList<String>()
            for (key in context.getSharedPreferences(PREF_EXTRA, 0).all.keys) {
                if (key.startsWith(PREFIX_TOPIC) && !key.contains(TOPIC_ALL)) {
                    topics.add(key.substring(PREFIX_TOPIC.length))
                }
            }
            return topics
        }

        @JvmStatic
        fun getAllUserAccount(context: Context): List<String> {
            val accounts = ArrayList<String>()
            for (key in context.getSharedPreferences(PREF_EXTRA, 0).all.keys) {
                if (key.startsWith(PREFIX_ACCOUNT)) {
                    accounts.add(key.substring(PREFIX_ACCOUNT.length))
                }
            }
            return accounts
        }

        @JvmStatic
        fun getAppRegion(context: Context): String? {
            return if (AppInfoHolder.getInstance(context).appRegistered()) {
                AppInfoHolder.getInstance(context).appRegion
            } else {
                null
            }
        }

        private fun getDefaultSwitch(): Boolean {
            return MIUIUtils.isNotMIUI()
        }

        @JvmStatic
        fun getOpenFCMPush(context: Context): Boolean {
            checkNotNull(context, "context")
            return AssemblePushCollectionsManager.getInstance(context).getUserSwitch(AssemblePush.ASSEMBLE_PUSH_FCM)
        }

        @JvmStatic
        fun getOpenHmsPush(context: Context): Boolean {
            checkNotNull(context, "context")
            return AssemblePushCollectionsManager.getInstance(context).getUserSwitch(AssemblePush.ASSEMBLE_PUSH_HUAWEI)
        }

        @JvmStatic
        fun getOpenOPPOPush(context: Context): Boolean {
            checkNotNull(context, "context")
            return AssemblePushCollectionsManager.getInstance(context).getUserSwitch(AssemblePush.ASSEMBLE_PUSH_COS)
        }

        @JvmStatic
        fun getOpenVIVOPush(context: Context): Boolean {
            return AssemblePushCollectionsManager.getInstance(context).getUserSwitch(AssemblePush.ASSEMBLE_PUSH_FTOS)
        }

        @JvmStatic
        fun getRegId(context: Context): String {
            return if (AppInfoHolder.getInstance(context).appRegistered()) {
                AppInfoHolder.getInstance(context).regID.orEmpty()
            } else {
                ""
            }
        }

        private fun initEventPerfLogic(context: Context) {
            PushClientReportHelper.setUploader(object : PushClientReportHelper.Uploader {
                override fun uploader(context: Context, clientUploadDataItem: ClientUploadDataItem) {
                    MiTinyDataClient.upload(context, clientUploadDataItem)
                }
            })
            val config = PushClientReportHelper.getConfig(context)
            ClientReportLogicManager.getInstance(context).prepareInit(PushConstants.PUSH_VERSION_NAME)
            ClientReportClient.init(context, config, MIPushEventDataProcessor(context), MIPushPerfDataProcessor(context))
            ActivityLifecycleCallbacksForCR.forceAttachApplication(context)
            ClientReportHelper.sendConfigInfo(context, config)
            OnlineConfig.getInstance(context).addOCUpdateCallbacks(object : OnlineConfig.OCUpdateCallback(100, "perf event job update") {
                override fun onCallback() {
                    PushClientReportHelper.checkConfigChange(context)
                }
            })
        }

        @Deprecated("")
        @JvmStatic
        fun initialize(context: Context, appId: String, appToken: String, callback: MiPushClientCallback?) {
            initialize(context, appId, appToken, callback, null, null)
        }

        private fun initialize(
            context: Context,
            appId: String,
            appToken: String,
            callback: MiPushClientCallback?,
            alias: String?,
            callbackResult: ICallbackResult<*>?,
        ) {
            try {
                MyLog.init(context.applicationContext)
                MyLog.persist("sdk_version = " + PushConstants.PUSH_VERSION_NAME)
                if (callback != null) {
                    PushMessageHandler.addPushCallbackClass(callback)
                }
                if (callbackResult != null) {
                    PushMessageHandler.addUPSCallback(callbackResult)
                }
                if (SystemUtils.isDebuggable(sContext)) {
                    ManifestChecker.asynCheckManifest(sContext)
                }
                val appInfoHolder = AppInfoHolder.getInstance(sContext)
                val envChanged = appInfoHolder.envType != Constants.getEnvType()
                val sendRegRequest = shouldSendRegRequest(sContext)
                MyLog.w(
                    "registration initialize envChanged=$envChanged sendRegRequest=$sendRegRequest " +
                        appInfoHolder.registrationStateSummary(appId, appToken)
                )
                if (!envChanged && !sendRegRequest) {
                    PushServiceClient.getInstance(sContext).awakePushService()
                    MyLog.w("Could not send register message within 5s repeatedly. " + appInfoHolder.registrationStateSummary(appId, appToken))
                    return
                }
                if (envChanged || !appInfoHolder.appRegistered(appId, appToken) || appInfoHolder.invalidated()) {
                    val randomDeviceId = XMStringUtils.generateRandomString(6)
                    appInfoHolder.clear()
                    appInfoHolder.setEnvType(Constants.getEnvType())
                    appInfoHolder.putAppIDAndToken(appId, appToken, randomDeviceId)
                    MiTinyDataClient.MiTinyDataClientImp.getInstance().processPendingList(MiTinyDataClient.PENDING_REASON_APPID)
                    clearExtras(sContext)
                    clearNotification(context)
                    val registration = XmPushActionRegistration().apply {
                        id = PacketHelper.generatePacketID()
                        setAppId(appId)
                        setToken(appToken)
                        setPackageName(sContext.packageName)
                        setDeviceId(randomDeviceId)
                        val packageName = sContext.packageName
                        val versionName = AppInfoUtils.getVersionName(sContext, packageName)
                        val versionCode = AppInfoUtils.getVersionCode(sContext, packageName)
                        setAppVersion(PushVersionInfo.reportedAppVersionName(packageName, versionName))
                        setAppVersionCode(PushVersionInfo.reportedAppVersionCode(packageName, versionCode))
                        setPushSdkVersionName(PushConstants.PUSH_VERSION_NAME)
                        setPushSdkVersionCode(PushConstants.PUSH_VERSION_CODE)
                        setReason(RegistrationReason.Init)
                        if (!TextUtils.isEmpty(alias)) {
                            setAliasName(alias)
                        }
                        if (!MIUIUtils.isGlobalRegion()) {
                            val imei = DeviceInfo.quicklyGetIMEI(sContext)
                            if (!TextUtils.isEmpty(imei)) {
                                setImeiMd5(XMStringUtils.getMd5Digest(imei!!) + "," + DeviceInfo.quicklyGetSubIMEISMd5(sContext))
                            }
                        }
                        val spaceId = DeviceInfo.getSpaceId()
                        if (spaceId >= 0) {
                            setSpaceId(spaceId)
                        }
                    }
                    MyLog.w(
                        "registration request dispatch idPresent=${!TextUtils.isEmpty(registration.id)} " +
                            "envChanged=$envChanged " + appInfoHolder.registrationStateSummary(appId, appToken)
                    )
                    PushServiceClient.getInstance(sContext).register(registration, envChanged)
                    sContext.getSharedPreferences(PREF_EXTRA, 4).getBoolean(PushConstants.SP_KEY_MIPUSH_REGISTED, true)
                } else {
                    MyLog.w("registration already valid " + appInfoHolder.registrationStateSummary(appId, appToken))
                    if (1 == PushMessageHelper.getPushMode(sContext)) {
                        checkNotNull(callback, "callback")
                        callback!!.onInitializeResult(0L, null, appInfoHolder.regID)
                    } else {
                        val args = ArrayList<String>()
                        appInfoHolder.regID?.let { args.add(it) }
                        PushMessageHelper.sendCommandMessageBroadcast(
                            sContext,
                            PushMessageHelper.generateCommandMessage(Command.COMMAND_REGISTER.value, args, 0L, null, null)
                        )
                    }
                    PushServiceClient.getInstance(sContext).awakePushService()
                    if (AppInfoHolder.getInstance(sContext).checkVersionNameChanged()) {
                        val notification = XmPushActionNotification().apply {
                            setAppId(AppInfoHolder.getInstance(sContext).appID)
                            setType(NotificationType.ClientInfoUpdate.value)
                            id = PacketHelper.generatePacketID()
                            extra = HashMap()
                        }
                        val packageName = sContext.packageName
                        notification.extra!![Constants.EXTRA_KEY_APP_VERSION] =
                            PushVersionInfo.reportedAppVersionName(packageName, AppInfoUtils.getVersionName(sContext, packageName))
                        notification.extra!![Constants.EXTRA_KEY_APP_VERSION_CODE] =
                            PushVersionInfo.reportedAppVersionCode(packageName, AppInfoUtils.getVersionCode(sContext, packageName)).toString()
                        notification.extra!![PushConstants.KEY_PUSH_SDK_VERSION_NAME] = PushConstants.PUSH_VERSION_NAME
                        notification.extra!![PushConstants.KEY_PUSH_SDK_VERSION_CODE] = PushConstants.PUSH_VERSION_CODE.toString()
                        DeviceInfo.fillLocalVirtDevId(sContext, notification.extra)
                        val regResource = AppInfoHolder.getInstance(sContext).regResource
                        if (!TextUtils.isEmpty(regResource)) {
                            notification.extra!!["deviceid"] = regResource!!
                        }
                        PushServiceClient.getInstance(sContext).sendMessage(notification, ActionType.Notification, false, null)
                    }
                    if (!PreferenceUtils.getSettingBoolean(sContext, "update_devId", false)) {
                        updateImeiOrOaid()
                        PreferenceUtils.setSettingBoolean(sContext, "update_devId", true)
                    }
                    val checkedVirtDevId = DeviceInfo.checkVirtDevId(sContext)
                    if (!TextUtils.isEmpty(checkedVirtDevId)) {
                        val command = XmPushActionCommand().apply {
                            id = PacketHelper.generatePacketID()
                            setAppId(appId)
                            cmdName = Command.COMMAND_CHK_VDEVID.value
                            cmdArgs = ArrayList<String>().apply {
                                val virtDevId = DeviceInfo.getVirtDevId(sContext)
                                if (!TextUtils.isEmpty(virtDevId)) {
                                    add(virtDevId!!)
                                }
                                add(checkedVirtDevId ?: "")
                                add(Build.MODEL ?: "")
                                add(Build.BOARD ?: "")
                            }
                        }
                        PushServiceClient.getInstance(sContext).sendMessage(command, ActionType.Command, false, null)
                    }
                    if (shouldUseMIUIPush(sContext) && shouldPullNotification(sContext)) {
                        val notification = XmPushActionNotification().apply {
                            setAppId(AppInfoHolder.getInstance(sContext).appID)
                            type = NotificationType.PullOfflineMessage.value
                            id = PacketHelper.generatePacketID()
                            setRequireAck(false)
                        }
                        PushServiceClient.getInstance(sContext).sendMessage(notification, ActionType.Notification, false, null, false)
                        addPullNotificationTime(sContext)
                    }
                }
                addRegRequestTime(sContext)
                scheduleOcVersionCheckJob()
                scheduleDataCollectionJobs(sContext)
                initEventPerfLogic(sContext)
                SyncInfoHelper.tryToSyncInfo(sContext)
                if (sContext.packageName != PushConstants.PUSH_SERVICE_PACKAGE_NAME) {
                    if (Logger.getUserLogger() != null) {
                        Logger.setLogger(sContext, Logger.getUserLogger())
                    }
                    MyLog.setLogLevel(2)
                }
                operateSyncAction(context)
            } catch (throwable: Throwable) {
                MyLog.e(throwable)
            }
        }

        private fun operateSyncAction(context: Context) {
            if (OperatePushHelper.SYNCING == OperatePushHelper.getInstance(sContext).getSyncStatus(RetryType.DISABLE_PUSH)) {
                disablePush(sContext)
            }
            if (OperatePushHelper.SYNCING == OperatePushHelper.getInstance(sContext).getSyncStatus(RetryType.ENABLE_PUSH)) {
                enablePush(sContext)
            }
            if (OperatePushHelper.SYNCING == OperatePushHelper.getInstance(sContext).getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN)) {
                syncAssemblePushToken(sContext)
            }
            if (OperatePushHelper.SYNCING == OperatePushHelper.getInstance(sContext).getSyncStatus(RetryType.UPLOAD_FCM_TOKEN)) {
                syncAssembleFCMPushToken(sContext)
            }
            if (OperatePushHelper.SYNCING == OperatePushHelper.getInstance(sContext).getSyncStatus(RetryType.UPLOAD_COS_TOKEN)) {
                syncAssembleCOSPushToken(context)
            }
            if (OperatePushHelper.SYNCING == OperatePushHelper.getInstance(sContext).getSyncStatus(RetryType.UPLOAD_FTOS_TOKEN)) {
                syncAssembleFTOSPushToken(context)
            }
        }

        @JvmStatic
        fun pausePush(context: Context, category: String?) {
            setAcceptTime(context, 0, 0, 0, 0, category)
        }

        @JvmStatic
        fun reInitialize(context: Context, registrationReason: RegistrationReason) {
            if (AppInfoHolder.getInstance(context).appRegistered()) {
                val randomDeviceId = XMStringUtils.generateRandomString(6)
                val appId = AppInfoHolder.getInstance(context).appID
                val appToken = AppInfoHolder.getInstance(context).appToken
                AppInfoHolder.getInstance(context).clear()
                clearNotification(context)
                AppInfoHolder.getInstance(context).setEnvType(Constants.getEnvType())
                AppInfoHolder.getInstance(context).putAppIDAndToken(appId, appToken, randomDeviceId)
                val registration = XmPushActionRegistration().apply {
                    id = PacketHelper.generatePacketID()
                    setAppId(appId)
                    setToken(appToken)
                    setDeviceId(randomDeviceId)
                    setPackageName(context.packageName)
                    val packageName = context.packageName
                    setAppVersion(PushVersionInfo.reportedAppVersionName(packageName, AppInfoUtils.getVersionName(context, packageName)))
                    setReason(registrationReason)
                }
                PushServiceClient.getInstance(context).register(registration, false)
            }
        }

        @Deprecated("")
        @JvmStatic
        fun registerCrashHandler(uncaughtExceptionHandler: Thread.UncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
        }

        private fun registerNetworkReceiver(context: Context) {
            try {
                val intentFilter = IntentFilter().apply {
                    addAction("android.net.conn.CONNECTIVITY_CHANGE")
                    addCategory("android.intent.category.DEFAULT")
                }
                context.applicationContext.registerReceiver(NetworkStatusReceiver(null), intentFilter)
            } catch (throwable: Throwable) {
                MyLog.e(throwable)
            }
        }

        @JvmStatic
        fun registerPush(context: Context, appId: String, appToken: String) {
            registerPush(context, appId, appToken, PushConfiguration())
        }

        @JvmStatic
        fun registerPush(context: Context, appId: String, appToken: String, pushConfiguration: PushConfiguration) {
            registerPush(context, appId, appToken, pushConfiguration, null, null)
        }

        private fun registerPush(
            context: Context,
            appId: String,
            appToken: String,
            pushConfiguration: PushConfiguration,
            alias: String?,
            callbackResult: ICallbackResult<*>?,
        ) {
            checkNotNull(context, "context")
            checkNotNull(appId, "appID")
            checkNotNull(appToken, "appToken")
            val applicationContext = context.applicationContext
            sContext = applicationContext ?: context
            val actualContext = sContext
            SystemUtils.initialize(actualContext)
            if (!NetworkStatusReceiver.isRegister()) {
                registerNetworkReceiver(sContext)
            }
            AssemblePushCollectionsManager.getInstance(sContext).setConfiguration(pushConfiguration)
            ScheduledJobManager.getInstance(actualContext).addOneShootJob {
                initialize(sContext, appId, appToken, null, alias, callbackResult)
            }
        }

        @JvmStatic
        fun registerPush(context: Context, appId: String, appToken: String, alias: String?) {
            registerPush(context, appId, appToken, PushConfiguration(), alias, null)
        }

        @JvmStatic
        fun registerToken(context: Context, appId: String, appToken: String, packageName: String?, callback: UPSRegisterCallBack?) {
            registerPush(context, appId, appToken, PushConfiguration(), null, callback)
        }

        @JvmStatic
        fun removeAcceptTime(context: Context) {
            synchronized(MiPushClient::class.java) {
                val editor = context.getSharedPreferences(PREF_EXTRA, 0).edit()
                editor.remove(Constants.EXTRA_KEY_ACCEPT_TIME)
                SharedPrefsCompat.apply(editor)
            }
        }

        @JvmStatic
        fun removeAccount(context: Context, account: String?) {
            synchronized(MiPushClient::class.java) {
                context.getSharedPreferences(PREF_EXTRA, 0).edit().remove(PREFIX_ACCOUNT + account).commit()
            }
        }

        @JvmStatic
        fun removeAlias(context: Context, alias: String?) {
            synchronized(MiPushClient::class.java) {
                context.getSharedPreferences(PREF_EXTRA, 0).edit().remove(PREFIX_ALIAS + alias).commit()
            }
        }

        @JvmStatic
        fun removeAllAccounts(context: Context) {
            synchronized(MiPushClient::class.java) {
                for (account in getAllUserAccount(context)) {
                    removeAccount(context, account)
                }
            }
        }

        @JvmStatic
        fun removeAllAliases(context: Context) {
            synchronized(MiPushClient::class.java) {
                for (alias in getAllAlias(context)) {
                    removeAlias(context, alias)
                }
            }
        }

        @JvmStatic
        fun removeAllTopics(context: Context) {
            synchronized(MiPushClient::class.java) {
                for (topic in getAllTopic(context)) {
                    removeTopic(context, topic)
                }
            }
        }

        @JvmStatic
        fun removeTopic(context: Context, topic: String?) {
            synchronized(MiPushClient::class.java) {
                context.getSharedPreferences(PREF_EXTRA, 0).edit().remove(PREFIX_TOPIC + topic).commit()
            }
        }

        @JvmStatic
        fun reportAppRunInBackground(context: Context, isBackground: Boolean) {
            if (AppInfoHolder.getInstance(context).checkAppInfo()) {
                val notificationType = if (isBackground) NotificationType.APP_SLEEP else NotificationType.APP_WAKEUP
                val notification = XmPushActionNotification().apply {
                    setAppId(AppInfoHolder.getInstance(context).appID)
                    type = notificationType.value
                    packageName = context.packageName
                    id = PacketHelper.generatePacketID()
                    setRequireAck(false)
                }
                PushServiceClient.getInstance(context).sendMessage(notification, ActionType.Notification, false, null, false)
            }
        }

        @JvmStatic
        fun reportIgnoreRegMessageClicked(context: Context, id: String?, pushMetaInfo: PushMetaInfo?, packageName: String?, appId: String?) {
            val notification = XmPushActionNotification()
            if (TextUtils.isEmpty(appId)) {
                MyLog.e("do not report clicked message")
                return
            }
            notification.setAppId(appId)
            notification.type = "bar:click"
            notification.id = id
            notification.setRequireAck(false)
            PushServiceClient.getInstance(context).sendMessage(
                notification,
                ActionType.Notification,
                false,
                true,
                pushMetaInfo,
                true,
                packageName ?: "",
                appId
            )
        }

        @JvmStatic
        fun reportMessageClicked(context: Context, miPushMessage: MiPushMessage) {
            val pushMetaInfo = PushMetaInfo().apply {
                id = miPushMessage.messageId
                topic = miPushMessage.topic
                description = miPushMessage.description
                title = miPushMessage.title
                notifyId = miPushMessage.notifyId
                notifyType = miPushMessage.notifyType
                passThrough = miPushMessage.passThrough
                extra = miPushMessage.extra
            }
            reportMessageClicked(context, miPushMessage.messageId, pushMetaInfo, null)
        }

        @Deprecated("")
        @JvmStatic
        fun reportMessageClicked(context: Context, messageId: String?) {
            reportMessageClicked(context, messageId, null, null)
        }

        @JvmStatic
        fun reportMessageClicked(context: Context, messageId: String?, pushMetaInfo: PushMetaInfo?, appId: String?) {
            val notification = XmPushActionNotification()
            if (!TextUtils.isEmpty(appId)) {
                notification.setAppId(appId)
            } else {
                if (!AppInfoHolder.getInstance(context).checkAppInfo()) {
                    MyLog.e("do not report clicked message")
                    return
                }
                notification.setAppId(AppInfoHolder.getInstance(context).appID)
            }
            notification.type = "bar:click"
            notification.id = messageId
            notification.setRequireAck(false)
            PushServiceClient.getInstance(context).sendMessage(notification, ActionType.Notification, false, pushMetaInfo)
        }

        @JvmStatic
        fun resumePush(context: Context, category: String?) {
            setAcceptTime(context, 0, 0, 23, 59, category)
        }

        private fun scheduleDataCollectionJobs(context: Context) {
            if (OnlineConfig.getInstance(sContext).getBooleanValue(ConfigKey.DataCollectionSwitch.value, getDefaultSwitch())) {
                CDActionProviderHolder.getInstance().setCDActionProvider(CDActionProviderImpl(context))
                ScheduledJobManager.getInstance(sContext).addOneShootJob({
                    CDEntrance.start(sContext)
                }, CD_DELAY)
            }
        }

        private fun scheduleOcVersionCheckJob() {
            ScheduledJobManager.getInstance(sContext).addRepeatJob(
                OcVersionCheckJob(sContext),
                OnlineConfig.getInstance(sContext).getIntValue(ConfigKey.OcVersionCheckFrequency.value, 86400),
                CHECK_VERSION_DELAY
            )
        }

        @JvmStatic
        fun setAcceptTime(context: Context, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, category: String?) {
            if (startHour < 0 || startHour >= 24 || endHour < 0 || endHour >= 24 || startMinute < 0 || startMinute >= 60 || endMinute < 0 || endMinute >= 60) {
                throw IllegalArgumentException("the input parameter is not valid.")
            }
            val rawOffset = ((TimeZone.getTimeZone("GMT+08").rawOffset - TimeZone.getDefault().rawOffset) / 1000) / 60
            val start = (((startHour * 60 + startMinute).toLong() + rawOffset) + 1440) % 1440
            val end = (((endHour * 60 + endMinute).toLong() + rawOffset) + 1440) % 1440
            val serverArgs = ArrayList<String>().apply {
                add(String.format("%1\$02d:%2\$02d", start / 60, start % 60))
                add(String.format("%1\$02d:%2\$02d", end / 60, end % 60))
            }
            val localArgs = ArrayList<String>().apply {
                add(String.format("%1\$02d:%2\$02d", startHour, startMinute))
                add(String.format("%1\$02d:%2\$02d", endHour, endMinute))
            }
            if (!acceptTimeSet(context, serverArgs[0], serverArgs[1])) {
                setCommand(context, Command.COMMAND_SET_ACCEPT_TIME.value, serverArgs, category)
            } else if (1 == PushMessageHelper.getPushMode(context)) {
                PushMessageHandler.onCommandResult(context, category ?: "", Command.COMMAND_SET_ACCEPT_TIME.value, 0L, null, localArgs)
            } else {
                PushMessageHelper.sendCommandMessageBroadcast(
                    context,
                    PushMessageHelper.generateCommandMessage(Command.COMMAND_SET_ACCEPT_TIME.value, localArgs, 0L, null, null)
                )
            }
        }

        @JvmStatic
        fun setAlias(context: Context, alias: String?, category: String?) {
            if (!TextUtils.isEmpty(alias)) {
                setCommand(context, Command.COMMAND_SET_ALIAS.value, alias, category)
            }
        }

        @Deprecated("")
        @JvmStatic
        fun setAwakeServiceEnabled(enabled: Boolean) {
        }

        @JvmStatic
        fun setCommand(context: Context, command: String, argument: String?, category: String?) {
            val arguments = ArrayList<String>()
            if (!TextUtils.isEmpty(argument)) {
                arguments.add(argument!!)
            }
            if (Command.COMMAND_SET_ALIAS.value.equals(command, ignoreCase = true) && Math.abs(System.currentTimeMillis() - aliasSetTime(context, argument)) < 86400000) {
                if (1 == PushMessageHelper.getPushMode(context)) {
                    PushMessageHandler.onCommandResult(context, category ?: "", command, 0L, null, arguments)
                    return
                } else {
                    PushMessageHelper.sendCommandMessageBroadcast(
                        context,
                        PushMessageHelper.generateCommandMessage(Command.COMMAND_SET_ALIAS.value, arguments, 0L, null, category)
                    )
                    return
                }
            }
            if (Command.COMMAND_UNSET_ALIAS.value.equals(command, ignoreCase = true) && aliasSetTime(context, argument) < 0) {
                MyLog.w("Don't cancel alias for " + XMStringUtils.obfuscateString(arguments.toString(), 3) + " is unseted")
                return
            }
            if (Command.COMMAND_SET_ACCOUNT.value.equals(command, ignoreCase = true) && Math.abs(System.currentTimeMillis() - accountSetTime(context, argument)) < 3600000) {
                if (1 == PushMessageHelper.getPushMode(context)) {
                    PushMessageHandler.onCommandResult(context, category ?: "", command, 0L, null, arguments)
                    return
                } else {
                    PushMessageHelper.sendCommandMessageBroadcast(
                        context,
                        PushMessageHelper.generateCommandMessage(Command.COMMAND_SET_ACCOUNT.value, arguments, 0L, null, category)
                    )
                    return
                }
            }
            if (!Command.COMMAND_UNSET_ACCOUNT.value.equals(command, ignoreCase = true) || accountSetTime(context, argument) >= 0) {
                setCommand(context, command, arguments, category)
                return
            }
            MyLog.w("Don't cancel account for " + XMStringUtils.obfuscateString(arguments.toString(), 3) + " is unseted")
        }

        @JvmStatic
        fun setCommand(context: Context, commandName: String, arguments: ArrayList<String>, category: String?) {
            if (TextUtils.isEmpty(AppInfoHolder.getInstance(context).appID)) {
                return
            }
            val packetId = PacketHelper.generatePacketID()
            val command = XmPushActionCommand().apply {
                id = packetId
                setAppId(AppInfoHolder.getInstance(context).appID)
                cmdName = commandName
                for (argument in arguments) {
                    addToCmdArgs(argument)
                }
                setCategory(category)
                packageName = context.packageName
            }
            MyLog.persist("cmd:$commandName, $packetId")
            PushServiceClient.getInstance(context).sendMessage(command, ActionType.Command, null)
        }

        @JvmStatic
        fun setLocalNotificationType(context: Context, notifyType: Int) {
            PushServiceClient.getInstance(context).setLocalNotificationType(notifyType and -1)
        }

        @JvmStatic
        fun setUserAccount(context: Context, account: String?, category: String?) {
            if (!TextUtils.isEmpty(account)) {
                setCommand(context, Command.COMMAND_SET_ACCOUNT.value, account, category)
            }
        }

        private fun shouldPullNotification(context: Context): Boolean {
            return Math.abs(System.currentTimeMillis() - context.getSharedPreferences(PREF_EXTRA, 0).getLong(LAST_PULL_NOTIFICATION, -1L)) >
                Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL
        }

        private fun shouldSendRegRequest(context: Context): Boolean {
            return Math.abs(System.currentTimeMillis() - context.getSharedPreferences(PREF_EXTRA, 0).getLong(LAST_REG_REQUEST, -1L)) > 5000
        }

        @JvmStatic
        fun shouldUseMIUIPush(context: Context): Boolean {
            return PushServiceClient.getInstance(context).shouldUseMIUIPush()
        }

        @JvmStatic
        fun subscribe(context: Context, topic: String?, category: String?) {
            if (TextUtils.isEmpty(AppInfoHolder.getInstance(context).appID) || TextUtils.isEmpty(topic)) {
                return
            }
            if (Math.abs(System.currentTimeMillis() - topicSubscribedTime(context, topic)) <= 86400000) {
                if (1 == PushMessageHelper.getPushMode(context)) {
                    PushMessageHandler.onSubscribeResult(context, category ?: "", 0L, null, topic)
                    return
                }
                val args = ArrayList<String>().apply { add(topic!!) }
                PushMessageHelper.sendCommandMessageBroadcast(
                    context,
                    PushMessageHelper.generateCommandMessage(Command.COMMAND_SUBSCRIBE_TOPIC.value, args, 0L, null, null)
                )
                return
            }
            val packetId = PacketHelper.generatePacketID()
            val subscription = XmPushActionSubscription().apply {
                id = packetId
                setAppId(AppInfoHolder.getInstance(context).appID)
                setTopic(topic)
                packageName = context.packageName
                setCategory(category)
            }
            MyLog.persist("cmd:" + Command.COMMAND_SUBSCRIBE_TOPIC + ", " + packetId)
            PushServiceClient.getInstance(context).sendMessage(subscription, ActionType.Subscription, null)
        }

        @JvmStatic
        fun syncAssembleCOSPushToken(context: Context) {
            PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, RetryType.UPLOAD_COS_TOKEN, AssemblePush.ASSEMBLE_PUSH_COS)
        }

        @JvmStatic
        fun syncAssembleFCMPushToken(context: Context) {
            PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, RetryType.UPLOAD_FCM_TOKEN, AssemblePush.ASSEMBLE_PUSH_FCM)
        }

        @JvmStatic
        fun syncAssembleFTOSPushToken(context: Context) {
            PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, RetryType.UPLOAD_FTOS_TOKEN, AssemblePush.ASSEMBLE_PUSH_FTOS)
        }

        @JvmStatic
        fun syncAssemblePushToken(context: Context) {
            PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, RetryType.UPLOAD_HUAWEI_TOKEN, AssemblePush.ASSEMBLE_PUSH_HUAWEI)
        }

        @JvmStatic
        fun topicSubscribedTime(context: Context, topic: String?): Long {
            return context.getSharedPreferences(PREF_EXTRA, 0).getLong(PREFIX_TOPIC + topic, -1L)
        }

        @JvmStatic
        fun turnOffPush(context: Context, callback: UPSTurnCallBack?) {
            disablePush(context)
            if (callback != null) {
                val codeResult = CodeResult()
                codeResult.setResultCode(0L)
                codeResult.getResultCode()
                callback.onResult(codeResult)
            }
        }

        @JvmStatic
        fun turnOnPush(context: Context, callback: UPSTurnCallBack?) {
            enablePush(context)
            if (callback != null) {
                val codeResult = CodeResult()
                codeResult.setResultCode(0L)
                codeResult.getResultCode()
                callback.onResult(codeResult)
            }
        }

        @JvmStatic
        fun unRegisterToken(context: Context, callback: UPSUnRegisterCallBack?) {
            unregisterPush(context)
            if (callback != null) {
                val tokenResult = TokenResult()
                tokenResult.setToken(null)
                tokenResult.getToken()
                tokenResult.setResultCode(0L)
                tokenResult.getResultCode()
                callback.onResult(tokenResult)
            }
        }

        @JvmStatic
        fun unregisterPush(context: Context) {
            AssemblePushHelper.unregisterAssemblePush(context)
            OnlineConfig.getInstance(context).clearCallbacks()
            if (AppInfoHolder.getInstance(context).checkAppInfo()) {
                val unRegistration = XmPushActionUnRegistration().apply {
                    id = PacketHelper.generatePacketID()
                    setAppId(AppInfoHolder.getInstance(context).appID)
                    setRegId(AppInfoHolder.getInstance(context).regID)
                    setToken(AppInfoHolder.getInstance(context).appToken)
                    packageName = context.packageName
                }
                PushServiceClient.getInstance(context).unregister(unRegistration)
                PushMessageHandler.removeAllPushCallbackClass()
                PushMessageHandler.removeAllUPSCallback()
                AppInfoHolder.getInstance(context).invalidate()
                clearLocalNotificationType(context)
                clearNotification(context)
                clearExtras(context)
            }
        }

        @JvmStatic
        fun unsetAlias(context: Context, alias: String?, category: String?) {
            setCommand(context, Command.COMMAND_UNSET_ALIAS.value, alias, category)
        }

        @JvmStatic
        fun unsetUserAccount(context: Context, account: String?, category: String?) {
            setCommand(context, Command.COMMAND_UNSET_ACCOUNT.value, account, category)
        }

        @JvmStatic
        fun unsubscribe(context: Context, topic: String?, category: String?) {
            if (AppInfoHolder.getInstance(context).checkAppInfo()) {
                if (topicSubscribedTime(context, topic) < 0) {
                    MyLog.w("Don't cancel subscribe for $topic is unsubscribed")
                    return
                }
                val packetId = PacketHelper.generatePacketID()
                val unSubscription = XmPushActionUnSubscription().apply {
                    id = packetId
                    setAppId(AppInfoHolder.getInstance(context).appID)
                    setTopic(topic)
                    packageName = context.packageName
                    setCategory(category)
                }
                MyLog.persist("cmd:" + Command.COMMAND_UNSUBSCRIBE_TOPIC + ", " + packetId)
                PushServiceClient.getInstance(context).sendMessage(unSubscription, ActionType.UnSubscription, null)
            }
        }

        private fun updateImeiOrOaid() {
            Thread {
                if (MIUIUtils.isGlobalRegion()) {
                    return@Thread
                }
                if (DeviceInfo.blockingGetIMEI(sContext) != null || MsaIdManager.getInstance(sContext).isSupported()) {
                    val notification = XmPushActionNotification().apply {
                        setAppId(AppInfoHolder.getInstance(sContext).appID)
                        type = NotificationType.ClientInfoUpdate.value
                        id = PacketHelper.generatePacketID()
                        extra = HashMap()
                    }
                    var imeiMd5 = ""
                    val imei = DeviceInfo.blockingGetIMEI(sContext)
                    if (!TextUtils.isEmpty(imei)) {
                        imeiMd5 = "" + XMStringUtils.getMd5Digest(imei!!)
                    }
                    val subImeiMd5 = DeviceInfo.blockingGetSubIMEISMd5(sContext)
                    var imeiListMd5 = imeiMd5
                    if (!TextUtils.isEmpty(imeiMd5) && !TextUtils.isEmpty(subImeiMd5)) {
                        imeiListMd5 = "$imeiMd5,$subImeiMd5"
                    }
                    if (!TextUtils.isEmpty(imeiListMd5)) {
                        notification.extra!![Constants.EXTRA_KEY_IMEI_MD5] = imeiListMd5
                    }
                    MsaIdManager.getInstance(sContext).fillData(notification.extra)
                    DeviceInfo.fillLocalVirtDevId(sContext, notification.extra)
                    val spaceId = DeviceInfo.getSpaceId()
                    if (spaceId >= 0) {
                        notification.extra!!["space_id"] = spaceId.toString()
                    }
                    PushServiceClient.getInstance(sContext).sendMessage(notification, ActionType.Notification, false, null)
                }
            }.start()
        }
    }
}
