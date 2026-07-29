package io.github.magisk317.mipush.service.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Base64
import com.xiaomi.channel.commonutils.android.SystemProperties
import com.xiaomi.mipush.sdk.aidl.IExtensionCallback
import com.xiaomi.mipush.sdk.aidl.IExtensionInterface
import com.xiaomi.mipush.sdk.aidl.RemoteNotificationContent
import com.xiaomi.mipush.sdk.aidl.RemoteNotificationInfo
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.platform.support.XMPushUtils
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal object ExtensionNotificationContract {
    const val SERVICE_ACTION = "com.xiaomi.push.sdk.action.receive_extension_message"
    const val PROCESS_SUFFIX = ":pushExtensionService"
    const val HYPER_TYPE = "hyper_type"
    const val HYPER_TYPE_EXTENSION = "1"
    const val INITIAL_TIMEOUT_MS = 10_000L
    const val NEAR_TIMEOUT_MS = 8_000L
    const val TEMP_LARGE_ICON = "temp_large_icon"

    private const val MI_OS_VERSION_CODE = "ro.mi.os.version.code"
    private const val MI_OS_INCREMENTAL = "ro.mi.os.version.incremental"
    private const val MI_OS_DEBUG_VERSION = "ro.mi.os.debug.version.code"
    private const val TARGET_NAME = "_target_name"
    private const val LARGE_ICON_URI = "notification_large_icon_uri"
    private const val BIG_PICTURE_URI = "notification_bigPic_uri"
    private const val HYPER_CRYPT = "hyper_crypt"
    private const val HYPER_CLICK_TYPE = "hyper_click_type"
    private const val INTENT_URI = "intent_uri"
    private const val WEB_URI = "web_uri"
    private const val MESSAGE_COUNT = "message_count"
    private const val NOTIFY_EFFECT = "notify_effect"
    private const val MAX_EXTENSION_ICON_PIXELS = 49_152L
    data class AppliedContent(
        val container: XmPushActionContainer,
        val payload: ByteArray,
    )

    /**
     * Stock 7.4.67-C `extensionnotifaction.b.c()` admits HyperOS newer than 3, plus 3.1 stable or
     * debug builds. The old runtime had no version gate because it had no extension dispatch path.
     */
    fun isSupportedHyperOs(
        versionCode: String?,
        incrementalVersion: String?,
        debugVersion: String?,
    ): Boolean {
        val major = versionCode?.toIntOrNull() ?: 0
        if (major > 3) return true
        if (major < 3) return false

        val incrementalPatch = incrementalVersion
            ?.takeIf(String::isNotEmpty)
            ?.replace("OS", "")
            ?.split('.')
            ?.takeIf { it.size >= 3 }
            ?.get(2)
            ?.takeIf { it.length == 3 && it.isAsciiDigits() }
        if (incrementalPatch != null) {
            return incrementalPatch.toInt() >= 100
        }

        val debugMinor = debugVersion
            ?.takeIf(String::isNotEmpty)
            ?.split('.')
            ?.takeIf { it.size >= 2 }
            ?.get(1)
            ?.takeIf { it.isAsciiDigits() }
            ?.toIntOrNull()
        return debugMinor != null && debugMinor >= 1
    }

    fun isSupportedHyperOs(): Boolean = isSupportedHyperOs(
        versionCode = SystemProperties.get(MI_OS_VERSION_CODE, ""),
        incrementalVersion = SystemProperties.get(MI_OS_INCREMENTAL, ""),
        debugVersion = SystemProperties.get(MI_OS_DEBUG_VERSION, ""),
    )

    fun hasExpectedProcess(packageName: String, processName: String?): Boolean {
        return processName == packageName + PROCESS_SUFFIX
    }

    fun createRemoteInfo(container: XmPushActionContainer): RemoteNotificationInfo {
        val metaInfo = container.metaInfo ?: return RemoteNotificationInfo()
        val extra = metaInfo.extra ?: return RemoteNotificationInfo()
        val clickType = extra[HYPER_CLICK_TYPE]
            ?.takeIf { it.isAsciiDigits() }
            ?.toInt()
            ?: 0
        val clickUrl = when (clickType) {
            1 -> extra[INTENT_URI]
            2 -> extra[WEB_URI]
            else -> null
        }
        return RemoteNotificationInfo(
            token = extra[TARGET_NAME],
            title = metaInfo.title,
            body = metaInfo.description,
            image = extra[LARGE_ICON_URI].takeUnless { it.isNullOrEmpty() } ?: extra[BIG_PICTURE_URI],
            notifyId = metaInfo.notifyId.toLong(),
            clickType = clickType,
            clickUrl = clickUrl,
            extraData = extra[HYPER_CRYPT],
            msgId = metaInfo.id,
        )
    }

    /**
     * Stock 7.4.67-C mutates title/body/badge/click metadata, serializes that payload, and only then
     * adds `temp_large_icon` to the in-memory container. MiPushFramework previously skipped all of
     * those app-supplied changes, so keep the same ordering for click payload compatibility.
     */
    fun applyContent(
        container: XmPushActionContainer,
        originalPayload: ByteArray,
        content: RemoteNotificationContent,
    ): AppliedContent {
        val updated = container.deepCopy()
        val metaInfo = updated.metaInfo ?: return AppliedContent(updated, originalPayload)

        content.title?.takeIf(String::isNotEmpty)?.let { metaInfo.title = it }
        content.body?.takeIf(String::isNotEmpty)?.let { metaInfo.description = it }
        when (content.badgeOperateType) {
            1 -> metaInfo.putToExtra(MESSAGE_COUNT, "1")
            2 -> if (content.badgeNum > 0) {
                metaInfo.putToExtra(MESSAGE_COUNT, content.badgeNum.toString())
            }
        }
        when (content.clickType) {
            0 -> metaInfo.putToExtra(NOTIFY_EFFECT, "1")
            1 -> content.clickUrl?.takeIf(String::isNotEmpty)?.let {
                metaInfo.putToExtra(NOTIFY_EFFECT, "2")
                metaInfo.putToExtra(INTENT_URI, it)
            }
            2 -> content.clickUrl?.takeIf(String::isNotEmpty)?.let {
                metaInfo.putToExtra(NOTIFY_EFFECT, "3")
                metaInfo.putToExtra(WEB_URI, it)
            }
        }

        val serializedPayload = runCatching { XMPushUtils.packToBytes(updated) }
            .getOrNull()
            ?.takeIf(ByteArray::isNotEmpty)
        if (serializedPayload != null) {
            // Stock 7.4.67-C skips `temp_large_icon` when rebuilding the click payload fails. The
            // first implementation still attached it to the in-memory container on that path.
            encodeTemporaryLargeIcon(content.image)?.let {
                metaInfo.putToExtra(TEMP_LARGE_ICON, it)
            }
        }
        return AppliedContent(updated, serializedPayload ?: originalPayload)
    }

    fun decodeTemporaryLargeIcon(metaInfo: PushMetaInfo): Bitmap? {
        val encoded = metaInfo.extra?.get(TEMP_LARGE_ICON)?.takeIf(String::isNotEmpty) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun encodeTemporaryLargeIcon(bitmap: Bitmap?): String? {
        if (bitmap == null || bitmap.width.toLong() * bitmap.height.toLong() >= MAX_EXTENSION_ICON_PIXELS) {
            return null
        }
        return runCatching {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
                Base64.encodeToString(output.toByteArray(), Base64.DEFAULT).takeIf(String::isNotEmpty)
            }
        }.getOrNull()
    }

    private fun String.isAsciiDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }
}

