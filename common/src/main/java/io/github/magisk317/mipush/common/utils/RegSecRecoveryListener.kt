package io.github.magisk317.mipush.common.utils

/**
 * Hook for the product layer to reconcile the service-side registration record when
 * [Utils.getRegSecs] had to recover a registration secret from the target application's
 * own MiPush store. Recovery implies the cloud still considers the package registered
 * while the service record was lost.
 */
fun interface RegSecRecoveryListener {
    fun onRegSecRecovered(packageName: String, userId: Int)
}
