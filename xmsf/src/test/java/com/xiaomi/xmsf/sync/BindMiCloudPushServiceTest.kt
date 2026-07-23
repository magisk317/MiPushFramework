package com.xiaomi.xmsf.sync

import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class BindMiCloudPushServiceTest {
    @Test
    fun `uses legacy cloud sync target when primary cloud service is unavailable`() {
        val context: Application = RuntimeEnvironment.getApplication()

        assertEquals(
            BindMiCloudPushService.FALLBACK_COMPONENT,
            BindMiCloudPushService.resolveTargetComponent(context.packageManager),
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `prefers current cloud service target when it resolves`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val primaryIntent = Intent().setComponent(BindMiCloudPushService.PRIMARY_COMPONENT)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            primaryIntent,
            ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    packageName = BindMiCloudPushService.PRIMARY_COMPONENT.packageName
                    name = BindMiCloudPushService.PRIMARY_COMPONENT.className
                }
            },
        )

        assertEquals(
            BindMiCloudPushService.PRIMARY_COMPONENT,
            BindMiCloudPushService.resolveTargetComponent(context.packageManager),
        )
    }
}
