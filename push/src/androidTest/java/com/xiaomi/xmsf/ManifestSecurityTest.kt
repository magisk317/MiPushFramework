package com.xiaomi.xmsf

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaomi.xmsf.push.service.HttpService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityTest {

    @Test
    fun httpService_requiresSignaturePermission() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        val componentName = ComponentName(context, HttpService::class.java)

        val serviceInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getServiceInfo(componentName, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getServiceInfo(componentName, 0)
        }

        assertTrue(serviceInfo.exported)
        assertEquals("com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE", serviceInfo.permission)
    }

    @Test
    fun bindHttpPermission_isSignatureProtected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        val permissionInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPermissionInfo(
                "com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE",
                PackageManager.PermissionInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPermissionInfo("com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE", 0)
        }
        val baseProtection = permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, baseProtection)
    }
}
