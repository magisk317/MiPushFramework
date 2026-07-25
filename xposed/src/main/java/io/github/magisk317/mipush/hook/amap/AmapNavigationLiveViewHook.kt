package io.github.magisk317.mipush.hook.amap

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.magisk317.mipush.common.ISLAND_PREF_AUTHORITY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_COLUMN_VALUE
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_PATH_FLAGS
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.HookHandle
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.invokeOriginalMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Reuses AMap's drive live-view feed to update its existing driving foreground notification.
 *
 * AMap only enables this feed for OPPO-family live-view surfaces, although the feed already
 * contains the distance, next maneuver, and next road needed by Xiaomi focus notifications.
 * The bridge completes biz 111 locally, so no OPPO provider or device transport is touched.
 */
class AmapNavigationLiveViewHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != AMAP_PACKAGE) return
        if (param.processName.isNotBlank() && param.processName != AMAP_PACKAGE) return

        runCatching {
            AmapNavigationLiveViewBridge(param.classLoader).install()
            MagiskOtel.event(
                name = "push.island",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "amap_liveview",
                    "reason" to "installed",
                    "target_package" to AMAP_PACKAGE,
                ),
                statusOk = true,
            )
        }.onFailure {
            XLog.e(TAG, "install AMap drive live-view bridge failed: ${it.message}", it)
            MagiskOtel.event(
                name = "push.island",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "hook",
                    "stage" to "amap_liveview",
                    "reason" to "install_failed",
                    "target_package" to AMAP_PACKAGE,
                    "error_class" to it.javaClass.simpleName,
                ),
                statusOk = false,
            )
        }
    }

    private companion object {
        private const val TAG = "AmapNavigationLiveView"
        private const val AMAP_PACKAGE = "com.autonavi.minimap"
    }
}

