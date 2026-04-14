package com.xiaomi.mipush.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.XMPushService
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.push.clientreport.PerfMessageHelper
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushProvision
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.BootModeType
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.ConfigKey
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase

class PushServiceClient private constructor(context: Context) {
    companion object {
        private const val MAX_PENDING_MESSAGES_SIZE = 50
        private const val MIN_MIUI_PUSH_BIND_SERVICE_VERSION = 108
        private const val MIN_MIUI_PUSH_VERSION = 105
        private const val MIN_MIUI_PUSH_VERSION_JAR = 106
        private const val REQUEST_CACHE_SIZE = 10
        private const val isForHybridFrame = false

        @Volatile
        private var sInstance: PushServiceClient? = null
        private var isBind = false
        private val sPendingRequest = ArrayList<BufferedRequest>()

        @JvmStatic
        fun getInstance(context: Context): PushServiceClient {
            return sInstance ?: synchronized(PushServiceClient::class.java) {
                sInstance ?: PushServiceClient(context).also { sInstance = it }
            }
        }
    }

    private val handler: Handler
    private var mClientMessenger: Messenger? = null
    private val mContext: Context = context.applicationContext
    private var mIsMiuiPushServiceEnabled = false
    private val pendingMessages = ArrayList<Message>()
    private var isConnectingService = false
    private var registerTask: Intent? = null
    private var mDeviceProvisioned: Int? = null
    private var mSession: String? = null

    interface PendingRequestDispatcher {
        fun dispatch(client: PushServiceClient)
    }

    class BufferedRequest {
        var dispatcher: PendingRequestDispatcher? = null
    }

