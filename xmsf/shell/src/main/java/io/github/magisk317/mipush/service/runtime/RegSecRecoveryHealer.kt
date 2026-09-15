package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.RegSecRecoveryListener
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import kotlinx.coroutines.runBlocking

/**
 * Reconciles the product registration record after [io.github.magisk317.mipush.common.utils.Utils.getRegSecs]
 * recovered a decryption secret from the target app's own store. That recovery proves the
 * cloud-side registration is still valid while the service-side record was lost (identity
 * migration, storage reset); without this healer the app keeps receiving pushes yet never
 * leaves the not-registered state, because re-registration responses only re-issue the
 * secret on a fresh registration.
 */
object RegSecRecoveryHealer : RegSecRecoveryListener {

    override fun onRegSecRecovered(packageName: String, userId: Int) {
        if (packageName.isBlank()) return
        val row = runCatching { RegisteredApplicationDb.registerApplication(packageName) }.getOrNull() ?: return
        if (!shouldMarkRegistered(row)) return
        val changed = runCatching {
            RegistrationStateStore.updateIfChanged(
                application = row,
                nextType = RegisteredAppRegisteredType.Registered,
                source = RegistrationStateStore.Source.LOCAL_PROBE,
            )
        }.getOrDefault(false)
        if (!changed) return
        runCatching {
            runBlocking {
                EventDb.insertEventAsync(
                    EventRowResultType.OK,
                    RegistrationType("regsec_recovery_heal", packageName, null),
                )
            }
        }
    }

    internal fun shouldMarkRegistered(row: RuntimeRegisteredApplicationRow): Boolean =
        !row.blocked && row.registeredType != RegisteredAppRegisteredType.Registered
}
