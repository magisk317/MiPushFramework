package top.trumeet.mipushframework.wizard

import android.content.Context
import top.trumeet.mipushframework.wizard.permission.PermissionInfo
import top.trumeet.mipushframework.wizard.permission.PermissionOperator

abstract class DisplayOnlyPhonyPermissionInfo(
    protected val context: Context
) : PermissionInfo, PermissionOperator {
    override val permissionOperator: PermissionOperator
        get() = this

    override fun isPermissionGranted(): Boolean {
        return true
    }

    override fun requestPermissionSilently() {
    }

    override fun requestPermission() {
    }
}
