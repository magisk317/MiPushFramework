package io.github.magisk317.mipush.freeze

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.platform.support.BoundedShellResult
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.delay
import io.github.magisk317.mipush.common.logging.StableLogTag

/**
 * Re-enables apps the user froze with `pm disable-user` when one of their notifications is
 * tapped. Frozen apps still receive notifications through the standard publish chain; the click
 * lands in the XMSF service click route, where [handleLaunchActivation] enables the package again.
 *
 * Re-freezing is deliberately not this module's decision: the package stays enabled afterwards,
 * and the platform -- or whichever keep-alive module the user pairs with this one -- takes it from
 * there. Only packages this coordinator unfrozen are ever touched at all.
 */
@StableLogTag
class FrozenAppCoordinator(
    private val appContext: Context,
    private val preferenceRepository: PreferenceRepository,
) {
    /**
     * Activation hook from AppPushMessageProcessor.activeApp. Suspending: the caller must block the
     * click route until this returns so the subsequent pull-up sees the package enabled and ready.
     */
    suspend fun handleLaunchActivation(targetPackage: String) {
        if (targetPackage.isBlank()) return
        val unfreezeOnLaunch = runCatching {
            preferenceRepository.freezeEnabledOnce()
        }.getOrNull()
        if (unfreezeOnLaunch == null) {
            logW { "freeze settings snapshot unavailable, unconditional enable pkg=$targetPackage" }
            enablePackage(targetPackage)
            return
        }
        if (!unfreezeOnLaunch) {
            enablePackage(targetPackage)
            return
        }
        when (componentEnabledState(targetPackage)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> {
                if (enablePackage(targetPackage).isSuccess) {
                    waitForPackageReady(targetPackage)
                    logI("freeze unfroze pkg=$targetPackage")
                    emitFreezeEvent("ok", "unfreeze", targetPackage)
                } else {
                    emitFreezeEvent("error", "unfreeze_failed", targetPackage)
                }
            }
            else -> {
                logD { "freeze skip enable, package already enabled pkg=$targetPackage" }
                emitFreezeEvent("skip", "already_enabled", targetPackage)
            }
        }
    }

    private fun enablePackage(targetPackage: String, userId: Int = currentUserId()): BoundedShellResult {
        val quoted = shellQuote(targetPackage)
        val result = runCatching {
            AppRootAccessFacade.runRootCommand("pm enable --user $userId $quoted")
        }.getOrElse {
            logW { "pm enable failed pkg=$targetPackage error=${it.localizedMessage}" }
            return BoundedShellResult.skipped(it.javaClass.simpleName)
        }
        if (result.isSuccess) {
            // Clear stopped state so the newly enabled package can receive broadcasts.
            runCatching {
                AppRootAccessFacade.runRootCommand(
                    "cmd package set-stopped-state --user $userId $quoted false",
                )
            }.onFailure { }
        }
        return result
    }

    private suspend fun waitForPackageReady(packageName: String, timeoutMs: Long = PACKAGE_READY_TIMEOUT_MS) {
        var waited = 0L
        while (waited < timeoutMs) {
            val state = componentEnabledState(packageName)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            ) {
                return
            }
            delay(PACKAGE_READY_STEP_MS)
            waited += PACKAGE_READY_STEP_MS
        }
        logW { "freeze waitPackagesReady timeout pkg=$packageName after ${waited}ms" }
    }

    private fun componentEnabledState(packageName: String): Int = runCatching {
        appContext.packageManager.getApplicationEnabledSetting(packageName)
    }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE

    private fun emitFreezeEvent(result: String, reason: String, targetPackage: String) {
        MagiskOtel.event(
            name = "push.freeze",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "freeze_flow",
                "reason" to reason,
                "target_package" to targetPackage,
            ),
            statusOk = result != "error",
        )
    }

    private companion object {
        const val PACKAGE_READY_TIMEOUT_MS = 2200L
        const val PACKAGE_READY_STEP_MS = 50L
        const val PER_USER_RANGE = 100_000
    }
}
