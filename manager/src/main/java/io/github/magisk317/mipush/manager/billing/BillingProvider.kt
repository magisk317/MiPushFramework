package io.github.magisk317.mipush.manager.billing

import android.app.Activity

interface BillingProvider {
    val supportsPlayDonations: Boolean

    fun launchDonation(activity: Activity, productId: String)
}

class NoOpBillingProvider : BillingProvider {
    override val supportsPlayDonations: Boolean = false

    override fun launchDonation(activity: Activity, productId: String) = Unit
}
