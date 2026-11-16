package io.github.magisk317.mipush.compat

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.core.registration.RegistrationPolicyGate
import io.github.magisk317.mipush.runtime.core.registration.RegistrationPolicyDecision
import io.github.magisk317.mipush.runtime.android.PackageRegistrationGuard
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRegistrationState
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.logging.StableLogTag

@StableLogTag
object RegistrationStateStore {

    enum class Source {
        SERVER_RESULT,
        LOCAL_PROBE,
        MANUAL
    }

    @JvmStatic
    fun updateIfChanged(
        application: RuntimeRegisteredApplicationRow,
        nextType: Int,
        source: Source
    ): Boolean {
        return PackageRegistrationGuard.withPackage(application.packageName, application.userId) {
            val latest = RegisteredApplicationDb.getRegisteredApplication(application.packageName) ?: application
            if (source != Source.MANUAL && RegistrationPolicyGate.decide(latest.type, latest.blocked) != RegistrationPolicyDecision.Allow) return@withPackage false
            val oldType = latest.registeredType
            val changed = oldType != nextType
            if (changed) {
                val updated = latest.copy(registeredType = nextType)
                RegisteredApplicationDb.update(updated)
                logI(
                    "registration state changed pkg=${application.packageName}, ${labelOf(oldType)} -> ${labelOf(nextType)}, source=$source"
                )
                MagiskOtel.event(
                    name = "push.register",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to "0",
                        "process" to "xmsf",
                        "stage" to "state_store",
                        "reason" to "${labelOf(oldType)}_to_${labelOf(nextType)}",
                        "target_package" to application.packageName,
                        "source" to source.name.lowercase(),
                    ),
                    statusOk = true,
                )
            }
            PushRuntime.observeRegistrationState(
                packageName = application.packageName,
                state = runtimeStateOf(nextType),
                source = "RegistrationStateStore.${source.name.lowercase()}",
                reason = "persisted_state",
                androidUserId = application.userId,
            )
            changed
        }
    }

    private fun runtimeStateOf(type: Int): PushRegistrationState = when (type) {
        RegisteredAppRegisteredType.Registered -> PushRegistrationState.Registered
        RegisteredAppRegisteredType.Unregistered -> PushRegistrationState.Unregistered
        else -> PushRegistrationState.NotRegistered
    }

    private fun labelOf(type: Int): String {
        return when (type) {
            RegisteredAppRegisteredType.Registered -> "Registered"
            RegisteredAppRegisteredType.Unregistered -> "Unregistered"
            else -> "NotRegistered"
        }
    }
}
