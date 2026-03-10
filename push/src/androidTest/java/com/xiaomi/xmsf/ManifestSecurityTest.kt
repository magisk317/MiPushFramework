package com.xiaomi.xmsf

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magisk317.compat.PackageManagerCompatBridge
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

        val serviceInfo = PackageManagerCompatBridge.getServiceInfo(pm, componentName, 0)

        assertTrue(serviceInfo.exported)
        assertEquals("com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE", serviceInfo.permission)
    }

    @Test
    fun bindHttpPermission_isSignatureProtected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pm = context.packageManager
        val permissionInfo = pm.getPermissionInfo("com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE", 0)
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, permissionInfo.protection)
    }
}
