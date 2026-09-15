package com.xiaomi.xms.auth

import android.os.Bundle
import co.touchlab.kermit.Logger

/**
 * Allow-all replacement for the stock XMSF focus-auth manager.
 *
 * The stock service consulted a cloud-synced commercial registration list (AuthApiFetcher +
 * AuthDB) that requires genuine device attestation; it cannot be reproduced on a replaced XMSF,
 * and any locally maintained per-package list inevitably lags behind (JD/外卖/出行 partners were
 * silently dropped). So this service authorizes every request, mirroring what the stock
 * international build does in FocusNotificationController.fetchAuthResult (unconditional success
 * at the same position in the chain).
 *
 * This removes only the commercial gate. The per-app decision to show an island still belongs to
 * the system-side gates (canShowFocus / canCustomFocus), and MiPush-managed notifications remain
 * gated by the module's own switches. The result contract mirrors stock
 * [com.xiaomi.xms.auth.AuthSession]: success -> result_code 0, with the original request echoed
 * back as result_auth_params.
 */
object AuthManager {
    private const val TAG = "FocusAuthAllowAll"

    val binder: android.os.IBinder = object : IAuthService.Stub() {
        override fun auth(bundle: Bundle?, callback: IAuthServiceCallback?) {
            val result = authorize(bundle)
            runCatching { callback?.onAuthResult(result) }
        }

        override fun syncAuth(bundle: Bundle?): Bundle = authorize(bundle)
    }

    private fun authorize(bundle: Bundle?): Bundle {
        val pkg = bundle?.getString("package_name")
        Logger.withTag(TAG).d { "authorized pkg=$pkg (allow-all)" }
        val result = Bundle()
        result.putBundle("result_auth_params", bundle)
        result.putInt("result_code", 0)
        result.putString("result_msg", "Auth is successful")
        return result
    }
}
