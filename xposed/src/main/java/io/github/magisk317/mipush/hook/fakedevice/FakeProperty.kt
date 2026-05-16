package io.github.magisk317.mipush.hook.fakedevice

import android.os.Build
import io.github.magisk317.mipush.common.fakedevice.MiPushResetpropTemplate
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

    val baseProps = LinkedHashMap<String, String>()
    Property.values()
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
        .forEach { prop ->
            baseProps[prop.key] = prop.value
        }

    val propsToFake = MiPushResetpropTemplate.mergedCustomProps(baseProps)
    XLog.i(
        TAG,
        "Applying ${propsToFake.size} fake properties " +
            "(base=${baseProps.size}, template=${MiPushResetpropTemplate.defaultCustomProps().size}, autoMiPush=${!isXiaomi})"
    )
    fakePropertiesSafely(propsToFake)
}

fun fakeProperty(vararg properties: Property) {
    fakeProperty(*properties.map { it.entry }.toTypedArray())
}

private val propertyMap: MutableMap<String, String> = HashMap()
private val hooked = AtomicBoolean(false)

private val buildStringFieldMappings = listOf(
    BuildStringFieldMapping("ro.product.board", Build::class.java, "BOARD"),
    BuildStringFieldMapping("ro.product.brand", Build::class.java, "BRAND"),
    BuildStringFieldMapping("ro.product.manufacturer", Build::class.java, "MANUFACTURER"),
    BuildStringFieldMapping("ro.product.model", Build::class.java, "MODEL"),
    BuildStringFieldMapping("ro.product.device", Build::class.java, "DEVICE"),
    BuildStringFieldMapping("ro.product.name", Build::class.java, "PRODUCT"),
    BuildStringFieldMapping("ro.product.cpu.abi", Build::class.java, "CPU_ABI"),
    BuildStringFieldMapping("ro.product.cpu.abi2", Build::class.java, "CPU_ABI2"),
    BuildStringFieldMapping("ro.build.display.id", Build::class.java, "DISPLAY"),
    BuildStringFieldMapping("ro.build.user", Build::class.java, "USER"),
    BuildStringFieldMapping("ro.build.host", Build::class.java, "HOST"),
    BuildStringFieldMapping("ro.build.id", Build::class.java, "ID"),
    BuildStringFieldMapping("ro.build.fingerprint", Build::class.java, "FINGERPRINT"),
    BuildStringFieldMapping("ro.build.tags", Build::class.java, "TAGS"),
    BuildStringFieldMapping("ro.build.type", Build::class.java, "TYPE"),
    BuildStringFieldMapping("ro.build.version.incremental", Build.VERSION::class.java, "INCREMENTAL"),
    BuildStringFieldMapping("ro.build.version.release", Build.VERSION::class.java, "RELEASE"),
)

fun fakeProperty(vararg properties: Pair<String, String>) {
    propertyMap.putAll(properties)

    applyBuildFieldOverrides()

    if (hooked.getAndSet(true)) return

    val classSystemProperties = runCatching {
        Build::class.java.classLoader!!.findClass("android.os.SystemProperties")
    }.onFailure {
        XLog.w(TAG, "skip SystemProperties hook: ${it.javaClass.simpleName}: ${it.message}")
    }.getOrNull() ?: return

    val getCallback: HookContext.() -> Unit = {
        doBefore {
            val key = args[0] as String
            propertyMap[key]?.let {
                result = it
            }
        }
    }

    runCatching {
        classSystemProperties.hookMethod("get", String::class.java, callback = getCallback)
        classSystemProperties.hookMethod("get", String::class.java, String::class.java, callback = getCallback)
        classSystemProperties.hookMethod("getInt", String::class.java, Int::class.javaPrimitiveType!!) {
            doBefore {
                val key = args[0] as String
                propertyMap[key]?.toIntOrNull()?.let { result = it }
            }
        }
        classSystemProperties.hookMethod("getLong", String::class.java, Long::class.javaPrimitiveType!!) {
            doBefore {
                val key = args[0] as String
                propertyMap[key]?.toLongOrNull()?.let { result = it }
            }
        }
        classSystemProperties.hookMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType!!) {
            doBefore {
                val key = args[0] as String
                propertyMap[key]?.let { value ->
                    when (value.lowercase()) {
                        "1", "true", "y", "yes", "on" -> result = true
                        "0", "false", "n", "no", "off" -> result = false
                    }
                }
            }
        }
    }.onFailure {
        XLog.w(TAG, "skip part of SystemProperties hooks: ${it.javaClass.simpleName}: ${it.message}")
    }

    runCatching {
        Runtime::class.java.hookAllMethods("exec") {
            doBefore {
                val key = extractGetpropKey(args.getOrNull(0)) ?: return@doBefore
                val value = propertyMap[key] ?: return@doBefore
                val replacement = replacementCommand(value)
                XLog.d(TAG, "hook exec getprop $key")
                when (args[0]) {
                    is String -> args[0] = replacement
                    is Array<*> -> args[0] = arrayOf("sh", "-c", replacement)
                }
            }
        }
    }.onFailure {
        XLog.w(TAG, "skip Runtime.exec getprop hook: ${it.javaClass.simpleName}: ${it.message}")
    }

    runCatching {
        ProcessBuilder::class.java.hookMethod("start") {
            doBefore {
                val processBuilder = thisObject as? ProcessBuilder ?: return@doBefore
                val command = runCatching { processBuilder.command() }.getOrNull() ?: return@doBefore
                val key = extractGetpropKey(command.toTypedArray()) ?: return@doBefore
                val value = propertyMap[key] ?: return@doBefore
                XLog.d(TAG, "hook ProcessBuilder getprop $key")
                processBuilder.command(listOf("sh", "-c", replacementCommand(value)))
            }
        }
    }.onFailure {
        XLog.w(TAG, "skip ProcessBuilder getprop hook: ${it.javaClass.simpleName}: ${it.message}")
    }
}