internal class ExtensionPendingRegistry(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val timeoutMs: Long = ExtensionNotificationContract.INITIAL_TIMEOUT_MS,
) {
    data class Entry(
        val startedAtMs: Long,
        val packageName: String,
        val container: XmPushActionContainer,
        val payload: ByteArray,
        val publish: (XmPushActionContainer, ByteArray) -> Unit,
    )

    data class Completion(
        val entry: Entry,
        val content: RemoteNotificationContent?,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun register(
        messageId: String,
        packageName: String,
        container: XmPushActionContainer,
        payload: ByteArray,
        publish: (XmPushActionContainer, ByteArray) -> Unit,
    ) {
        entries[messageId] = Entry(elapsedRealtime(), packageName, container, payload, publish)
    }

    fun get(messageId: String): Entry? = entries[messageId]

    fun complete(messageId: String, content: RemoteNotificationContent?): Completion? {
        if (content == null) return null
        val entry = entries[messageId] ?: return null
        if (abs(elapsedRealtime() - entry.startedAtMs) >= timeoutMs) return null
        if (!entries.remove(messageId, entry)) return null
        return Completion(entry, content)
    }

    fun fail(messageId: String): Completion? {
        val entry = entries.remove(messageId) ?: return null
        return Completion(entry, null)
    }

    fun timeout(messageId: String): Completion? = fail(messageId)
}

/**
 * Product-owned implementation of the stock extension-notification client contract.
 *
 * Stock XMSF 7.4.67-C intercepted `hyper_type=1` before ordinary posting, called the target service
 * immediately and again at 8 seconds, then restored the original notification at 10 seconds. The
 * old MiPushFramework path posted immediately and never bound the target service.
 */
internal object ExtensionNotificationCoordinator {
    private const val TAG = "ExtensionNotification"
    // Stock 7.4.67-C `extensionnotifaction.a.e()` passes the legacy GET_SERVICES value (4).
    // The first implementation requested metadata even though eligibility only reads processName.
    private const val STOCK_SERVICE_RESOLVE_FLAGS = 4

    private val pending = ExtensionPendingRegistry()
    private val connections = ExtensionServiceConnectionPool()
    private val handler: Handler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val thread = HandlerThread("ExtensionMessageHandleCenter")
        thread.start()
        Handler(thread.looper)
    }

    fun handleIfEligible(
        context: Context,
        container: XmPushActionContainer,
        payload: ByteArray,
        publish: (XmPushActionContainer, ByteArray) -> Unit,
    ): Boolean {
        if (context.packageName != PushConstants.PUSH_SERVICE_PACKAGE_NAME) return false
        val metaInfo = container.metaInfo ?: return false
        if (metaInfo.extra?.get(ExtensionNotificationContract.HYPER_TYPE) !=
            ExtensionNotificationContract.HYPER_TYPE_EXTENSION
        ) {
            return false
        }
        val packageName = container.packageName?.takeIf(String::isNotEmpty) ?: return false
        if (!declaresCompatibleService(context, packageName)) return false
        if (!ExtensionNotificationContract.isSupportedHyperOs()) return false

        val messageId = metaInfo.id
        if (messageId.isNullOrEmpty()) {
            // Stock 7.4.67-C returns handled after all eligibility checks even when the ID is empty.
            // The old path posted such malformed extension payloads, so preserve the stock drop.
            logW("drop eligible extension notification with empty message id pkg=$packageName")
            return true
        }

        val remoteInfo = runCatching { ExtensionNotificationContract.createRemoteInfo(container) }
            .onFailure { logE("build extension notification info failed pkg=$packageName id=$messageId", it) }
            .getOrNull()
            ?: return false
        val appContext = context.applicationContext ?: context
        pending.register(messageId, packageName, container, payload, publish)
        scheduleTimeouts(messageId)
        handler.post {
            connections.connect(
                context = appContext,
                packageName = packageName,
                messageId = messageId,
                onConnected = { service -> sendInitial(messageId, packageName, remoteInfo, service) },
                onFailure = { failAndFallback(messageId, "bind_failed") },
            )
        }
        logD("extension notification intercepted pkg=$packageName id=$messageId")
        return true
    }

    @Suppress("DEPRECATION")
    private fun declaresCompatibleService(context: Context, packageName: String): Boolean {
        return runCatching {
            val intent = Intent(ExtensionNotificationContract.SERVICE_ACTION).setPackage(packageName)
            val resolveInfo = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.resolveService(
                    intent,
                    PackageManager.ResolveInfoFlags.of(STOCK_SERVICE_RESOLVE_FLAGS.toLong()),
                )
            } else {
                context.packageManager.resolveService(intent, STOCK_SERVICE_RESOLVE_FLAGS)
            }
            ExtensionNotificationContract.hasExpectedProcess(
                packageName,
                resolveInfo?.serviceInfo?.processName,
            )
        }.onFailure {
            logW("extension service lookup failed pkg=$packageName reason=${it.message}")
        }.getOrDefault(false)
    }

    private fun scheduleTimeouts(messageId: String) {
        val now = SystemClock.uptimeMillis()
        handler.postAtTime(
            { sendNearTimeout(messageId) },
            messageId,
            now + ExtensionNotificationContract.NEAR_TIMEOUT_MS,
        )
        handler.postAtTime(
            { timeoutAndFallback(messageId) },
            messageId,
            now + ExtensionNotificationContract.INITIAL_TIMEOUT_MS,
        )
    }

    private fun sendInitial(
        messageId: String,
        packageName: String,
        info: RemoteNotificationInfo,
        service: IExtensionInterface,
    ) {
        if (pending.get(messageId) == null) {
            connections.release(packageName, messageId)
            return
        }
        runCatching {
            service.baseReceiveRemoteNotification(info, callback(messageId))
        }.onFailure {
            logE("extension initial callback dispatch failed id=$messageId", it)
            failAndFallback(messageId, "initial_dispatch_failed")
        }
    }

    private fun sendNearTimeout(messageId: String) {
        val entry = pending.get(messageId) ?: return
        val service = connections.get(entry.packageName)
        if (service == null) {
            logD("extension near-timeout service unavailable pkg=${entry.packageName} id=$messageId")
            return
        }
        val info = runCatching { ExtensionNotificationContract.createRemoteInfo(entry.container) }
            .getOrNull() ?: return
        runCatching {
            service.baseExtensionTimeWillExpire(info, callback(messageId))
        }.onFailure {
            // Stock waits for the 10-second original-notification fallback after this failure.
            logW("extension near-timeout callback failed id=$messageId reason=${it.message}")
        }
    }

    private fun callback(messageId: String): IExtensionCallback = object : IExtensionCallback.Stub() {
        override fun onFinish(content: RemoteNotificationContent?) {
            finishFromCallback(messageId, content)
        }
    }

    private fun finishFromCallback(messageId: String, content: RemoteNotificationContent?) {
        val completion = pending.complete(messageId, content) ?: return
        finish(messageId, completion, "callback")
    }

    private fun failAndFallback(messageId: String, reason: String) {
        val completion = pending.fail(messageId) ?: return
        finish(messageId, completion, reason)
    }

    private fun timeoutAndFallback(messageId: String) {
        val completion = pending.timeout(messageId) ?: return
        finish(messageId, completion, "timeout")
    }

    private fun finish(
        messageId: String,
        completion: ExtensionPendingRegistry.Completion,
        reason: String,
    ) {
        handler.removeCallbacksAndMessages(messageId)
        val entry = completion.entry
        connections.release(entry.packageName, messageId)
        val content = completion.content
        if (content != null && !content.isShowNotification) {
            logD("extension notification suppressed pkg=${entry.packageName} id=$messageId reason=$reason")
            return
        }
        val applied = if (content == null) {
            ExtensionNotificationContract.AppliedContent(entry.container, entry.payload)
        } else {
            runCatching {
                ExtensionNotificationContract.applyContent(entry.container, entry.payload, content)
            }.onFailure {
                logE("apply extension notification failed pkg=${entry.packageName} id=$messageId", it)
            }.getOrElse {
                ExtensionNotificationContract.AppliedContent(entry.container, entry.payload)
            }
        }
        runCatching { entry.publish(applied.container, applied.payload) }
            .onFailure {
                logE("publish extension notification failed pkg=${entry.packageName} id=$messageId", it)
            }
        logD("extension notification completed pkg=${entry.packageName} id=$messageId reason=$reason")
    }

}

