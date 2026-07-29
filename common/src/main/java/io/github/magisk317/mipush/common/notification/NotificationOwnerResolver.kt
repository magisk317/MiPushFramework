package io.github.magisk317.mipush.common.notification

import android.os.Bundle

object NotificationOwnerResolver {
    const val EXTRA_TARGET_PACKAGE = "target_package"
    const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
    const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
    const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"

    @JvmStatic
    fun resolve(postingPackage: String, extras: Bundle?): String {
        if (postingPackage.isBlank()) return postingPackage
        return sequenceOf(
            extras?.getString(EXTRA_TARGET_PACKAGE),
            extras?.getString(EXTRA_MIUI_TARGET_PACKAGE),
            extras?.getString(EXTRA_XMSF_TARGET_PACKAGE),
            extras?.getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE),
        ).firstOrNull { !it.isNullOrBlank() } ?: postingPackage
    }
}
