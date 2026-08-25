package io.github.magisk317.mipush.push.bridge

import android.content.Context
import android.content.Intent
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.service.RegisterRecorder
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.common.utils.Utils
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import org.apache.thrift.TBase

object DefaultPushShellBridge : PushShellBridge {
    override fun receiveFromApplication(intent: Intent) {
        Global.miPushEventListener().receiveFromApplication(intent)
    }

    override fun recordRegisterRequest(context: Context, intent: Intent) {
        RegisterRecorder(context).recordRegisterRequest(intent)
    }

    override fun recordEvent(context: Context, container: XmPushActionContainer) {
        val packageName = container.packageName
        if (packageName.isNullOrBlank()) return
        if (io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb.isBlocked(packageName)) return
        if (!io.github.magisk317.mipush.common.utils.Utils.isUserApplication(context.applicationContext, packageName)) return
        val eventType = io.github.magisk317.mipush.runtime.store.event.type.TypeFactory.createForStore(container)
        val application = io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb.registerApplication(packageName)
        io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge.applyRegistrationStateFromContainer(
            context,
            container,
            application,
        )
        kotlinx.coroutines.runBlocking {
            io.github.magisk317.mipush.runtime.store.db.EventDb.insertEventAsync(
                io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType.OK,
                eventType,
            )
        }
    }

    override fun transferToServer(intent: Intent) {
        Global.miPushEventListener().transferToServer(intent)
    }

    override fun receiveFromServer(container: XmPushActionContainer) {
        Global.miPushEventListener().receiveFromServer(container)
    }

    override fun transferToApplication(container: XmPushActionContainer) {
        Global.miPushEventListener().transferToApplication(container)
    }

    override fun resolveTargetPackage(container: XmPushActionContainer): String? =
        StalePackagePushGuard.resolveTargetPackage(container)

    override fun packToContainer(payload: ByteArray?): XmPushActionContainer? =
        XMPushUtils.packToContainer(payload)

    override fun packToBytes(container: XmPushActionContainer): ByteArray =
        XMPushUtils.packToBytes(container)

    override fun dispatchToApplication(context: Context, packageName: String, payload: ByteArray): Boolean =
        XMPushUtils.dispatchToApplication(context, packageName, payload)

    override fun isProfileAllowed(context: Context, container: XmPushActionContainer): Boolean =
        StockSurfaceSupport.isProfileAllowed(context, container)

    override fun decodeMessageBody(container: XmPushActionContainer, regSec: String?): TBase<*, *>? =
        ConvertUtils.getResponseMessageBodyFromContainer(container, regSec)

    override fun getRegSec(container: XmPushActionContainer): String? = RegSecUtils.getRegSec(container)

    override fun updateRegistrationState(application: RuntimeRegisteredApplicationRow, nextType: Int) {
        RegistrationStateStore.updateIfChanged(
            application = application,
            nextType = nextType,
            source = RegistrationStateStore.Source.SERVER_RESULT,
        )
    }

    override fun forgetPendingRegistration(context: Context, packageName: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.forgetPendingRegistration(context, packageName)
    }

    override fun queuePendingAppAbsent(context: Context, packageName: String, appId: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.queuePendingAppAbsent(context, packageName, appId)
    }

    override fun forgetRegisteredPackage(context: Context, packageName: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.forgetRegisteredPackage(context, packageName)
    }

    override fun rememberRegisteredPackage(context: Context, packageName: String, appId: String) {
        com.xiaomi.push.service.MIPushAppAbsentManager.rememberRegisteredPackage(context, packageName, appId)
    }

    override fun setRegSec(context: Context, packageName: String, regSecret: String) {
        Utils.setRegSec(context, packageName, regSecret)
    }

    override fun applyConfigurations(packageName: String, container: XmPushActionContainer) {
        Configurations.getInstance().handle(packageName, container)
    }

    override fun shouldDropInbound(context: Context, container: XmPushActionContainer, source: String): Boolean =
        StalePackagePushGuard.shouldDropInbound(context, container, source)

    override fun shouldDropNotification(context: Context, container: XmPushActionContainer, source: String): Boolean =
        StalePackagePushGuard.shouldDropNotification(context, container, source)
}
