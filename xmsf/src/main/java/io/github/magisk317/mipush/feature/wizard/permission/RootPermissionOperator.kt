package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.platform.support.PermissionUtils

class RootPermissionOperator : PermissionOperator {
    override fun isPermissionGranted(): Boolean = PermissionUtils.hasCachedRootAccess()

    override fun requestPermissionSilently(): Boolean = false

    override fun requestPermission() {
        PermissionUtils.requestRootAccess()
    }
}
