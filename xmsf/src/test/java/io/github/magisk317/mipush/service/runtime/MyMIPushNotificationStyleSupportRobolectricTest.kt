package io.github.magisk317.mipush.service.runtime

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MyMIPushNotificationStyleSupportRobolectricTest {

    @Test
    fun `personal messaging style uses sender as message person and falls back to app logo`() {
        val context = RuntimeEnvironment.getApplication()
        val container = messagingContainer(
            title = "Alice",
            description = "sent a message",
            extra = mutableMapOf(
                "__mi_push_conversation_sender" to "Alice",
                "__mi_push_conversation_sender_id" to "alice-id",
                "__mi_push_conversation_message" to "Hello",
                "__mi_push_use_messaging_style" to "",
                "__mi_push_text_icon" to "ShouldNotBecomeTextAvatar",
            ),
        )

        val message = MyMIPushNotificationStyleSupport.createMessage(context, container, context)
        assertNotNull(message)
        val notification = MyMIPushNotificationStyleSupport.messagingStyleNotificationBuilder(
            context = context,
            container = container,
            notificationId = 1,
            message = message!!,
            pkgCtx = context,
        )
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .build()

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        assertNotNull(style)
        assertNull(style!!.conversationTitle)
        assertFalse(style.isGroupConversation)
        assertEquals("Alice", style.messages.single().person?.name)
        assertNotNull(notification.getLargeIcon())
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
    }

    @Test
    fun `group messaging style keeps conversation title and falls back to app logo`() {
        val context = RuntimeEnvironment.getApplication()
        val container = messagingContainer(
            title = "Family",
            description = "Alice: Hello",
            extra = mutableMapOf(
                "__mi_push_conversation_title" to "Family",
                "__mi_push_conversation_id" to "family-id",
                "__mi_push_conversation_sender" to "Alice",
                "__mi_push_conversation_message" to "Hello",
                "__mi_push_use_messaging_style" to "",
            ),
        )

        val message = MyMIPushNotificationStyleSupport.createMessage(context, container, context)
        assertNotNull(message)
        val notification = MyMIPushNotificationStyleSupport.messagingStyleNotificationBuilder(
            context = context,
            container = container,
            notificationId = 2,
            message = message!!,
            pkgCtx = context,
        )
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .build()

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        assertNotNull(style)
        assertEquals("Family", style!!.conversationTitle)
        assertTrue(style.isGroupConversation)
        assertEquals("Alice", style.messages.single().person?.name)
        assertNotNull(notification.getLargeIcon())
    }

    @Test
    fun `normal builder renders sweet tags for collapsed text and big text`() {
        val context = RuntimeEnvironment.getApplication()
        val description = """This is a long notification body with <ft color="#ff0000">rich text</ft> inside."""
        val metaInfo = PushMetaInfo().apply {
            title = "Title"
            this.description = description
        }

        val builder = MyMIPushNotificationStyleSupport.normalStyleNotificationBuilder(
            context,
            metaInfo,
            context.packageName
        )
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp)
        val notification = builder.build()

        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("<ft"))
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("<ft"))
    }

    private fun messagingContainer(
        title: String,
        description: String,
        extra: MutableMap<String, String>
    ): XmPushActionContainer {
        return XmPushActionContainer().apply {
            action = ActionType.SendMessage
            packageName = RuntimeEnvironment.getApplication().packageName
            metaInfo = PushMetaInfo().apply {
                this.title = title
                this.description = description
                messageTs = 1234L
                this.extra = extra
            }
        }
    }
}
