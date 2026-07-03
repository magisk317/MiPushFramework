package io.github.magisk317.mipush.hook.systemui

import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hookMethod
import io.github.magisk317.xposed.setHookObjectField

class MiPushFocusStatusBarIconHook {
    fun hook(classLoader: ClassLoader) {
        runCatching {
            val entryClass =
                classLoader.findClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
            classLoader.findClass(
                "com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsStoreBuilder"
            ).hookMethod("toModel", entryClass) {
                doAfter {
                    val model = result ?: return@doAfter
                    val entry = args.firstOrNull() ?: return@doAfter
                    val notification = statusBarNotificationFromEntry(entry)?.notification ?: return@doAfter
                    if (!MiPushFocusStatusBarPolicy.isMiPushIslandProxy(notification.extras)) return@doAfter
                    runCatching {
                        setHookObjectField(model, "isFocusNotification", false)
                    }.onFailure {
                        XLog.e(TAG, "failed to clear proxy focus status model: ${it.message}", it)
                    }
                }
            }
            XLog.i(TAG, "hooked ActiveNotificationsStoreBuilder.toModel for MiPush proxy focus icons")
        }.onFailure {
            XLog.d(TAG, "skip proxy focus status icon hook: ${it.message}")
        }
    }

    private fun statusBarNotificationFromEntry(entry: Any?): StatusBarNotification? {
        if (entry == null) return null
        return runCatching {
            getHookObjectField(entry, "mSbn") as? StatusBarNotification
        }.getOrNull() ?: runCatching {
            entry.callMethod("getSbn") as? StatusBarNotification
        }.getOrNull()
    }

    private companion object {
        private const val TAG = "MiPushFocusStatusBarIconHook"
    }
}

internal object MiPushFocusStatusBarPolicy {
    fun isMiPushIslandProxy(extras: Bundle?): Boolean {
        if (extras == null) return false
        return hasMiPushIslandOwner(extras::getString)
    }

    fun hasMiPushIslandOwner(getString: (String) -> String?): Boolean {
        return getString(IslandDispatchContract.OWNER) == IslandDispatchContract.OWNER_MARKER
    }
}