    init {
        mIsMiuiPushServiceEnabled = serviceInstalled()
        isBind = useBind()
        handler = object : Handler(Looper.getMainLooper()) {
            override fun dispatchMessage(message: Message) {
                if (message.what == 19) {
                    val str = message.obj as String
                    val i = message.arg1
                    synchronized(OperatePushHelper::class.java) {
                        val helper = OperatePushHelper.getInstance(mContext)
                        if (helper.isMessageOperating(str)) {
                            if (helper.getRetryCount(str) < 10) {
                                when {
                                    i == RetryType.DISABLE_PUSH.ordinal && OperatePushHelper.SYNCING == helper.getSyncStatus(RetryType.DISABLE_PUSH) ->
                                        retryPolicy(str, RetryType.DISABLE_PUSH, true, null)
                                    i == RetryType.ENABLE_PUSH.ordinal && OperatePushHelper.SYNCING == helper.getSyncStatus(RetryType.ENABLE_PUSH) ->
                                        retryPolicy(str, RetryType.ENABLE_PUSH, true, null)
                                    i == RetryType.UPLOAD_HUAWEI_TOKEN.ordinal && OperatePushHelper.SYNCING == helper.getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN) ->
                                        retryPolicy(str, RetryType.UPLOAD_HUAWEI_TOKEN, false, getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_HUAWEI))
                                    i == RetryType.UPLOAD_FCM_TOKEN.ordinal && OperatePushHelper.SYNCING == helper.getSyncStatus(RetryType.UPLOAD_FCM_TOKEN) ->
                                        retryPolicy(str, RetryType.UPLOAD_FCM_TOKEN, false, getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_FCM))
                                    i == RetryType.UPLOAD_COS_TOKEN.ordinal && OperatePushHelper.SYNCING == helper.getSyncStatus(RetryType.UPLOAD_COS_TOKEN) ->
                                        retryPolicy(str, RetryType.UPLOAD_COS_TOKEN, false, getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_COS))
                                    i == RetryType.UPLOAD_FTOS_TOKEN.ordinal && OperatePushHelper.SYNCING == helper.getSyncStatus(RetryType.UPLOAD_FTOS_TOKEN) ->
                                        retryPolicy(str, RetryType.UPLOAD_FTOS_TOKEN, false, getAssemblePushExtraOrNull(AssemblePush.ASSEMBLE_PUSH_FTOS))
                                }
                                helper.increaseRetryCount(str)
                            } else {
                                helper.removeOperateMessage(str)
                            }
                        }
                    }
                }
            }
        }
        val intent = createGlobalServiceIntent()
        if (intent != null) {
            startServiceSafely(intent)
        }
    }

    private fun bindServiceSafely(intent: Intent) {
        synchronized(this) {
            if (isConnectingService) {
                val toMessage = parseToMessage(intent)
                if (pendingMessages.size >= MAX_PENDING_MESSAGES_SIZE) {
                    pendingMessages.removeAt(0)
                }
                pendingMessages.add(toMessage)
                return
            }
            if (mClientMessenger == null) {
                mContext.bindService(intent, object : ServiceConnection {
                    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                        synchronized(this@PushServiceClient) {
                            mClientMessenger = Messenger(iBinder)
                            isConnectingService = false
                            for (msg in pendingMessages) {
                                try {
                                    mClientMessenger?.send(msg)
                                } catch (e: RemoteException) {
                                    MyLog.e(e)
                                }
                            }
                            pendingMessages.clear()
                        }
                    }

                    override fun onServiceDisconnected(componentName: ComponentName) {
                        mClientMessenger = null
                        isConnectingService = false
                    }
                }, 1)
                isConnectingService = true
                pendingMessages.clear()
                pendingMessages.add(parseToMessage(intent))
            } else {
                try {
                    mClientMessenger?.send(parseToMessage(intent))
                } catch (e: RemoteException) {
                    mClientMessenger = null
                    isConnectingService = false
                }
            }
        }
    }

    private fun callService(intent: Intent) {
        val intValue = OnlineConfig.getInstance(mContext).getIntValue(ConfigKey.ServiceBootMode.value, BootModeType.START.value)
        val serviceBootMode = serviceBootMode
        val z = intValue == BootModeType.BIND.value && isBind
        val value = if (z) BootModeType.BIND.value else BootModeType.START.value
        if (value != serviceBootMode) {
            sendServiceBootMode(value)
        }
        if (z) {
            bindServiceSafely(intent)
        } else {
            startServiceSafely(intent)
        }
    }

    private fun createGlobalServiceIntent(): Intent? {
        return if (PushConstants.PUSH_SERVICE_PACKAGE_NAME != mContext.packageName) {
            createGlobalServiceIntentForApp()
        } else {
            MyLog.v("pushChannel xmsf create own channel")
            createMyPushChannelIntent()
        }
    }

    private fun createGlobalServiceIntentForApp(): Intent {
        return if (shouldUseMIUIPush()) {
            MyLog.v("pushChannel app start miui china channel")
            createMIUIPushChannelIntent()
        } else {
            MyLog.v("pushChannel app start  own channel")
            createMyPushChannelIntent()
        }
    }

    private fun createMIUIPushChannelIntent(): Intent {
        return Intent().apply {
            val packageName = mContext.packageName
            setPackage(PushConstants.PUSH_SERVICE_PACKAGE_NAME)
            setClassName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, pushServiceName)
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
            disableMyPushService()
        }
    }

    private fun createMyPushChannelIntent(): Intent {
        return Intent().apply {
            val packageName = mContext.packageName
            enableMyPushService()
            component = ComponentName(mContext, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
            putExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE, packageName)
        }
    }

    private fun createServiceIntent(): Intent {
        return if (!shouldUseMIUIPush() || PushConstants.PUSH_SERVICE_PACKAGE_NAME == mContext.packageName) {
            createMyPushChannelIntent()
        } else {
            createMIUIPushChannelIntent()
        }
    }

    private fun disableMyPushService() {
        try {
            val packageManager = mContext.packageManager
            val componentName = ComponentName(mContext, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
            if (packageManager.getComponentEnabledSetting(componentName) == 2) return
            packageManager.setComponentEnabledSetting(componentName, 2, 1)
        } catch (th: Throwable) {
        }
    }

    private fun enableMyPushService() {
        try {
            val packageManager = mContext.packageManager
            val componentName = ComponentName(mContext, PushConstants.PUSH_SERVICE_CLASS_NAME_JAR)
            if (packageManager.getComponentEnabledSetting(componentName) == 1) return
            packageManager.setComponentEnabledSetting(componentName, 1, 1)
        } catch (th: Throwable) {
        }
    }

    private val pushServiceName: String
        get() {
            return try {
                if (mContext.packageManager.getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4).longVersionCode >= 106) {
                    PushConstants.PUSH_SERVICE_CLASS_NAME_JAR
                } else {
                    PushConstants.PUSH_SERVICE_CLASS_NAME
                }
            } catch (e: Exception) {
                PushConstants.PUSH_SERVICE_CLASS_NAME
            }
        }

    private val serviceBootMode: Int
        get() {
            return synchronized(this) {
                mContext.getSharedPreferences("mipush_extra", 0).getInt(Constants.EXTRA_KEY_BOOT_SERVICE_MODE, -1)
            }
        }

    private fun isAutoTry(): Boolean {
        val packageName = mContext.packageName
        if (packageName.contains("miui") || packageName.contains("xiaomi")) return true
        return (mContext.applicationInfo.flags and 1) != 0
    }

    private fun parseToMessage(intent: Intent): Message {
        return Message.obtain().apply {
            what = 17
            obj = intent
        }
    }

    private fun retryPolicy(str: String?, retryType: RetryType, z: Boolean, map: HashMap<String, String>?) {
        if (AppInfoHolder.getInstance(mContext).checkAppInfo() && Network.hasNetwork(mContext)) {
            val xmPushActionNotification2 = XmPushActionNotification().apply {
                setRequireAck(true)
            }
            val intent = createServiceIntent()
            var id = str
            if (TextUtils.isEmpty(id)) {
                val strGeneratePacketID = PacketHelper.generatePacketID()
                xmPushActionNotification2.id = strGeneratePacketID
                val xmPushActionNotification3 = if (z) XmPushActionNotification(strGeneratePacketID, true) else null
                synchronized(OperatePushHelper::class.java) {
                    OperatePushHelper.getInstance(mContext).resetOperateMessage(strGeneratePacketID)
                }
                id = strGeneratePacketID
            } else {
                xmPushActionNotification2.id = id
            }
            when (retryType) {
                RetryType.DISABLE_PUSH -> {
                    xmPushActionNotification2.type = NotificationType.DisablePushMessage.value
                    map?.let { xmPushActionNotification2.setExtra(it) }
                    intent.action = PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE
                }
                RetryType.ENABLE_PUSH -> {
                    xmPushActionNotification2.type = NotificationType.EnablePushMessage.value
                    map?.let { xmPushActionNotification2.setExtra(it) }
                    intent.action = PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE
                }
                RetryType.UPLOAD_HUAWEI_TOKEN, RetryType.UPLOAD_FCM_TOKEN, RetryType.UPLOAD_COS_TOKEN, RetryType.UPLOAD_FTOS_TOKEN -> {
                    xmPushActionNotification2.type = NotificationType.ThirdPartyRegUpdate.value
                    map?.let { xmPushActionNotification2.setExtra(it) }
                }
                else -> {}
            }
            MyLog.persist("type:$retryType, $id")
            xmPushActionNotification2.setAppId(AppInfoHolder.getInstance(mContext).appID)
            xmPushActionNotification2.packageName = mContext.packageName
            sendMessage(xmPushActionNotification2, ActionType.Notification, false, null)
            if (z) {
                val xmPushActionNotification = XmPushActionNotification().apply {
                    setAppId(AppInfoHolder.getInstance(mContext).appID)
                    packageName = mContext.packageName
                }
                val bArr = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
                    PushContainerHelper.generateRequestContainer(mContext, xmPushActionNotification, ActionType.Notification, false, mContext.packageName, AppInfoHolder.getInstance(mContext).appID)
                )
                if (bArr != null) {
                    PerfMessageHelper.collectPerfData(mContext.packageName, mContext, xmPushActionNotification, ActionType.Notification, bArr.size)
                    intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr)
                    intent.putExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true)
                    intent.putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, AppInfoHolder.getInstance(mContext).appID)
                    intent.putExtra(PushConstants.MIPUSH_EXTRA_APP_TOKEN, AppInfoHolder.getInstance(mContext).appToken)
                    callService(intent)
                }
            }
            handler.sendMessageDelayed(Message.obtain().apply {
                what = 19
                obj = id
                arg1 = retryType.ordinal
            }, 5000L)
        }
    }

    private fun saveServiceBootMode(i: Int) {
        synchronized(this) {
            mContext.getSharedPreferences("mipush_extra", 0).edit().putInt(Constants.EXTRA_KEY_BOOT_SERVICE_MODE, i).commit()
        }
    }

    private fun serviceInstalled(): Boolean {
        return try {
            val packageInfo: PackageInfo? = mContext.packageManager.getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4)
            packageInfo != null && packageInfo.longVersionCode >= MIN_MIUI_PUSH_VERSION
        } catch (th: Throwable) {
            false
        }
    }

    private fun startServiceSafely(intent: Intent) {
        try {
            if (MIUIUtils.isMIUI() || Build.VERSION.SDK_INT < 26) {
                mContext.startService(intent)
            } else {
                bindServiceSafely(intent)
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    private fun useBind(): Boolean {
        if (!shouldUseMIUIPush()) return true
        return try {
            mContext.packageManager.getPackageInfo(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 4).longVersionCode >= MIN_MIUI_PUSH_BIND_SERVICE_VERSION
        } catch (e: Exception) {
            true
        }
    }

    fun <T : TBase<T, *>> addPendRequest(t: T, actionType: ActionType, z: Boolean) {
        val bufferedRequest = BufferedRequest()
        bufferedRequest.dispatcher = object : PendingRequestDispatcher {
            override fun dispatch(client: PushServiceClient) {
                client.sendMessage(t, actionType, z, false, null, true)
            }
        }
        synchronized(sPendingRequest) {
            sPendingRequest.add(bufferedRequest)
            if (sPendingRequest.size > REQUEST_CACHE_SIZE) {
                sPendingRequest.removeAt(0)
            }
        }
    }

    fun awakePushService() {
        startServiceSafely(createServiceIntent())
    }

    fun clearLocalNotificationType() {
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, mContext.packageName)
            putExtra(PushConstants.EXTRA_SIG, MD5.MD5_16(mContext.packageName))
        }
        callService(intent)
    }

    fun clearNotification(i: Int) {
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, mContext.packageName)
            putExtra(PushConstants.EXTRA_NOTIFY_ID, i)
        }
        callService(intent)
    }

    fun clearNotification(str: String, str2: String) {
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, mContext.packageName)
            putExtra(PushConstants.EXTRA_NOTIFY_TITLE, str)
            putExtra(PushConstants.EXTRA_NOTIFY_DESCRIPTION, str2)
        }
        callService(intent)
    }

    fun closePush() {
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_DISABLE_PUSH
        }
        callService(intent)
    }

    fun isProvisioned(): Boolean {
        if (!shouldUseMIUIPush() || !isAutoTry()) return true
        if (mDeviceProvisioned == null) {
            val pushProvision = PushProvision.getInstance(mContext)
            val numValueOf = pushProvision.getProvisioned()
            mDeviceProvisioned = numValueOf
            if (numValueOf == 0) {
                mContext.contentResolver.registerContentObserver(
                    pushProvision.getProvisionedUri(),
                    false,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(z2: Boolean) {
                            mDeviceProvisioned = PushProvision.getInstance(mContext).getProvisioned()
                            if (mDeviceProvisioned != 0) {
                                mContext.contentResolver.unregisterContentObserver(this)
                                if (Network.hasNetwork(mContext)) {
                                    processRegisterTask()
                                }
                            }
                        }
                    }
                )
            }
        }
        return mDeviceProvisioned != 0
    }

    fun processPendRequest() {
        synchronized(sPendingRequest) {
            val z = Thread.currentThread() == Looper.getMainLooper().thread
            for (bufferedRequest in sPendingRequest) {
                bufferedRequest.dispatcher?.dispatch(this)
                if (!z) {
                    try {
                        Thread.sleep(100L)
                    } catch (e: InterruptedException) {
                    }
                }
            }
            sPendingRequest.clear()
        }
    }

    fun processRegisterTask() {
        val intent = registerTask
        registerTask = null
        if (intent != null) {
            XMPushService.observer?.cacheRegistrationTask(
                mContext.packageName, intent, "PushServiceClient.processRegisterTask",
                "legacy_cached_task", System.currentTimeMillis()
            )
        }
        XMPushService.observer?.dispatchRegistrationTasks("PushServiceClient.processRegisterTask")
    }

    fun register(xmPushActionRegistration: XmPushActionRegistration, z: Boolean) {
        PushClientReportManager.getInstance(mContext.applicationContext).reportEvent(
            mContext.packageName, ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID,
            xmPushActionRegistration.id, ReportConstants.REGISTER_TYPE_CONSTRUCT_MSG, null
        )
        registerTask = null
        AppInfoHolder.getInstance(mContext).appRegRequestId = xmPushActionRegistration.id
        XMPushService.observer?.onAccountEvent(
            mContext.packageName,
            if (z) "env_changed" else "client_register"
        )
        val intent = createServiceIntent()
        val bArr = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            PushContainerHelper.generateRequestContainer(mContext, xmPushActionRegistration, ActionType.Registration)
        )
        if (bArr == null) {
            MyLog.w("register fail, because msgBytes is null.")
            return
        }
        intent.action = PushConstants.MIPUSH_ACTION_REGISTER_APP
        intent.putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, AppInfoHolder.getInstance(mContext).appID)
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr)
        intent.putExtra(PushConstants.MIPUSH_EXTRA_SESSION, mSession)
        intent.putExtra(PushConstants.MIPUSH_EXTRA_ENV_CHANAGE, z)
        intent.putExtra(PushConstants.MIPUSH_EXTRA_ENV_TYPE, AppInfoHolder.getInstance(mContext).envType)
        if (Network.hasNetwork(mContext) && isProvisioned()) {
            XMPushService.observer?.clearRegistrationTasks(mContext.packageName)
            XMPushService.observer?.onAccountEvent(mContext.packageName, "call_service")
            callService(intent)
        } else {
            XMPushService.observer?.cacheRegistrationTask(
                mContext.packageName, intent, "PushServiceClient.register",
                if (Network.hasNetwork(mContext)) "device_unprovisioned" else "network_unavailable",
                System.currentTimeMillis()
            )
        }
    }

    fun send3rdPushHint(i: Int, str: String) {
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_THIRDPARTY_HINT
            putExtra(PushConstants.EXTRA_THIRDPARTY_HINT_LEVEL, i)
            putExtra(PushConstants.EXTRA_THIRDPARTY_HINT_DESC, str)
        }
        startServiceSafely(intent)
    }

    private fun getAssemblePushExtraOrNull(assemblePush: AssemblePush): HashMap<String, String>? {
        return try {
            AssemblePushHelper.getAssemblePushExtra(mContext, assemblePush)
        } catch (e: PackageManager.NameNotFoundException) {
            MyLog.e(e)
            null
        }
    }

    fun sendAssemblePushTokenCommon(str: String?, retryType: RetryType, assemblePush: AssemblePush) {
        OperatePushHelper.getInstance(mContext).putSyncStatus(retryType, OperatePushHelper.SYNCING)
        retryPolicy(str, retryType, false, getAssemblePushExtraOrNull(assemblePush))
    }

    fun sendDataCommon(intent: Intent) {
        intent.fillIn(createServiceIntent(), 24)
        callService(intent)
    }

    fun <T : TBase<T, *>> sendMessage(t: T, actionType: ActionType, pushMetaInfo: PushMetaInfo?) {
        sendMessage(t, actionType, !actionType.equals(ActionType.Registration), pushMetaInfo)
    }

    fun <T : TBase<T, *>> sendMessage(t: T, actionType: ActionType, z: Boolean, pushMetaInfo: PushMetaInfo?) {
        sendMessage(t, actionType, z, true, pushMetaInfo, true)
    }

    fun <T : TBase<T, *>> sendMessage(t: T, actionType: ActionType, z: Boolean, pushMetaInfo: PushMetaInfo?, z2: Boolean) {
        sendMessage(t, actionType, z, true, pushMetaInfo, z2)
    }

    fun <T : TBase<T, *>> sendMessage(t: T, actionType: ActionType, z: Boolean, z2: Boolean, pushMetaInfo: PushMetaInfo?, z3: Boolean) {
        sendMessage(t, actionType, z, z2, pushMetaInfo, z3, mContext.packageName, AppInfoHolder.getInstance(mContext).appID)
    }

    fun <T : TBase<T, *>> sendMessage(t: T, actionType: ActionType, z: Boolean, z2: Boolean, pushMetaInfo: PushMetaInfo?, z3: Boolean, str: String, str2: String) {
        sendMessage(t, actionType, z, z2, pushMetaInfo, z3, str, str2, true)
    }

    fun <T : TBase<T, *>> sendMessage(t: T, actionType: ActionType, z: Boolean, z2: Boolean, pushMetaInfo: PushMetaInfo?, z3: Boolean, str: String, str2: String, z4: Boolean) {
        if (!AppInfoHolder.getInstance(mContext).appRegistered()) {
            if (z2) {
                addPendRequest(t, actionType, z)
            } else {
                MyLog.w("drop the message before initialization.")
            }
            return
        }
        val container = if (z4) {
            PushContainerHelper.generateRequestContainer(mContext, t, actionType, z, str, str2)
        } else {
            PushContainerHelper.constructResponseContainer(mContext, t, actionType, z, str, str2)
        }
        if (pushMetaInfo != null) {
            container?.setMetaInfo(pushMetaInfo)
        }
        val bArr = XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
        if (bArr == null) {
            MyLog.w("send message fail, because msgBytes is null.")
            return
        }
        PerfMessageHelper.collectPerfData(mContext.packageName, mContext, t, actionType, bArr.size)
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_SEND_MESSAGE
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr)
            putExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, z3)
        }
        callService(intent)
    }

    fun sendPushEnableDisableMessage(z: Boolean) {
        sendPushEnableDisableMessage(z, null)
    }

    fun sendPushEnableDisableMessage(z: Boolean, str: String?) {
        if (z) {
            OperatePushHelper.getInstance(mContext).putSyncStatus(RetryType.DISABLE_PUSH, OperatePushHelper.SYNCING)
            OperatePushHelper.getInstance(mContext).putSyncStatus(RetryType.ENABLE_PUSH, "")
            retryPolicy(str, RetryType.DISABLE_PUSH, true, null)
        } else {
            OperatePushHelper.getInstance(mContext).putSyncStatus(RetryType.ENABLE_PUSH, OperatePushHelper.SYNCING)
            OperatePushHelper.getInstance(mContext).putSyncStatus(RetryType.DISABLE_PUSH, "")
            retryPolicy(str, RetryType.ENABLE_PUSH, true, null)
        }
    }

    fun sendServiceBootMode(i: Int): Boolean {
        if (!AppInfoHolder.getInstance(mContext).checkAppInfo()) return false
        saveServiceBootMode(i)
        val xmPushActionNotification = XmPushActionNotification().apply {
            id = PacketHelper.generatePacketID()
            setAppId(AppInfoHolder.getInstance(mContext).appID)
            packageName = mContext.packageName
            type = NotificationType.ClientABTest.value
            extra = HashMap()
            extra!!["boot_mode"] = "$i"
        }
        getInstance(mContext).sendMessage(xmPushActionNotification, ActionType.Notification, false, null)
        return true
    }

    fun sendTinyData(clientUploadDataItem: ClientUploadDataItem) {
        val intent = createServiceIntent()
        val bArr = XmPushThriftSerializeUtils.convertThriftObjectToBytes(clientUploadDataItem)
        if (bArr == null) {
            MyLog.w("send TinyData failed, because tinyDataBytes is null.")
            return
        }
        intent.action = PushConstants.MIPUSH_ACTION_SEND_TINYDATA
        intent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr)
        startServiceSafely(intent)
    }

    fun setLocalNotificationType(i: Int) {
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE
            putExtra(PushConstants.EXTRA_PACKAGE_NAME, mContext.packageName)
            putExtra(PushConstants.EXTRA_NOTIFY_TYPE, i)
            putExtra(PushConstants.EXTRA_SIG, MD5.MD5_16(mContext.packageName + i))
        }
        callService(intent)
    }

    fun shouldUseMIUIPush(): Boolean {
        return mIsMiuiPushServiceEnabled && AppInfoHolder.getInstance(mContext).envType == 1
    }

    fun unregister(xmPushActionUnRegistration: XmPushActionUnRegistration) {
        val bArr = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            PushContainerHelper.generateRequestContainer(mContext, xmPushActionUnRegistration, ActionType.UnRegistration)
        )
        if (bArr == null) {
            MyLog.w("unregister fail, because msgBytes is null.")
            return
        }
        val intent = createServiceIntent().apply {
            action = PushConstants.MIPUSH_ACTION_UNREGISTER_APP
            putExtra(PushConstants.MIPUSH_EXTRA_APP_ID, AppInfoHolder.getInstance(mContext).appID)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, bArr)
        }
        callService(intent)
    }
}
