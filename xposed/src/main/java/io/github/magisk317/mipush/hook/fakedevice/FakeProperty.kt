package io.github.magisk317.mipush.hook.fakedevice

import android.os.Build
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.*
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "FakeProperties"

enum class Property(val entry: Pair<String, String>) {
    // 清空其他厂商特征，避免与小米身份冲突
    EMUI_API("ro.build.hw_emui_api_level" to ""),
    EMUI_VERSION("ro.build.version.emui" to ""),
    
    // 小米设备身份（仅非小米设备需要）
    BRAND("ro.product.brand" to "Xiaomi"),
    MANUFACTURER("ro.product.manufacturer" to "Xiaomi"),
    MODEL("ro.product.model" to "Redmi K30 5G"),
    DEVICE("ro.product.device" to "picasso"),
    NAME("ro.product.name" to "picasso"),
    MIUI_VERSION_NAME("ro.miui.ui.version.name" to "V130"),
    MIUI_VERSION_CODE("ro.miui.ui.version.code" to "13"),
    MIUI_CUST_DEVICE("ro.miui.cust_device" to "picasso"),
    MIUI_CUST_VARIANT("ro.miui.cust_variant" to "cn_chinatelecom"),
    MIUI_MOD_DEVICE("ro.product.mod_device" to "picasso"),
    MIUI_HAS_GMSCORE("ro.miui.has_gmscore" to "1"),
    MIUI_NOTCH("ro.miui.notch" to "1"),
    MIUI_HAS_SECURITY_KEYBOARD("ro.miui.has_security_keyboard" to "1"),
    BUILD_FINGERPRINT("ro.build.fingerprint" to "Redmi/picasso/picasso:12/SKQ1.211006.001/V13.0.5.0.SGICNXM:user/release-keys"),
    BUILD_DISPLAY_ID("ro.build.display.id" to "SKQ1.211006.001 test-keys"),

    // 清空其他厂商特征
    FLYME_VERSION_NAME("ro.build.flyme.version" to ""),
    FLYME_VERSION_CODE("ro.flyme.version.id" to ""),
    COLOROS_BUILD_VERSION_OLD("ro.build.version.opporom" to ""),
    COLOROS_BUILD_VERSION("ro.build.version.oplusrom" to ""),

    REGION_MIUI("ro.miui.region" to "CN"),
    REGION_PRODUCT_LOCALE("ro.product.locale.region" to "CN"),
    REGION_PRODUCT_COUNTRY("ro.product.country.region" to "CN"),
    REGION_PERSIST_COUNTRY("persist.sys.country" to "CN"),
    
    // 小米服务与框架
    PERSIST_MICONNECT_RUNNING("persist.sys.miconnect.running" to "1"),
    PERSIST_MILLET_HANDSHAKE("persist.sys.millet.handshake" to "true"),
    PERSIST_BRIGHTMILLET_ENABLE("persist.sys.brightmillet.enable" to "true"),
    ;

    val key: String
        get() = entry.first

    val value: String
        get() = entry.second
}

// 需要完整伪装的属性（仅非小米设备）
private val XIAOMI_IDENTITY_PROPS = setOf(
    Property.BRAND.key,
    Property.MANUFACTURER.key,
    Property.MODEL.key,
    Property.DEVICE.key,
    Property.NAME.key,
    Property.BUILD_FINGERPRINT.key,
    Property.BUILD_DISPLAY_ID.key
)

// 始终需要清空的其他厂商特征
private val VENDOR_CLEAR_PROPS = setOf(
    Property.EMUI_API.key,
    Property.EMUI_VERSION.key,
    Property.FLYME_VERSION_NAME.key,
    Property.FLYME_VERSION_CODE.key,
    Property.COLOROS_BUILD_VERSION_OLD.key,
    Property.COLOROS_BUILD_VERSION.key
)

fun fakeProperty(property: Property, overrideValue: String) = fakeProperty(Pair(property.key, overrideValue))

fun fakeAllBuildInProperties() {
    val isXiaomi = DeviceDetector.isXiaomiDevice()
    XLog.d(TAG, "Device detection: isXiaomi=$isXiaomi, brand=${Build.BRAND}, manufacturer=${Build.MANUFACTURER}")
    
    val propsToFake = Property.values()
        .filter { prop ->
            when {
                // 始终清空其他厂商特征
                VENDOR_CLEAR_PROPS.contains(prop.key) -> true
                // 小米设备：跳过身份伪装，保留原生特征
                isXiaomi && XIAOMI_IDENTITY_PROPS.contains(prop.key) -> {
                    XLog.d(TAG, "Skipping ${prop.key} on Xiaomi device (keep native)")
                    false
                }
                // 其他属性都需要应用
                else -> true
            }
        }
        .map { it.entry }
        .toTypedArray()
    
    XLog.d(TAG, "Applying ${propsToFake.size} properties (total ${Property.values().size})")
    fakeProperty(*propsToFake)
}

fun fakeProperty(vararg properties: Property) {
    fakeProperty(*properties.map { it.entry }.toTypedArray())
}

private val propertyMap: MutableMap<String, String> = HashMap()
private val hooked = AtomicBoolean(false)

fun fakeProperty(vararg properties: Pair<String, String>) {
    propertyMap.putAll(properties)

    if (propertyMap.containsKey(Property.BRAND.key)) {
        Build::class.java["BRAND"] = propertyMap[Property.BRAND.key]
    }

    if (propertyMap.containsKey(Property.MANUFACTURER.key)) {
        Build::class.java["MANUFACTURER"] = propertyMap[Property.MANUFACTURER.key]
    }

    if (propertyMap.containsKey("ro.product.model")) {
        Build::class.java["MODEL"] = propertyMap["ro.product.model"]
    }

    if (propertyMap.containsKey("ro.product.device")) {
        Build::class.java["DEVICE"] = propertyMap["ro.product.device"]
    }

    if (propertyMap.containsKey("ro.product.name")) {
        Build::class.java["PRODUCT"] = propertyMap["ro.product.name"]
    }

    if (propertyMap.containsKey("ro.build.display.id")) {
        Build::class.java["DISPLAY"] = propertyMap["ro.build.display.id"]
    }

    if (propertyMap.containsKey("ro.build.user")) {
        Build::class.java["USER"] = propertyMap["ro.build.user"]
    }

    if (hooked.getAndSet(true)) return

    val classSystemProperties = Build::class.java.classLoader!!.findClass("android.os.SystemProperties")

    val callback: HookContext.() -> Unit = {
        doBefore {
            val key = args[0] as String
            propertyMap[key]?.let {
                result = it
            }
        }
    }

    classSystemProperties.hookMethod("get", String::class.java, callback = callback)
    classSystemProperties.hookMethod("get", String::class.java, String::class.java, callback = callback)

    Runtime::class.java.hookMethod("exec", String::class.java) {
        doBefore {
            val cmd = args[0] as String
            if (cmd.startsWith("getprop")) {
                val key = cmd.removePrefix("getprop").trim()
                propertyMap[key]?.let {
                    XLog.d(TAG, "hook getprop $key")
                    args[0] = "echo $it"
                }
            }
        }
    }
}
