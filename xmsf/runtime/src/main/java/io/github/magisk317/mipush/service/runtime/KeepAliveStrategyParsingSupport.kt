package io.github.magisk317.mipush.service.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ParsedKeepAliveStrategy(
    val targetPackage: String,
    val targetClass: String,
    val targetProcess: String,
    val targetAction: String,
    val triggerProcesses: Set<String>,
    val bindEvenAlive: Boolean,
    val memoryStandardMb: Int,
    val memoryUsageRate: Int,
    val batteryLowRate: Int,
    val maxTemperatureCelsius: Float,
    val deviceBlackList: Set<String>,
    val needStat: Boolean,
    val ignoreMiuiLite: Boolean,
    val calmDownPeriodMs: Int,
    val supportedOnDevice: Boolean,
)

internal object KeepAliveStrategyParsingSupport {
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun parse(configJson: String?, deviceName: String, onMalformed: () -> Unit): ParsedKeepAliveStrategy? {
        if (configJson.isNullOrBlank()) return null
        return runCatching {
            val root = Json.parseToJsonElement(configJson).jsonObject
            val targetPackage = root["package"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val targetClass = root["class"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val targetAction = root["action"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (!packagePattern.matches(targetPackage) || (targetClass.isBlank() && targetAction.isBlank())) {
                return null
            }
            val triggers = root["app_list"]?.jsonArray.orEmptyStrings(lowercase = false)
            val blockedDevices = root["dev_black_list"]?.jsonArray.orEmptyStrings(lowercase = true)
            ParsedKeepAliveStrategy(
                targetPackage = targetPackage,
                targetClass = targetClass,
                targetProcess = root["process"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                targetAction = targetAction,
                triggerProcesses = triggers,
                bindEvenAlive = root["bind_even_alive"]?.jsonPrimitive?.booleanOrNull ?: false,
                memoryStandardMb = root["men_std"]?.jsonPrimitive?.intOrNull ?: 0,
                memoryUsageRate = root["mem_usage_rate"]?.jsonPrimitive?.intOrNull ?: 0,
                batteryLowRate = root["battery_low_rate"]?.jsonPrimitive?.intOrNull ?: 0,
                maxTemperatureCelsius = (root["max_temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toFloat(),
                deviceBlackList = blockedDevices,
                needStat = root["need_stat"]?.jsonPrimitive?.booleanOrNull ?: true,
                ignoreMiuiLite = root["ignore_miui_lite"]?.jsonPrimitive?.booleanOrNull ?: false,
                calmDownPeriodMs = root["calm_down_period"]?.jsonPrimitive?.intOrNull ?: 0,
                supportedOnDevice = deviceName.lowercase() !in blockedDevices,
            )
        }.onFailure { onMalformed() }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonArray?.orEmptyStrings(lowercase: Boolean): Set<String> =
        buildSet {
            this@orEmptyStrings?.forEach { element ->
                element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank)?.let {
                    add(if (lowercase) it.lowercase() else it)
                }
            }
        }
}
