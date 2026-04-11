package io.github.magisk317.mipush.feature.wizard.support

import android.content.Context
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionOperator

abstract class DisplayOnlyPhonyPermissionInfo(
    protected val context: Context
) : PermissionInfo, PermissionOperator {
    override val permissionOperator: PermissionOperator
        get() = this

    override fun isPermissionGranted(): Boolean {
        return true
    }

    override fun requestPermissionSilently(): Boolean {
        return false
    }

    override fun requestPermission() {
    }
}
