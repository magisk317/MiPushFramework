package com.xiaomi.push.service

import android.content.ContextWrapper
import android.content.Intent
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.com.xiaomi.channel.commonutils.android.AppInfoUtilsAspect
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.nihility.Global
import com.nihility.XMPushUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.utils.ConvertUtils
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.EventType
import top.trumeet.mipush.provider.event.type.TypeFactory

class MIPushEventProcessorAspect {
    @Volatile
    var isPostProcessMIPushMessage = false

    @Volatile
    var hookedXMPushService: XMPushService? = null

    @Throws(Throwable::class)
    fun buildIntent(joinPoint: ProceedingJoinPoint): Intent {
        val intent = joinPoint.proceed() as Intent
        return ignoreMessageIdAndMessageTypeExtra(intent)
    }

    @NonNull
    private fun ignoreMessageIdAndMessageTypeExtra(intent: Intent): Intent {
        return object : Intent(intent) {
            @NonNull
            override fun putExtra(name: String, @Nullable value: String?): Intent {
                if ("messageId" == name) {
                    return this
                }
                return super.putExtra(name, value)
            }

            @NonNull
            override fun putExtra(name: String, value: Int): Intent {
                if (ReportConstants.EVENT_MESSAGE_TYPE == name) {
                    return this
                }
                return super.putExtra(name, value)
            }
        }
    }

    @Throws(Throwable::class)
    fun buildContainerHook(joinPoint: ProceedingJoinPoint): XmPushActionContainer? {
        val container = joinPoint.proceed() as XmPushActionContainer?
        recordContainer(container)
        return container
    }

    fun isIntentAvailable(joinPoint: ProceedingJoinPoint): Boolean {
        return true
    }

    /**
     * default behavior is
     * return true
     * unless
     * - metaInfo.EXTRA_PARAM_CHECK_ALIVE exists
     * - metaInfo.EXTRA_PARAM_AWAKE is false
     * - the target application is not running
     */
    @Throws(Throwable::class)
    fun shouldSendBroadcast(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean {
        joinPoint.proceed()
        if (container.action == ActionType.Registration) {
            return true
        }
        if (container.packageName.startsWith("com.mi.") ||
            container.packageName.startsWith("com.miui.") ||
            container.packageName.startsWith("com.xiaomi.")
        ) {
            return true
        }
        val decorated = decoratedContainer(container.packageName, container)
        return AppInfoUtilsAspect.shouldSendBroadcast(pushService, packageName, decorated.metaInfo)
    }

    fun processMIPushMessage(
        joinPoint: JoinPoint,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    ) {
        logger.d(joinPoint.signature)

        val buildContainer = XMPushUtils.packToContainer(decryptedContent) ?: return
        if (MiPushMessageDuplicateAspect.isMockMessage(buildContainer)) {
            return
        }
        logger.d("buildContainer ${ConvertUtils.toJson(buildContainer)}")
        Global.MiPushEventListener().receiveFromServer(buildContainer)
        recordEvent(TypeFactory.createForStore(buildContainer))
    }

    @Throws(Throwable::class)
    fun postProcessMIPushMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) {
        isPostProcessMIPushMessage = true

        hookXMPushService(pushService)

        try {
            joinPoint.proceed()
        } finally {
            isPostProcessMIPushMessage = false
        }
    }

    fun hookXMPushService(pushService: XMPushService?) {
        if (hookedXMPushService === pushService || pushService == null) {
            return
        }
        synchronized(this) {
            if (hookedXMPushService === pushService) {
                return
            }
            try {
                val wrapped = object : ContextWrapper(pushService.baseContext) {
                    override fun sendBroadcast(intent: Intent, @Nullable receiverPermission: String?) {
                        if (isPostProcessMIPushMessage) {
                            val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
                            val container = XMPushUtils.packToContainer(payload)
                            if (container != null) {
                                Global.MiPushEventListener().transferToApplication(container)
                            }
                        }
                        super.sendBroadcast(intent, receiverPermission)
                    }
                }
                JavaCalls.setField(pushService, "mBase", wrapped)
                hookedXMPushService = pushService
            } catch (e: Throwable) {
                logger.e("hook xmpushservice failed", e)
            }
        }
    }

    companion object {
        private val TAG: String = MIPushEventProcessorAspect::class.java.simpleName
        private val logger: Logger = XLog.tag(TAG).build()

        @JvmStatic
        fun recordEvent(type: EventType) {
            RegisteredApplicationDb.registerApplication(type.pkg ?: return)
            logger.d("insertEvent -> $type")
            EventDb.insertEvent(Event.ResultType.OK, type)
        }

        private fun recordContainer(container: XmPushActionContainer?) {
            if (container == null) {
                return
            }
            Global.RegistrationRecorder().recordRegSec(container)
            val decorated = decoratedContainer(container.packageName, container)
            AppInfoUtilsAspect.setLastMetaInfo(decorated.metaInfo)
        }

        @JvmStatic
        fun decoratedContainer(
            realTargetPackage: String,
            container: XmPushActionContainer
        ): XmPushActionContainer {
            val decorated = container.deepCopy()
            try {
                Configurations.getInstance().handle(realTargetPackage, decorated)
            } catch (_: Throwable) {
                // Ignore
            }
            return decorated
        }
    }
}
