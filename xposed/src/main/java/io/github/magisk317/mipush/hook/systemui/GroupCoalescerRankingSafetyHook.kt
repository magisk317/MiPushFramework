package io.github.magisk317.mipush.hook.systemui

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Soft-fail HyperOS/AOSP GroupCoalescer when RankingMap is missing the posted key.
 *
 * Stock code throws IllegalArgumentException and kills SystemUI:
 * "Ranking map does not contain key …|g:Aggregate_AlertingSection|…"
 *
 * GroupCoalescer uses an anonymous NotificationListener ($1). Anonymous classes are
 * NOT returned by Class.getDeclaredClasses(), so we resolve $1..$N and constructor fields.
 */
class GroupCoalescerRankingSafetyHook {
    fun hook(classLoader: ClassLoader) {
        var hooked = 0
        hooked += hookKnownAnonymousListeners(classLoader)
        hooked += hookListenerFieldsFromConstructors(classLoader)
        if (hooked == 0) {
            hooked += hookAnyOnNotificationPostedWithRanking(classLoader)
        }
        if (hooked > 0) {
            XLog.i(TAG, "GroupCoalescer ranking safety installed hooks=$hooked")
        } else {
            XLog.w(TAG, "GroupCoalescer ranking safety: no onNotificationPosted targets found")
        }
    }

    private fun hookKnownAnonymousListeners(classLoader: ClassLoader): Int {
        var count = 0
        for (index in 1..8) {
            val clazz = runCatching {
                classLoader.findClass("$GROUP_COALESCER\$$index")
            }.getOrNull() ?: continue
            count += hookOnNotificationPostedMethods(clazz.declaredMethods)
        }
        // Also try the outer class itself in case OEM inlined the callback.
        runCatching {
            val outer = classLoader.findClass(GROUP_COALESCER)
            count += hookOnNotificationPostedMethods(outer.declaredMethods)
            count += hookOnNotificationPostedMethods(outer.methods)
        }
        return count
    }

    /**
     * When $N names differ, capture the listener class from GroupCoalescer fields after <init>.
     */
    private fun hookListenerFieldsFromConstructors(classLoader: ClassLoader): Int {
        val coalescer = runCatching {
            classLoader.findClass(GROUP_COALESCER)
        }.getOrNull() ?: return 0
        var count = 0
        val hookedListenerClasses = mutableSetOf<String>()
        runCatching {
            coalescer.hookAllMethods("<init>") {
                doAfter {
                    val host = thisObject ?: return@doAfter
                    host.javaClass.declaredFields.forEach { field ->
                        runCatching {
                            field.isAccessible = true
                            val value = field.get(host) ?: return@forEach
                            val listenerClass = value.javaClass
                            if (!hookedListenerClasses.add(listenerClass.name)) return@forEach
                            val hookedHere = hookOnNotificationPostedMethods(listenerClass.declaredMethods)
                            if (hookedHere > 0) {
                                count += hookedHere
                                XLog.i(TAG, "hooked listener from field ${field.name} class=${listenerClass.name}")
                            }
                        }
                    }
                }
            }
            count += 1 // constructor hooks installed (may discover listeners later)
        }.onFailure {
            XLog.d(TAG, "GroupCoalescer <init> probe failed: ${it.message}")
        }
        return count
    }

    private fun hookAnyOnNotificationPostedWithRanking(classLoader: ClassLoader): Int {
        val candidates = listOf(
            "com.android.systemui.statusbar.NotificationListener",
            "com.android.systemui.statusbar.notification.MiuiNotificationListener",
        )
        var count = 0
        for (name in candidates) {
            val clazz = runCatching { classLoader.findClass(name) }.getOrNull() ?: continue
            count += hookOnNotificationPostedMethods(clazz.declaredMethods)
            count += hookOnNotificationPostedMethods(clazz.methods)
        }
        return count
    }

    private fun hookOnNotificationPostedMethods(methods: Array<Method>): Int {
        var count = 0
        methods.filter(::isOnNotificationPostedWithRanking).forEach { method ->
            runCatching {
                method.hook {
                    replace {
                        val sbn = args.getOrNull(0) as? StatusBarNotification
                        val rankingMap = args.getOrNull(1) as? NotificationListenerService.RankingMap
                        if (sbn != null && rankingMap != null && !rankingContains(rankingMap, sbn.key)) {
                            XLog.w(
                                TAG,
                                "skip GroupCoalescer post; ranking miss key=${sbn.key} pkg=${sbn.packageName}",
                            )
                            return@replace null
                        }
                        try {
                            invokeOriginal()
                        } catch (error: IllegalArgumentException) {
                            if (error.message?.contains("Ranking map") == true) {
                                XLog.w(TAG, "suppressed GroupCoalescer ranking crash: ${error.message}")
                                null
                            } else {
                                throw error
                            }
                        }
                    }
                }
                count++
            }.onFailure {
                XLog.d(TAG, "skip hook ${method.declaringClass.name}.${method.name}: ${it.message}")
            }
        }
        return count
    }

    private fun isOnNotificationPostedWithRanking(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers)) return false
        if (method.name != "onNotificationPosted") return false
        val params = method.parameterTypes
        if (params.size < 2) return false
        val firstOk = StatusBarNotification::class.java.isAssignableFrom(params[0]) ||
            params[0].name.contains("StatusBarNotification")
        val secondOk = NotificationListenerService.RankingMap::class.java.isAssignableFrom(params[1]) ||
            params[1].name.contains("RankingMap")
        return firstOk && secondOk
    }

    private fun rankingContains(
        rankingMap: NotificationListenerService.RankingMap,
        key: String?,
    ): Boolean {
        if (key.isNullOrBlank()) return true
        return runCatching {
            val rankingClass = NotificationListenerService.Ranking::class.java
            val ranking = rankingClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            val getRanking = NotificationListenerService.RankingMap::class.java.methods.firstOrNull {
                it.name == "getRanking" &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java
            } ?: return@runCatching true
            getRanking.invoke(rankingMap, key, ranking) as? Boolean ?: true
        }.getOrDefault(true)
    }

    private companion object {
        private const val TAG = "GroupCoalescerSafety"
        private const val GROUP_COALESCER =
            "com.android.systemui.statusbar.notification.collection.coalescer.GroupCoalescer"
    }
}
