package io.github.magisk317.mipush.feature.wizard.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.magisk317.mipush.platform.activity.impl.ActivityAccessibilityImpl

class AccessibilityPermissionOperator(private val context: Context) : PermissionOperator {
    private val delegate = ActivityAccessibilityImpl()

    override fun isPermissionGranted(): Boolean = delegate.isEnabled(context)

    override fun requestPermissionSilently(): Boolean = false

    override fun requestPermission() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
