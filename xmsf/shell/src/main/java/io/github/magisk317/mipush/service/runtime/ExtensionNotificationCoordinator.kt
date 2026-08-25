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
import java.util.Base64
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
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.XMPushUtils
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal object ExtensionNotificationBase64 {
    private val encoder = Base64.getMimeEncoder(76, byteArrayOf('\n'.code.toByte()))
    private val decoder = Base64.getMimeDecoder()

    fun encode(value: ByteArray): String {
        if (value.isEmpty()) return ""
        val encoded = encoder.encodeToString(value)
        return if (encoded.endsWith('\n')) encoded else "$encoded\n"
    }

    fun decode(value: String): ByteArray = decoder.decode(value)
}

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
            val bytes = ExtensionNotificationBase64.decode(encoded)
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
                ExtensionNotificationBase64.encode(output.toByteArray())
                    .takeIf(String::isNotEmpty)
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
        val userId: Int,
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
        packageName: String,
        messageId: String,
        container: XmPushActionContainer,
        payload: ByteArray,
        userId: Int,
        publish: (XmPushActionContainer, ByteArray) -> Unit,
    ) {
        val normalizedUserId = userId.coerceAtLeast(0)
        entries[key(normalizedUserId, packageName, messageId)] =
            Entry(elapsedRealtime(), packageName, normalizedUserId, container, payload, publish)
    }

    fun get(packageName: String, messageId: String, userId: Int): Entry? =
        entries[key(userId, packageName, messageId)]

    fun complete(
        packageName: String,
        messageId: String,
        content: RemoteNotificationContent?,
        userId: Int,
    ): Completion? {
        if (content == null) return null
        val key = key(userId, packageName, messageId)
        val entry = entries[key] ?: return null
        if (abs(elapsedRealtime() - entry.startedAtMs) >= timeoutMs) return null
        if (!entries.remove(key, entry)) return null
        return Completion(entry, content)
    }

    fun fail(packageName: String, messageId: String, userId: Int): Completion? {
        val entry = entries.remove(key(userId, packageName, messageId)) ?: return null
        return Completion(entry, null)
    }

    fun timeout(packageName: String, messageId: String, userId: Int): Completion? =
        fail(packageName, messageId, userId)

    fun clearPackage(packageName: String, userId: Int): Set<String> {
        val normalizedUserId = userId.coerceAtLeast(0)
        val prefix = "$normalizedUserId|$packageName\u0000"
        return entries.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()
            .also { messageIds ->
                messageIds.forEach { messageId ->
                    entries.remove(key(normalizedUserId, packageName, messageId))
                }
            }
    }

    private fun key(userId: Int, packageName: String, messageId: String): String =
        "${userId.coerceAtLeast(0)}|$packageName\u0000$messageId"
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
        val userId = currentUserId()
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
        pending.register(
            packageName = packageName,
            messageId = messageId,
            container = container,
            payload = payload,
            userId = userId,
            publish = publish,
        )
        scheduleTimeouts(packageName, messageId, userId)
        handler.post {
            connections.connect(
                context = appContext,
                packageName = packageName,
                messageId = messageId,
                userId = userId,
                onConnected = { service -> sendInitial(messageId, packageName, userId, remoteInfo, service) },
                onFailure = { failAndFallback(packageName, messageId, userId, "bind_failed") },
            )
        }
        logD("extension notification intercepted user=$userId pkg=$packageName id=$messageId")
        return true
    }

    fun clearPackageState(packageName: String, userId: Int = currentUserId()) {
        val normalizedUserId = userId.coerceAtLeast(0)
        val messageIds = pending.clearPackage(packageName, normalizedUserId)
        messageIds.forEach { messageId ->
            handler.removeCallbacksAndMessages(token(packageName, messageId, normalizedUserId))
            connections.release(packageName, messageId, normalizedUserId)
        }
    }

    @Suppress("DEPRECATION", "WrongConstant")
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

    private fun scheduleTimeouts(packageName: String, messageId: String, userId: Int) {
        val now = SystemClock.uptimeMillis()
        handler.postAtTime(
            { sendNearTimeout(packageName, messageId, userId) },
            token(packageName, messageId, userId),
            now + ExtensionNotificationContract.NEAR_TIMEOUT_MS,
        )
        handler.postAtTime(
            { timeoutAndFallback(packageName, messageId, userId) },
            token(packageName, messageId, userId),
            now + ExtensionNotificationContract.INITIAL_TIMEOUT_MS,
        )
    }

    private fun sendInitial(
        messageId: String,
        packageName: String,
        userId: Int,
        info: RemoteNotificationInfo,
        service: IExtensionInterface,
    ) {
        if (pending.get(packageName, messageId, userId) == null) {
            connections.release(packageName, messageId, userId)
            return
        }
        runCatching {
            service.baseReceiveRemoteNotification(info, callback(packageName, messageId, userId))
        }.onFailure {
            logE("extension initial callback dispatch failed id=$messageId", it)
            failAndFallback(packageName, messageId, userId, "initial_dispatch_failed")
        }
    }

    private fun sendNearTimeout(packageName: String, messageId: String, userId: Int) {
        val entry = pending.get(packageName, messageId, userId) ?: return
        val service = connections.get(entry.packageName, entry.userId)
        if (service == null) {
            logD("extension near-timeout service unavailable pkg=${entry.packageName} id=$messageId")
            return
        }
        val info = runCatching { ExtensionNotificationContract.createRemoteInfo(entry.container) }
            .getOrNull() ?: return
        runCatching {
            service.baseExtensionTimeWillExpire(info, callback(packageName, messageId, userId))
        }.onFailure {
            // Stock waits for the 10-second original-notification fallback after this failure.
            logW("extension near-timeout callback failed id=$messageId reason=${it.message}")
        }
    }

    private fun callback(packageName: String, messageId: String, userId: Int): IExtensionCallback = object : IExtensionCallback.Stub() {
        override fun onFinish(content: RemoteNotificationContent?) {
            finishFromCallback(packageName, messageId, userId, content)
        }
    }

    private fun finishFromCallback(
        packageName: String,
        messageId: String,
        userId: Int,
        content: RemoteNotificationContent?,
    ) {
        val completion = pending.complete(packageName, messageId, content, userId) ?: return
        finish(packageName, messageId, userId, completion, "callback")
    }

    private fun failAndFallback(packageName: String, messageId: String, userId: Int, reason: String) {
        val completion = pending.fail(packageName, messageId, userId) ?: return
        finish(packageName, messageId, userId, completion, reason)
    }

    private fun timeoutAndFallback(packageName: String, messageId: String, userId: Int) {
        val completion = pending.timeout(packageName, messageId, userId) ?: return
        finish(packageName, messageId, userId, completion, "timeout")
    }

    private fun finish(
        packageName: String,
        messageId: String,
        userId: Int,
        completion: ExtensionPendingRegistry.Completion,
        reason: String,
    ) {
        handler.removeCallbacksAndMessages(token(packageName, messageId, userId))
        val entry = completion.entry
        connections.release(entry.packageName, messageId, entry.userId)
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

    private fun token(packageName: String, messageId: String, userId: Int): String =
        "${userId.coerceAtLeast(0)}|$packageName\u0000$messageId"

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }.getOrDefault(0).coerceAtLeast(0)

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
        userId: Int,
        onConnected: (IExtensionInterface) -> Unit,
        onFailure: () -> Unit,
    ) {
        val recordKey = userKey(userId, packageName)
        val existing = synchronized(records) {
            records[recordKey]?.also { it.messageIds += messageId }
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
                    val connected = records[recordKey]
                    if (connected != null) {
                        connected.messageIds += messageId
                        connected
                    } else {
                        Record(context, service, this, mutableSetOf(messageId)).also {
                            records[recordKey] = it
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
                if (disconnect(context, packageName, userId, this) || !connected) onFailure()
            }

            override fun onBindingDied(name: ComponentName) {
                if (disconnect(context, packageName, userId, this) || !connected) onFailure()
            }

            override fun onNullBinding(name: ComponentName) {
                if (disconnect(context, packageName, userId, this) || !connected) onFailure()
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

    fun get(packageName: String, userId: Int): IExtensionInterface? = synchronized(records) {
        records[userKey(userId, packageName)]?.service
    }

    fun release(packageName: String, messageId: String, userId: Int) {
        if (packageName.isEmpty()) return
        val recordKey = userKey(userId, packageName)
        val recordToRelease = synchronized(records) {
            val record = records[recordKey] ?: return@synchronized null
            record.messageIds -= messageId
            if (record.messageIds.isNotEmpty()) return@synchronized null
            records.remove(recordKey)
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
        userId: Int,
        connection: ServiceConnection,
    ): Boolean {
        val recordKey = userKey(userId, packageName)
        val removed = synchronized(records) {
            val current = records[recordKey]
            if (current?.connection !== connection) false else {
                records.remove(recordKey)
                true
            }
        }
        runCatching { context.unbindService(connection) }
        return removed
    }

    private fun userKey(userId: Int, packageName: String): String =
        "${userId.coerceAtLeast(0)}|$packageName"
}
