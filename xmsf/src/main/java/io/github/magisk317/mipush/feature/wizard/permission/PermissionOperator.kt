package io.github.magisk317.mipush.feature.wizard.permission

interface PermissionOperator {
    fun isPermissionGranted(): Boolean
    fun requestPermissionSilently(): Boolean
    fun requestPermission()
}
