package io.github.magisk317.mipush.feature.wizard.permission

interface PermissionInfo {
    val permissionOperator: PermissionOperator
    val permissionTitle: String
    val permissionDescription: String
    val isRequired: Boolean
        get() = true
}

internal const val FOREGROUND_DETECTION_GROUP = "foreground_detection"

fun PermissionInfo.requirementGroupKey(): String? {
    return when (this) {
        is UsageStatsPermissionInfo,
        is AccessibilityPermissionInfo -> FOREGROUND_DETECTION_GROUP
        else -> null
    }
}
