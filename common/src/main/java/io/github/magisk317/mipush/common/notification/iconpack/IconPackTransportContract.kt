package io.github.magisk317.mipush.common.notification.iconpack

/**
 * Identity of the icon-pack entry that produced a resolved small icon. The bitmap itself travels
 * on Notification.smallIcon; this string only records which pack entry it came from.
 */
fun thirdPartyPackSourceIdentity(targetPackage: String): String =
    "THIRD_PARTY_PACK($targetPackage)"
