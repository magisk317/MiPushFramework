package io.github.magisk317.mipush.feature.wizard.permission

interface PermissionInfo {
    val permissionOperator: PermissionOperator
    val permissionTitle: String
    val permissionDescription: String
    val isRequired: Boolean
        get() = true
}
