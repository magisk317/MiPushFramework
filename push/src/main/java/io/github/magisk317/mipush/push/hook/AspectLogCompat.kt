package io.github.magisk317.mipush.push.hook

import android.content.Intent
import android.os.SystemClock
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.XMPushService
import com.xiaomi.smack.packet.Packet
import com.xiaomi.slim.Blob
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.BuildConfig
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.magisk317.mipush.platform.support.Global
import kotlinx.coroutines.runBlocking

internal object AspectLogCompat {
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "AspectLogCompat")
    }
    private val indentLevel = ThreadLocal.withInitial { 0 }
    @Volatile
    private var cachedEnabled = BuildConfig.DEBUG
    @Volatile
    private var lastRefreshAt = 0L

    private fun enabled(): Boolean {
        if (BuildConfig.DEBUG) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastRefreshAt < 3000) return cachedEnabled
        cachedEnabled = runCatching { runBlocking { Global.configCenter().isDebugModeAsync() } }
            .getOrDefault(false)
        lastRefreshAt = now
        return cachedEnabled
    }

    private inline fun trace(signature: String, block: () -> Unit) {
        if (!enabled()) return
        val level = indentLevel.get() ?: 0
        val prefix = List(level) { "|\t" }.joinToString("")
        logger.d(prefix + signature)
        indentLevel.set(level + 1)
        try {
            block()
        } finally {
            val current = indentLevel.get() ?: 0
            indentLevel.set((current - 1).coerceAtLeast(0))
        }
    }

    fun logBuildContainer(payloadSize: Int, container: XmPushActionContainer?) {
        trace("MIPushEventProcessor.buildContainer(payloadSize=$payloadSize)") {
            logger.d("container=${ConvertUtils.toJson(container)}")
        }
    }

    fun logBuildIntent(intent: Intent?, source: String) {
        trace("MIPushEventProcessor.buildIntent(source=$source)") {
            logger.d("intent=${ConvertUtils.toJson(intent)}")
        }
    }

    fun logIntentAvailability(intent: Intent?, available: Boolean, source: String) {
        trace("MIPushEventProcessor.isIntentAvailable(source=$source, available=$available)") {
            logger.d("intent=${ConvertUtils.toJson(intent)}")
        }
    }

    fun logShouldSendBroadcast(
        pushService: XMPushService,
        packageName: String,
        metaInfo: PushMetaInfo?,
        decision: Boolean
    ) {
        trace("MIPushEventProcessor.shouldSendBroadcast(package=$packageName)") {
            logger.d(
                "decision=$decision, service=${pushService.javaClass.simpleName}, metaInfoHasExtra=${metaInfo?.extra?.isNotEmpty() == true}"
            )
        }
    }

    fun logPostProcessMIPushMessage(
        pkgName: String,
        payloadSize: Int,
        newMessageIntent: Intent
    ) {
        trace("MIPushEventProcessor.postProcessMIPushMessage(pkg=$pkgName, payloadSize=$payloadSize)") {
            logger.d("newMessageIntent=${ConvertUtils.toJson(newMessageIntent)}")
        }
    }

    fun logPacketArrival(chid: String, data: Any) {
        trace("ClientEventDispatcher.notifyPacketArrival(chid=$chid)") {
            when (data) {
                is Blob -> logger.d("blob arrival: $chid; $data")
                is Packet -> logger.d("packet arrival: $chid; ${data.toXML()}")
                else -> logger.d("arrival: $chid; type=${data.javaClass.name}")
            }
        }
    }

    fun logFallback(fallback: Fallback, usePort: Boolean) {
        trace("Fallback.getHosts(usePort=$usePort)") {
            val hosts = runCatching { fallback.getHosts(usePort) }.getOrElse { arrayListOf<String>() }
            val isp = runCatching { fallback.getISP() }.getOrNull()
            logger.d("fallback host=${fallback.host}, isp=$isp, hosts=$hosts")
        }
    }

    fun logProcessIntent(intent: Intent) {
        trace("PushMessageProcessor.processIntent") {
            logger.d("intent=${ConvertUtils.toJson(intent)}")
        }
    }

    fun logServiceMethod(signature: String, intent: Intent? = null, details: String? = null) {
        trace(signature) {
            if (!details.isNullOrEmpty()) {
                logger.d(details)
            }
            if (intent != null) {
                logger.d("intent=${ConvertUtils.toJson(intent)}")
            }
        }
    }

    fun logManifestCheck(packageName: String) {
        trace("ManifestChecker.checkServices(package=$packageName)") {
            logger.d("manifest check requested")
        }
    }

    fun logProcessMIPushMessage(packetBytesLen: Long, source: String) {
        trace("MIPushEventProcessor.processMIPushMessage(source=$source)") {
            logger.d("packetBytesLen=$packetBytesLen")
        }
    }

    fun logDuplicateCheck(packageName: String, messageId: String, duplicated: Boolean) {
        trace("MiPushMessageDuplicate.isDuplicateMessage(package=$packageName)") {
            logger.d("messageId=$messageId duplicated=$duplicated")
        }
    }

    fun logNotifyPushMessage(container: XmPushActionContainer, payloadSize: Int) {
        trace("MIPushNotificationHelper.notifyPushMessage(payloadSize=$payloadSize)") {
            logger.d("container=${ConvertUtils.toJson(container)}")
        }
    }
}
