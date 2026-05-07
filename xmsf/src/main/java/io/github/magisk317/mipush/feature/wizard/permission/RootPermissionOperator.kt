package io.github.magisk317.mipush.feature.wizard.permission

import io.github.magisk317.mipush.platform.support.PermissionUtils

class RootPermissionOperator : PermissionOperator {
    override fun isPermissionGranted(): Boolean = PermissionUtils.hasRootAccess()

    override fun requestPermissionSilently(): Boolean {
        return PermissionUtils.requestRootAccess()
    }

    override fun requestPermission() {
        PermissionUtils.requestRootAccess()
    }
}
