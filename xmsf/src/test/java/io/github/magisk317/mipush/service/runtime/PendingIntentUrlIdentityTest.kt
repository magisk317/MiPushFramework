package io.github.magisk317.mipush.service.runtime

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [29], application = Application::class)
class PendingIntentUrlIdentityTest {
    @Test
    fun `identifier prevents url pending intent reuse without replacing destination data`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun pendingIntent(messageId: String): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/same-target"))
            MyMIPushNotificationIntentSupport.applyPendingIntentIdentity(
                intent,
                "com.example.client",
                42,
                messageId,
            )
            assertEquals("https://example.com/same-target", intent.data.toString())
            return PendingIntent.getActivity(context, 7, intent, flags)
        }

        assertNotEquals(pendingIntent("message-one"), pendingIntent("message-two"))
    }
}
