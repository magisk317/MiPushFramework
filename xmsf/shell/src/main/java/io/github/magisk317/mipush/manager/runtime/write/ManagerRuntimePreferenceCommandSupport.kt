package io.github.magisk317.mipush.manager.runtime.write

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge
import io.github.magisk317.mipush.common.ACTION_PREF_CHANGED
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_ANIMATION_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_BLUR_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_DYNAMIC_COLOR
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_GLASS_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_OUTER_GLOW_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_RENDERER_MODE
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.ISLAND_PREF_VISUAL_ENABLED
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.service.ForegroundHelper

/** Shell-local runtime preference write handlers. Dependencies remain explicit to avoid executor coupling. */
internal object ManagerRuntimePreferenceCommandSupport {
    suspend fun setRuntimeBoolean(
        request: ManagerWriteRequestDto,
        context: Context,
        preferenceRepository: PreferenceRepository,
        runtimeActions: ManagerRuntimeActions,
        logInfo: (String) -> Unit,
    ): ManagerWriteResultDto {
        val key = request.argument.trim()
        if (key !in allowedRuntimeBooleanKeys) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_BOOLEAN_UNKNOWN_KEY)
        }
        val enabled = request.booleanArgument
        when (key) {
            COLOR_STATUS_BAR_ICON_KEY -> preferenceRepository.setColorStatusBarIcon(enabled)
            COLOR_STATUS_BAR_ICON_GLOBAL_KEY -> preferenceRepository.setColorStatusBarIconGlobal(enabled)
            "debug_mode" -> preferenceRepository.setDebugMode(enabled)
            LOG_SANITIZATION_ENABLED_KEY -> preferenceRepository.setLogSanitizationEnabled(enabled)
            ENABLE_ANALYTICS_KEY -> preferenceRepository.setAnalyticsEnabled(enabled)
            "show_all_events" -> preferenceRepository.setShowAllEvents(enabled)
            "start_foreground" -> {
                preferenceRepository.setIsStartForeground(enabled)
                applyForegroundServicePolicy(enabled, context, runtimeActions)
            }
            "start_push_as_foreground_service" -> preferenceRepository.setStartPushAsForegroundService(enabled)
            KEEPALIVE_PREF_OOM_ADJ -> preferenceRepository.setKeepAliveOomAdj(enabled)
            KEEPALIVE_PREF_ANTI_KILL -> preferenceRepository.setKeepAliveAntiKill(enabled)
            KEEPALIVE_PREF_STANDBY_BYPASS -> preferenceRepository.setKeepAliveStandbyBypass(enabled)
            KEEPALIVE_PREF_DOZE_BYPASS -> preferenceRepository.setKeepAliveDozeBypass(enabled)
            ISLAND_PREF_ENABLED -> preferenceRepository.setIslandEnabled(enabled)
            ISLAND_PREF_FIRST_FLOAT -> preferenceRepository.setIslandFirstFloat(enabled)
            ISLAND_PREF_ENABLE_FLOAT -> preferenceRepository.setIslandEnableFloat(enabled)
            ISLAND_PREF_SHOW_NOTIFICATION -> preferenceRepository.setIslandShowNotification(enabled)
            ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION -> preferenceRepository.setIslandShowOriginalNotification(enabled)
            ISLAND_PREF_FOCUS_NOTIF -> preferenceRepository.setIslandFocusNotification(enabled)
            ISLAND_PREF_VISUAL_ENABLED -> preferenceRepository.setIslandVisualEnabled(enabled)
            ISLAND_PREF_DYNAMIC_COLOR -> preferenceRepository.setIslandDynamicColor(enabled)
            ISLAND_PREF_BLUR_ENABLED -> preferenceRepository.setIslandBlurEnabled(enabled)
            ISLAND_PREF_GLASS_ENABLED -> preferenceRepository.setIslandGlassEnabled(enabled)
            ISLAND_PREF_OUTER_GLOW_ENABLED -> preferenceRepository.setIslandOuterGlowEnabled(enabled)
            ISLAND_PREF_ANIMATION_ENABLED -> preferenceRepository.setIslandAnimationEnabled(enabled)
            else -> error("unreachable runtime boolean key=$key")
        }
        runCatching { context.sendBroadcast(Intent(ACTION_PREF_CHANGED)) }
        logInfo("set_runtime_boolean key=$key value=$enabled")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_BOOLEAN_OK)
    }

    suspend fun setRuntimeInt(
        request: ManagerWriteRequestDto,
        context: Context,
        preferenceRepository: PreferenceRepository,
        logInfo: (String) -> Unit,
    ): ManagerWriteResultDto {
        val key = request.argument.trim()
        if (key !in allowedRuntimeIntKeys) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_INT_UNKNOWN_KEY)
        }
        val value = request.intArgument
        when (key) {
            ISLAND_PREF_TIMEOUT -> preferenceRepository.setIslandTimeout(value)
            else -> error("unreachable runtime int key=$key")
        }
        runCatching { context.sendBroadcast(Intent(ACTION_PREF_CHANGED)) }
        logInfo("set_runtime_int key=$key value=$value")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_INT_OK)
    }

    suspend fun setRuntimeString(
        request: ManagerWriteRequestDto,
        context: Context,
        preferenceRepository: PreferenceRepository,
        logInfo: (String) -> Unit,
    ): ManagerWriteResultDto {
        val encoded = request.argument.trim()
        val separator = encoded.indexOf('=')
        if (separator <= 0) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_STRING_UNKNOWN_KEY)
        }
        val key = encoded.substring(0, separator).trim()
        if (key !in allowedRuntimeStringKeys) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_STRING_UNKNOWN_KEY)
        }
        val value = encoded.substring(separator + 1).trim().lowercase()
        if (value !in setOf("auto", "mipush", "hyperisland")) {
            return failed(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_STRING_UNKNOWN_KEY)
        }
        preferenceRepository.setIslandRendererMode(value)
        runCatching { context.sendBroadcast(Intent(ACTION_PREF_CHANGED)) }
        logInfo("set_runtime_string key=$key value=$value")
        return success(request.requestId, ManagerProtocol.WRITE_DETAIL_SET_RUNTIME_STRING_OK)
    }

    private suspend fun applyForegroundServicePolicy(
        enabled: Boolean,
        context: Context,
        runtimeActions: ManagerRuntimeActions,
    ) {
        if (enabled) {
            runtimeActions.startMiPushServiceAsForegroundService(context)
        } else {
            MiPushRuntimeObserverBridge.currentService()?.let { service ->
                ForegroundHelper(service).stopForegroundNotification()
            }
        }
    }

    private fun success(requestId: String, details: String) = ManagerWriteResultDto(
        requestId = requestId,
        status = ManagerProtocol.WRITE_STATUS_SUCCESS,
        details = details,
    )

    private fun failed(requestId: String, details: String) = ManagerWriteResultDto(
        requestId = requestId,
        status = ManagerProtocol.WRITE_STATUS_FAILED,
        details = details,
    )

    private val allowedRuntimeBooleanKeys = setOf(
        COLOR_STATUS_BAR_ICON_KEY, COLOR_STATUS_BAR_ICON_GLOBAL_KEY, "debug_mode",
        LOG_SANITIZATION_ENABLED_KEY, ENABLE_ANALYTICS_KEY, "show_all_events", "start_foreground",
        "start_push_as_foreground_service", KEEPALIVE_PREF_OOM_ADJ, KEEPALIVE_PREF_ANTI_KILL,
        KEEPALIVE_PREF_STANDBY_BYPASS, KEEPALIVE_PREF_DOZE_BYPASS, ISLAND_PREF_ENABLED,
        ISLAND_PREF_FIRST_FLOAT, ISLAND_PREF_ENABLE_FLOAT, ISLAND_PREF_SHOW_NOTIFICATION,
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION, ISLAND_PREF_FOCUS_NOTIF, ISLAND_PREF_VISUAL_ENABLED,
        ISLAND_PREF_DYNAMIC_COLOR, ISLAND_PREF_BLUR_ENABLED, ISLAND_PREF_GLASS_ENABLED,
        ISLAND_PREF_OUTER_GLOW_ENABLED, ISLAND_PREF_ANIMATION_ENABLED,
    )
    private val allowedRuntimeIntKeys = setOf(ISLAND_PREF_TIMEOUT)
    private val allowedRuntimeStringKeys = setOf(ISLAND_PREF_RENDERER_MODE)
}
