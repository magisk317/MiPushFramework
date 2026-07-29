package com.xiaomi.xmsf.push.service.receivers

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class XMSFUploadReceiverTest {
    @Test
    fun `valid stock upload payload remains stateless while telemetry is disabled`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("stock_surface", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        XMSFUploadReceiver().onReceive(
            context,
            Intent("com.xiaomi.xmsf.push.XMSF_UPLOAD_ACTIVE")
                .putExtra("pkgname", "com.android.systemui")
                .putExtra("category", "systemui_event")
                .putExtra("name", "expose")
                .putExtra("data", "{\"push_id\":\"id\"}"),
        )

        assertTrue(preferences.all.isEmpty())
    }
}