private class ExtensionServiceConnectionPool {
    private data class Record(
        val context: Context,
        val service: IExtensionInterface,
        val connection: ServiceConnection,
        val messageIds: MutableSet<String>,
    )

    private val records = HashMap<String, Record>()

    fun connect(
        context: Context,
        packageName: String,
        messageId: String,
        onConnected: (IExtensionInterface) -> Unit,
        onFailure: () -> Unit,
    ) {
        val existing = synchronized(records) {
            records[packageName]?.also { it.messageIds += messageId }
        }
        if (existing != null) {
            onConnected(existing.service)
            return
        }

        val connection = object : ServiceConnection {
            private var connected = false

            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = IExtensionInterface.Stub.asInterface(binder) ?: run {
                    runCatching { context.unbindService(this) }
                    onFailure()
                    return
                }
                val selected = synchronized(records) {
                    val connected = records[packageName]
                    if (connected != null) {
                        connected.messageIds += messageId
                        connected
                    } else {
                        Record(context, service, this, mutableSetOf(messageId)).also {
                            records[packageName] = it
                        }
                    }
                }
                connected = true
                if (selected.connection !== this) {
                    runCatching { context.unbindService(this) }
                }
                onConnected(selected.service)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (disconnect(context, packageName, this) || !connected) onFailure()
            }

            override fun onBindingDied(name: ComponentName) {
                if (disconnect(context, packageName, this) || !connected) onFailure()
            }

            override fun onNullBinding(name: ComponentName) {
                if (disconnect(context, packageName, this) || !connected) onFailure()
            }
        }
        val bound = runCatching {
            context.bindService(
                Intent(ExtensionNotificationContract.SERVICE_ACTION).setPackage(packageName),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!bound) onFailure()
    }

    fun get(packageName: String): IExtensionInterface? = synchronized(records) {
        records[packageName]?.service
    }

    fun release(packageName: String, messageId: String) {
        if (packageName.isEmpty()) return
        val recordToRelease = synchronized(records) {
            val record = records[packageName] ?: return@synchronized null
            record.messageIds -= messageId
            if (record.messageIds.isNotEmpty()) return@synchronized null
            records.remove(packageName)
            record
        }
        if (recordToRelease != null) runCatching {
            // Stock 7.4.67-C recycles the package connection after its last pending message.
            // Keeping it forever would retain target processes after the extension timeout.
            recordToRelease.context.unbindService(recordToRelease.connection)
        }
    }

    private fun disconnect(
        context: Context,
        packageName: String,
        connection: ServiceConnection,
    ): Boolean {
        val removed = synchronized(records) {
            val current = records[packageName]
            if (current?.connection !== connection) false else {
                records.remove(packageName)
                true
            }
        }
        runCatching { context.unbindService(connection) }
        return removed
    }
}
