package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.compat.RegistrationStateCompat
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

data class AppRegistrationDiagnostics(
    val hasLocalRegistration: Boolean,
    val regSecCount: Int,
    val latestRegistrationEventResult: Int?,
    val registeredType: Int,
    val inferenceReason: String
)

object AppRegistrationDiagnosticsHelper {
    private val registrationEventTypes = setOf(
        Event.Type.Registration,
        Event.Type.RegistrationResult,
        Event.Type.UnRegistration
    )

    fun load(
        packageName: String,
        registeredType: Int
    ): AppRegistrationDiagnostics {
        val latestRegistrationEvent = runBlocking {
            EventDb.queryAsync(
                skip = 0,
                limit = 1,
                types = registrationEventTypes,
                pkg = packageName,
                text = null
            ).firstOrNull()
        }
        val hasLocalRegistration = RegistrationStateCompat.hasValidLocalRegistration(packageName)
        val regSecCount = Utils.getRegSecs(packageName).size
        return AppRegistrationDiagnostics(
            hasLocalRegistration = hasLocalRegistration,
            regSecCount = regSecCount,
            latestRegistrationEventResult = latestRegistrationEvent?.result,
            registeredType = registeredType,
            inferenceReason = inferReason(
                registeredType = registeredType,
                latestEvent = latestRegistrationEvent,
                hasLocalRegistration = hasLocalRegistration,
                hasRegSec = regSecCount > 0
            )
        )
    }

    private fun inferReason(
        registeredType: Int,
        latestEvent: Event?,
        hasLocalRegistration: Boolean,
        hasRegSec: Boolean
    ): String {
        if (registeredType == RegisteredApplication.RegisteredType.Registered) {
            return "registered"
        }
        if (latestEvent == null && !hasLocalRegistration && !hasRegSec) {
            return "never_attempted"
        }
        if (latestEvent?.type == Event.Type.UnRegistration) {
            return "unregistered_after_attempt"
        }
        if (latestEvent?.type == Event.Type.RegistrationResult && latestEvent.result != Event.ResultType.OK) {
            return "registration_result_failed"
        }
        if (latestEvent?.type == Event.Type.Registration) {
            return "registering_or_waiting_result"
        }
        if (hasLocalRegistration && registeredType != RegisteredApplication.RegisteredType.Registered) {
            return "local_state_stale"
        }
        if (hasRegSec && !hasLocalRegistration) {
            return "has_secret_but_no_local_reg"
        }
        return "unknown"
    }
}
