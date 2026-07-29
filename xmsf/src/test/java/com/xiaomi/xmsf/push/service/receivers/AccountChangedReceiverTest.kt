package com.xiaomi.xmsf.push.service.receivers

import android.app.Application
import android.content.Intent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class AccountChangedReceiverTest {
    @Test
    fun `stock account change component has no runtime side effects`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val applicationShadow = shadowOf(context)
        applicationShadow.clearStartedServices()
        applicationShadow.clearBroadcastIntents()

        AccountChangedReceiver().onReceive(
            context,
            Intent("android.accounts.LOGIN_ACCOUNTS_CHANGED"),
        )

        assertTrue(applicationShadow.allStartedServices.isEmpty())
        assertTrue(applicationShadow.broadcastIntents.isEmpty())
    }
}
