package io.github.magisk317.mipush.app.billing

import android.app.Activity
import io.github.magisk317.mipush.manager.billing.BillingProvider
import io.github.magisk317.uikit.billing.BillingManager

class PlayBillingProvider(
    private val billingManager: BillingManager,
) : BillingProvider {
    override val supportsPlayDonations: Boolean = true

    override fun launchDonation(activity: Activity, productId: String) {
        val productDetails = billingManager.donationDetails.value.find { details ->
            details.productId == productId
        } ?: return
        billingManager.launchDonationFlow(activity, productDetails)
    }
}
