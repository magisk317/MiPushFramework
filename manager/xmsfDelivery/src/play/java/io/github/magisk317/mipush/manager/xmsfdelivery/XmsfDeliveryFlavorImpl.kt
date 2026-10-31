package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context

/**
 * play flavor binding for [XmsfDeliveryProvider]. The play distribution only
 * guides the user to the release page and verifies afterwards; see
 * [PlayXmsfDelivery]. The channel gate is accepted for the shared reflective
 * contract and unused here — the play flavor never probes a registry.
 */
internal class XmsfDeliveryFlavorImpl(
    context: Context,
    trackCi: () -> Boolean,
) : XmsfDelivery by PlayXmsfDelivery(context)