private fun fakePropertiesSafely(properties: Map<String, String>) {
    properties.forEach { (key, value) ->
        runCatching {
            fakeProperty(key to value)
        }.onFailure {
            XLog.w(TAG, "skip fake property $key: ${it.javaClass.simpleName}: ${it.message}")
        }
    }
}

private fun applyBuildFieldOverrides() {
    propertyMap.buildFieldOverrides().forEach { override ->
        setStaticFieldIfPresent(override.targetClass, override.fieldName, override.value)
    }
    propertyMap["ro.build.version.sdk"]?.toIntOrNull()?.let { sdkInt ->
        setStaticFieldIfPresent(Build.VERSION::class.java, "SDK_INT", sdkInt)
    }
}

internal data class BuildFieldOverride(
    val targetClass: Class<*>,
    val fieldName: String,
    val value: String,
)

private data class BuildStringFieldMapping(
    val propertyKey: String,
    val targetClass: Class<*>,
    val fieldName: String,
)

internal fun Map<String, String>.buildFieldOverrides(): List<BuildFieldOverride> {
    return buildStringFieldMappings.mapNotNull { mapping ->
        val value = this[mapping.propertyKey] ?: return@mapNotNull null
        BuildFieldOverride(
            targetClass = mapping.targetClass,
            fieldName = mapping.fieldName,
            value = value,
        )
    }
}

private fun setStaticFieldIfPresent(targetClass: Class<*>, fieldName: String, value: String?) {
    if (value == null) return
    runCatching { targetClass.setField(fieldName, value, String::class.java) }
        .onFailure {
            XLog.w(TAG, "skip Build field ${targetClass.name}#$fieldName: ${it.javaClass.simpleName}: ${it.message}")
        }
}

private fun setStaticFieldIfPresent(targetClass: Class<*>, fieldName: String, value: Int) {
    runCatching { targetClass.setField(fieldName, value, Int::class.javaPrimitiveType!!) }
        .onFailure {
            XLog.w(TAG, "skip Build field ${targetClass.name}#$fieldName: ${it.javaClass.simpleName}: ${it.message}")
        }
}

private fun extractGetpropKey(commandArg: Any?): String? {
    return when (commandArg) {
        is String -> parseGetpropFromCommand(commandArg)
        is Array<*> -> parseGetpropFromTokens(commandArg.filterIsInstance<String>())
        else -> null
    }
}

private fun parseGetpropFromCommand(command: String): String? {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("getprop ")) {
        return trimmed.removePrefix("getprop").trim().takeIf { it.isNotEmpty() }
    }
    if (trimmed.startsWith("/system/bin/getprop ")) {
        return trimmed.removePrefix("/system/bin/getprop").trim().takeIf { it.isNotEmpty() }
    }
    return null
}

private fun parseGetpropFromTokens(tokens: List<String>): String? {
    if (tokens.isEmpty()) return null
    val command = tokens.first()
    val isGetprop = command == "getprop" || command.endsWith("/getprop")
    if (!isGetprop) return null
    return tokens.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun replacementCommand(value: String): String {
    return "printf %s ${quoteForShell(value)}"
}

private fun quoteForShell(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}
