package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context

/**
 * play flavor binding for [XmsfDeliveryProvider]. The play distribution only
 * guides the user to the release page and verifies afterwards; see
 * [PlayXmsfDelivery].
 */
internal class XmsfDeliveryFlavorImpl(context: Context) : XmsfDelivery by PlayXmsfDelivery(context)