private class AmapNavigationLiveViewBridge(
    private val classLoader: ClassLoader,
) {
    private val state = AmapDriveLiveViewState()
    private val lifecycleLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val protocolEnabledLog = AtomicBoolean(false)
    private val focusBypassGate = AmapFocusBypassGate(::readFocusBypassPreference)
    private var notificationTarget: NotificationTarget? = null
    private var localSessionActive = false
    private var publishGeneration = 0L

    fun install() {
        val targets = resolveTargets()
        val handles = mutableListOf<HookHandle>()
        try {
            handles += installNotificationTargetCapture(targets)
            handles += installNotificationLifecycleCleanup(targets)
            handles += installDriveLiveViewConsumer(targets)
            targets.bizBeginMethods.forEach { method ->
                handles += installBizBegin(method)
            }
            handles += installBizEnd(targets.bizEndMethod)
            // Install the producer gate last. Any earlier failure is rolled back before AMap can
            // observe drive live-view as enabled.
            handles += installDriveLiveViewGate(targets)
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            handles.asReversed().forEach { handle -> runCatching(handle::unhook) }
            throw throwable
        }
        XLog.i(TAG, "installed AMap drive live-view notification bridge")
    }

    private fun resolveTargets(): HookTargets {
        val channelClass = classLoader.findClass(NOTIFICATION_CHANNEL_CLASS)
        val carChannel = channelClass.getDeclaredField(CAR_CHANNEL_FIELD).apply {
            isAccessible = true
        }.get(null) ?: throw NoSuchFieldException("AMap car notification channel is null")

        val notifyServiceClass = classLoader.findClass(NOTIFY_SERVICE_CLASS)
        val notificationUpdateMethod = notifyServiceClass.declaredMethods
            .filter(::isNotificationUpdateMethod)
            .singleOrNull()
            ?.apply { isAccessible = true }
            ?: throw NoSuchMethodException("AMap notification update entrypoint not found")

        val notifyServiceImplClass = classLoader.findClass(NOTIFY_SERVICE_IMPL_CLASS)
        val notificationDestroyMethod = notifyServiceImplClass.declaredMethods
            .singleOrNull { it.name == SERVICE_DESTROY_METHOD && it.parameterCount == 0 }
            ?.apply { isAccessible = true }
            ?: throw NoSuchMethodException("AMap notification service lifecycle method not found")

        val wearableClass = classLoader.findClass(WEARABLE_MODULE_CLASS)
        val activitiesEnabledMethod = wearableClass.declaredMethods
            .singleOrNull {
                it.name == ACTIVITIES_ENABLED_METHOD &&
                    it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            ?.apply { isAccessible = true }
            ?: throw NoSuchMethodException("AMap drive live-view feature gate not found")
        val lockScreenMessageMethod = wearableClass.declaredMethods
            .singleOrNull {
                it.name == LOCK_SCREEN_MESSAGE_METHOD &&
                    it.parameterTypes.contentEquals(
                        arrayOf(
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            String::class.java,
                        ),
                    )
            }
            ?.apply { isAccessible = true }
            ?: throw NoSuchMethodException("AMap drive live-view message method not found")
        val bizBeginMethods = listOf(BIZ_BEGIN_METHOD, BIZ_BEGIN_WITH_DATA_METHOD).map { methodName ->
            wearableClass.declaredMethods
                .singleOrNull { it.name == methodName && it.firstCallbackArgumentIndex() != null }
                ?.apply { isAccessible = true }
                ?: throw NoSuchMethodException("AMap $methodName method not found")
        }
        val bizEndMethod = wearableClass.declaredMethods
            .singleOrNull {
                it.name == BIZ_END_METHOD &&
                    it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            }
            ?.apply { isAccessible = true }
            ?: throw NoSuchMethodException("AMap bizEnd method not found")

        return HookTargets(
            carChannel = carChannel,
            notificationUpdateMethod = notificationUpdateMethod,
            notificationDestroyMethod = notificationDestroyMethod,
            activitiesEnabledMethod = activitiesEnabledMethod,
            lockScreenMessageMethod = lockScreenMessageMethod,
            bizBeginMethods = bizBeginMethods,
            bizEndMethod = bizEndMethod,
        )
    }

    private fun installNotificationTargetCapture(targets: HookTargets): HookHandle {
        return targets.notificationUpdateMethod.hook {
            doAfter {
                if (args.firstOrNull() !== targets.carChannel) return@doAfter
                val owner = thisObject ?: return@doAfter
                synchronized(lifecycleLock) {
                    notificationTarget = NotificationTarget(
                        owner = owner,
                        arguments = args.copyOf(),
                        notificationUpdateMethod = targets.notificationUpdateMethod,
                    )
                }
                state.current()?.let(::schedulePublish)
            }
        }
    }

    private fun installNotificationLifecycleCleanup(targets: HookTargets): HookHandle {
        return targets.notificationDestroyMethod.hook {
            doBefore {
                clearNavigationState(clearTarget = true, endSession = false)
            }
        }
    }

    private fun installDriveLiveViewGate(targets: HookTargets): HookHandle {
        return targets.activitiesEnabledMethod.hook {
            doAfter {
                if (result == true || !isBridgeEnabled()) return@doAfter
                result = true
                if (protocolEnabledLog.compareAndSet(false, true)) {
                    XLog.i(TAG, "enabled AMap drive live-view producer for Xiaomi focus protocol")
                }
            }
        }
    }

    private fun installBizBegin(method: Method): HookHandle {
        val callbackIndex = requireNotNull(method.firstCallbackArgumentIndex())
        return method.hook {
            doBefore {
                if ((args.firstOrNull() as? Int) != DRIVE_LIVE_VIEW_BIZ_TYPE) return@doBefore
                if (!isBridgeEnabled()) return@doBefore
                val callback = args.getOrNull(callbackIndex) ?: return@doBefore
                beginLocalSession()
                if (dispatchBizBeginSuccess(callback)) {
                    result = null
                } else {
                    clearNavigationState(clearTarget = false, endSession = true)
                }
            }
        }
    }

    private fun installBizEnd(method: Method): HookHandle {
        return method.hook {
            doBefore {
                if ((args.firstOrNull() as? Int) != DRIVE_LIVE_VIEW_BIZ_TYPE) return@doBefore
                if (!isLocalSessionActive()) return@doBefore
                clearNavigationState(clearTarget = true, endSession = true)
                result = null
            }
        }
    }

    private fun installDriveLiveViewConsumer(targets: HookTargets): HookHandle {
        return targets.lockScreenMessageMethod.hook {
            doBefore {
                if ((args.getOrNull(0) as? Int) != DRIVE_LIVE_VIEW_BIZ_TYPE) return@doBefore
                if (!isLocalSessionActive()) return@doBefore

                // The local biz session never entered AMapDeviceManager. Always consume its
                // messages here, including after the preference is turned off mid-navigation.
                result = null
                if (!isBridgeEnabled()) {
                    clearNavigationState(clearTarget = false, endSession = false)
                    return@doBefore
                }
                val dataType = args.getOrNull(1) as? Int ?: return@doBefore
                if (dataType == CLEAR_DATA_TYPE) {
                    clearNavigationState(clearTarget = true, endSession = false)
                    XLog.i(TAG, "cleared AMap drive live-view navigation state")
                    return@doBefore
                }
                if (dataType != NAVIGATION_DATA_TYPE) return@doBefore

                val content = runCatching {
                    state.accept(dataType, args.getOrNull(2) as? String)
                }.onFailure {
                    XLog.w(TAG, "ignored malformed AMap live-view payload: ${it.message}")
                }.getOrNull()
                if (content != null) {
                    schedulePublish(content)
                }
            }
        }
    }

    private fun beginLocalSession() {
        state.clear()
        synchronized(lifecycleLock) {
            localSessionActive = true
            publishGeneration++
        }
    }

    private fun dispatchBizBeginSuccess(callback: Any): Boolean {
        val payload = JSONObject()
            .put("code", BIZ_CONNECT_SUCCESS)
            .put(
                "message",
                JSONObject()
                    .put("msg", "connect_success")
                    .put("manufacturer", "XIAOMI")
                    .put("deviceName", "XiaomiFocus")
                    .toString(),
            )
            .put("displayName", "")
            .put("deviceType", "")
        return runCatching {
            callback.callMethod(JS_CALLBACK_METHOD, arrayOf<Any?>(payload))
        }.onFailure {
            XLog.w(TAG, "AMap live-view begin callback failed: ${it.message}")
        }.isSuccess
    }

    private fun schedulePublish(content: AmapDriveNotificationContent) {
        if (!isBridgeEnabled()) return
        val generation = synchronized(lifecycleLock) {
            if (!localSessionActive || notificationTarget == null) return
            ++publishGeneration
        }
        mainHandler.post {
            publishOnMainThread(content, generation)
        }
    }

    private fun publishOnMainThread(content: AmapDriveNotificationContent, generation: Long) {
        if (!isBridgeEnabled()) return
        synchronized(lifecycleLock) {
            if (!localSessionActive || generation != publishGeneration) return
            val target = notificationTarget ?: return
            val invocationArgs = target.arguments.copyOf().apply {
                this[TITLE_ARGUMENT_INDEX] = content.title
                this[CONTENT_ARGUMENT_INDEX] = content.content
            }
            runCatching {
                invokeOriginalMethod(
                    target.notificationUpdateMethod,
                    target.owner,
                    invocationArgs,
                ) as? Boolean
            }.onSuccess { visible ->
                XLog.i(TAG, "updated AMap navigation notification state visible=${visible == true}")
            }.onFailure {
                XLog.e(TAG, "update AMap navigation notification failed: ${it.message}", it)
            }
        }
    }

    private fun clearNavigationState(clearTarget: Boolean, endSession: Boolean) {
        state.clear()
        synchronized(lifecycleLock) {
            publishGeneration++
            if (clearTarget) notificationTarget = null
            if (endSession) localSessionActive = false
        }
    }

    private fun isLocalSessionActive(): Boolean = synchronized(lifecycleLock) {
        localSessionActive
    }

    private fun isBridgeEnabled(): Boolean {
        val app = currentApplication() ?: return false
        val protocolAvailable = runCatching {
            Settings.System.getInt(app.contentResolver, FOCUS_PROTOCOL_SETTING, 0) > 0
        }.getOrDefault(false)
        return protocolAvailable && focusBypassGate.isEnabled()
    }

    private fun readFocusBypassPreference(): Boolean? {
        val app = currentApplication() ?: return null
        val uri = Uri.Builder()
            .scheme("content")
            .authority(ISLAND_PREF_AUTHORITY)
            .appendPath(ISLAND_PREF_PATH_FLAGS)
            .build()
        return runCatching {
            app.contentResolver.query(
                uri,
                null,
                null,
                arrayOf(ISLAND_PREF_FOCUS_NOTIF),
                null,
            )?.use { cursor ->
                val keyIndex = cursor.getColumnIndex(ISLAND_PREF_COLUMN_KEY)
                val valueIndex = cursor.getColumnIndex(ISLAND_PREF_COLUMN_VALUE)
                if (keyIndex < 0 || valueIndex < 0) return@use null
                while (cursor.moveToNext()) {
                    if (cursor.getString(keyIndex) == ISLAND_PREF_FOCUS_NOTIF) {
                        return@use cursor.getString(valueIndex).toBooleanFlag()
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun String?.toBooleanFlag(): Boolean? = when {
        this == "1" || equals("true", ignoreCase = true) -> true
        this == "0" || equals("false", ignoreCase = true) -> false
        else -> null
    }

    private fun Method.firstCallbackArgumentIndex(): Int? {
        val index = parameterTypes.indexOfFirst { it.name == JS_CALLBACK_CLASS }
        return index.takeIf { it > 0 }
    }

    private fun isNotificationUpdateMethod(method: Method): Boolean {
        val parameters = method.parameterTypes
        return method.name == NOTIFICATION_UPDATE_METHOD &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            parameters.size == NOTIFICATION_UPDATE_ARGUMENT_COUNT &&
            parameters[0].name == NOTIFICATION_CHANNEL_CLASS &&
            parameters[1] == Int::class.javaPrimitiveType &&
            parameters[2] == String::class.java &&
            parameters[3] == String::class.java &&
            parameters[4] == String::class.java &&
            List::class.java.isAssignableFrom(parameters[5])
    }

    private data class HookTargets(
        val carChannel: Any,
        val notificationUpdateMethod: Method,
        val notificationDestroyMethod: Method,
        val activitiesEnabledMethod: Method,
        val lockScreenMessageMethod: Method,
        val bizBeginMethods: List<Method>,
        val bizEndMethod: Method,
    )

    private data class NotificationTarget(
        val owner: Any,
        val arguments: Array<Any?>,
        val notificationUpdateMethod: Method,
    )

    private companion object {
        private const val TAG = "AmapNavigationLiveView"
        private const val NOTIFY_SERVICE_CLASS =
            "com.autonavi.bundle.amaphome.compat.service.NotifyService"
        private const val NOTIFY_SERVICE_IMPL_CLASS =
            "com.autonavi.bundle.amaphome.compat.service.NotifyServiceImpl"
        private const val NOTIFICATION_CHANNEL_CLASS =
            "com.amap.bundle.blutils.notification.NotificationChannelIds"
        private const val WEARABLE_MODULE_CLASS =
            "com.amap.bundle.wearable.ajx.NativesModuleWearable"
        private const val JS_CALLBACK_CLASS =
            "com.autonavi.minimap.ajx3.core.JsFunctionCallback"
        private const val CAR_CHANNEL_FIELD = "u"
        private const val NOTIFICATION_UPDATE_METHOD = "updateBackStageInfo"
        private const val SERVICE_DESTROY_METHOD = "onDestroy"
        private const val ACTIVITIES_ENABLED_METHOD = "areActivitiesEnabled"
        private const val LOCK_SCREEN_MESSAGE_METHOD = "sendLockScreenMessage"
        private const val BIZ_BEGIN_METHOD = "bizBegin"
        private const val BIZ_BEGIN_WITH_DATA_METHOD = "bizBeginWithData"
        private const val BIZ_END_METHOD = "bizEnd"
        private const val JS_CALLBACK_METHOD = "callback"
        private const val FOCUS_PROTOCOL_SETTING = "notification_focus_protocol"
        private const val DRIVE_LIVE_VIEW_BIZ_TYPE = 111
        private const val NAVIGATION_DATA_TYPE = 3
        private const val CLEAR_DATA_TYPE = 6
        private const val BIZ_CONNECT_SUCCESS = 1
        private const val NOTIFICATION_UPDATE_ARGUMENT_COUNT = 6
        private const val TITLE_ARGUMENT_INDEX = 2
        private const val CONTENT_ARGUMENT_INDEX = 3
    }
}
