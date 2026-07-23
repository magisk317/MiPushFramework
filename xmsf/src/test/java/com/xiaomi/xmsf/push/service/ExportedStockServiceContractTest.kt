package com.xiaomi.xmsf.push.service

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ExportedStockServiceContractTest {
    @Test
    fun `stat service exposes no binder or external state mutation`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("stock_surface", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val controller = Robolectric.buildService(StatService::class.java).create()
        val service = controller.get()

        assertNull(service.onBind(Intent("external.bind")))
        service.onStartCommand(Intent("external.start").putExtra("event", "forged"), 0, 1)
        assertFalse(preferences.contains("stat_events"))

        controller.destroy()
    }

    @Test
    fun `activate service ignores forged registration and external actions`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("pref_registered_pkg_names", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val controller = Robolectric.buildService(MiuiPushActivateService::class.java).create()
        val service = controller.get()

        service.onStartCommand(
            Intent("com.xiaomi.xmsf.push.APP_REGISTERED")
                .putExtra("source_package", "attacker.package")
                .putExtra("app_id", "forged"),
            0,
            1,
        )

        assertFalse(preferences.contains("attacker.package"))
        assertFalse(MiuiPushActivateService.isInternalActionSupported("com.xiaomi.xmsf.push.APP_REGISTERED"))
        assertTrue(MiuiPushActivateService.isInternalActionSupported("com.xiaomi.xmsf.push.SCAN"))
        assertTrue(MiuiPushActivateService.isInternalActionSupported("com.xiaomi.xmsf.push.ACCOUNT_CHANGE"))

        controller.destroy()
    }
}
