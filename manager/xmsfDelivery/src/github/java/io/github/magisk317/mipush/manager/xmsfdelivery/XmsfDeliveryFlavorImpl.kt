package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * github flavor binding for [XmsfDeliveryProvider]. The full chain
 * (download -> verify -> install -> post-install verify) lives in
 * [GithubXmsfDelivery]; this class only gives the provider a fixed FQCN to
 * instantiate.
 */
internal class XmsfDeliveryFlavorImpl(
    context: Context,
    trackCi: () -> Boolean,
) : XmsfDelivery by GithubXmsfDelivery(
    context = context,
    scope = CoroutineScope(Dispatchers.Main.immediate),
    trackCi = trackCi,
)
