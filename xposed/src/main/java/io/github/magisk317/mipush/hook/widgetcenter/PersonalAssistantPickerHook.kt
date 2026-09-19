package io.github.magisk317.mipush.hook.widgetcenter

import android.content.Context
import android.graphics.drawable.Drawable
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.hookMethod

/**
 * 小部件中心（com.miui.personalassistant PickerHomeActivity）的白名单绕过。
 *
 * "应用"分区的条目来自云端 POST component/store/expand/app 的返回
 * （PickerAppListRepository.loadDataFromService -> List<PickerListAppData>），
 * 侧载应用不在云端白名单里，因此永远不显示。本 hook 在该协程状态机
 * （PickerAppListRepository$loadDataFromService$1）的 invokeSuspend 最终返回处
 * 追加本应用的 PickerListAppData 条目。条目点击后走经典 AppWidget 绑定，
 * 渲染与数据全部在主进程，无独立进程/内存/占位限制。
 */
class PersonalAssistantPickerHook : BaseHook() {

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != PA_PACKAGE_NAME) return
        val classLoader = param.classLoader
        runCatching {
            val stateMachine = findHookClass(STATE_MACHINE_CLASS, classLoader)
            stateMachine.hookMethod("invokeSuspend", Any::class.java) {
                doAfter {
                    val list = result as? List<*> ?: return@doAfter
                    if (list.isEmpty()) return@doAfter
                    val proto = list.first() ?: return@doAfter
                    if (proto.javaClass.name != DATA_CLASS) return@doAfter
                    val alreadyIn = list.any {
                        runCatching { it.fieldOf("appPackage") as? String }.getOrNull() == MIPUSH_PACKAGE
                    }
                    if (alreadyIn) return@doAfter
                    val icon = currentApplicationIcon()
                    runCatching {
                        val merged = ArrayList(list)
                        merged.add(buildEntry(proto, icon))
                        result = merged
                        XLog.i(TAG, "appended MiPush entry into picker app list (server size=${list.size})")
                    }.onFailure { XLog.e(TAG, "append entry failed", it) }
                }
            }
            XLog.i(TAG, "picker app-list hook installed")
        }.onFailure {
            XLog.e(TAG, "install picker hook failed", it)
        }
    }

    private fun buildEntry(proto: Any, icon: Drawable?): Any {
        val clz = proto.javaClass
        val ctor = clz.getDeclaredConstructor(
            String::class.java,             // expansionType
            String::class.java,             // appPackage
            String::class.java,             // appName
            Integer.TYPE,                   // appVersionCode
            String::class.java,             // appVersionName
            String::class.java,             // appIcon
            Integer.TYPE,                   // appWidgetAmount
            String::class.java,             // appNamePinyin
            Drawable::class.java,           // localAppIcon
            Integer.TYPE,                   // itemType
        ).apply { isAccessible = true }
        val expansionType = runCatching { proto.fieldOf("expansionType") as? String }.getOrNull()
        val itemType = proto.intOf("itemType")
        val entry = ctor.newInstance(
            expansionType,
            MIPUSH_PACKAGE,
            MIPUSH_APP_NAME,
            MIPUSH_VERSION_CODE,
            MIPUSH_VERSION_NAME,
            "",
            2,
            "mipush",
            icon,
            itemType,
        )
        // 双保险：以字段为准显式覆盖（构造参数顺序若与运行时类有出入，字段设置仍然生效）
        entry.setFieldOf("appPackage", MIPUSH_PACKAGE)
        entry.setFieldOf("appName", MIPUSH_APP_NAME)
        entry.setIntFieldOf("appVersionCode", MIPUSH_VERSION_CODE)
        entry.setFieldOf("appVersionName", MIPUSH_VERSION_NAME)
        entry.setIntFieldOf("appWidgetAmount", 2)
        entry.setFieldOf("appNamePinyin", "mipush")
        if (icon != null) entry.setFieldOf("localAppIcon", icon)
        if (expansionType != null) entry.setFieldOf("expansionType", expansionType)
        entry.setIntFieldOf("itemType", itemType)
        return entry
    }

    companion object {
        private const val TAG = "PersonalAssistantPickerHook"
        const val PA_PACKAGE_NAME = "com.miui.personalassistant"
        const val MIPUSH_PACKAGE = "io.github.magisk317.mipush"
        private const val DATA_CLASS = "com.miui.personalassistant.picker.business.list.bean.PickerListAppData"
        private const val STATE_MACHINE_CLASS =
            "com.miui.personalassistant.picker.business.list.viewmodel.PickerAppListRepository\$loadDataFromService\$1"
        private const val MIPUSH_APP_NAME = "MiPush"
        private const val MIPUSH_VERSION_CODE = 1818
        private const val MIPUSH_VERSION_NAME = "1.0.4"

        fun currentApplication(): Context? = runCatching {
            val at = Class.forName("android.app.ActivityThread")
            at.getDeclaredMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()

        fun currentApplicationIcon(): Drawable? = runCatching {
            currentApplication()?.packageManager?.getApplicationIcon(MIPUSH_PACKAGE)
        }.getOrNull()
    }
}

internal fun Any?.fieldOf(name: String): Any? {
    if (this == null) return null
    return runCatching {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
    }.getOrNull()
}

internal fun Any?.setFieldOf(name: String, value: Any?) {
    if (this == null) return
    runCatching {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }
}

internal fun Any?.intOf(name: String): Int {
    if (this == null) return 0
    return runCatching {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.getInt(this)
    }.getOrDefault(0)
}

internal fun Any?.setIntFieldOf(name: String, value: Int) {
    if (this == null) return
    runCatching {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.setInt(this, value)
    }
}
