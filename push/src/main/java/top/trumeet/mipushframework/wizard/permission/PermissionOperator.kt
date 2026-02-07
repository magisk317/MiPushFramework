package top.trumeet.mipushframework.wizard.permission

interface PermissionOperator {
    fun isPermissionGranted(): Boolean
    fun requestPermissionSilently()
    fun requestPermission()
}
