package com.xiaomi.xmsf.pushprocess

import android.app.Application
import android.content.Intent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class PushInnerReceiverTest {
    private lateinit var context: Application
    private val receiver = PushInnerReceiver()

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).clearBroadcastIntents()
        shadowOf(context).clearStartedServices()
    }

    @Test
    fun `stock kit delivery becomes a package scoped kit broadcast`() {
        receiver.onReceive(
            context,
            Intent("com.xiaomi.xmsf.inner.PUSH_MESSAGE")
                .putExtra("messageId", "message-1")
                .putExtra(
                    "content",
                    """{"type":"1","name":"samplekit","configMap":{"payload":"value"}}""",
                ),
        )

        val broadcast = shadowOf(context).broadcastIntents.single()
        assertEquals("${context.packageName}.samplekit.PUSH_MESSAGE_RECEIVED", broadcast.action)
        assertEquals(context.packageName, broadcast.`package`)
        assertEquals("message-1", broadcast.getStringExtra("message_Id"))
        assertNull(shadowOf(context).peekNextStartedService())
    }

    @Test
    fun `stock uninstall command is rejected instead of entering push runtime`() {
        receiver.onReceive(
            context,
            Intent("com.xiaomi.xmsf.inner.PUSH_MESSAGE")
                .putExtra("messageId", "message-2")
                .putExtra(
                    "content",
                    """{"type":"0","configMap":{"command":"uninstallXmsf","versionCode":"all"}}""",
                ),
        )

        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
        assertNull(shadowOf(context).peekNextStartedService())
    }

    @Test
    fun `parser uses stock messageId content and configMap shape`() {
        val parsed = PushInnerReceiver.parseControlMessage(
            "id",
            """{"type":"0","configMap":{"command":"uninstallKit","kitName":"kit"}}""",
        )

        requireNotNull(parsed)
        assertEquals("id", parsed.messageId)
        assertEquals("0", parsed.type)
        assertEquals("uninstallKit", parsed.command)
        assertEquals("kit", parsed.kitName)
        assertNull(PushInnerReceiver.parseControlMessage("id", "not-json"))
    }
}
