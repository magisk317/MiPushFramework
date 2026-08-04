package io.github.magisk317.mipush.common.notification.iconpack

/**
 * Notification-only transport marker for a validated icon-pack smallIcon.
 *
 * The bitmap itself is carried by Notification.smallIcon. This marker carries no bitmap and is
 * accepted only when its value identifies the exact resolved target package.
 */
const val ICON_PACK_SOURCE_IDENTITY_EXTRA: String = "mipush_icon_pack_source_identity"

fun thirdPartyPackSourceIdentity(targetPackage: String): String =
    "THIRD_PARTY_PACK($targetPackage)"
