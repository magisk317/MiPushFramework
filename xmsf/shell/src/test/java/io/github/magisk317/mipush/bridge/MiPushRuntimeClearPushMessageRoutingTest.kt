package io.github.magisk317.mipush.bridge

import android.content.Context
import com.xiaomi.push.service.MIPushClearPushMessageSupport
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import io.github.magisk317.mipush.runtime.core.PushRuntimeNotificationObservationSink
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Black-box tests for the product-layer routing of the stock wc.a matcher family:
 * the adapter is constructed with the vendor default clear hooks overridden so no
 * NotificationManager plumbing is required on the JVM.
 */
class MiPushRuntimeClearPushMessageRoutingTest {
    private val adapter = MiPushRuntimeMessageNotificationExecutionAdapter(
        mockk<Context>(relaxed = true),
        mockk<PushRuntimeNotificationObservationSink>(relaxed = true),
    )

    private fun clearControl(pkg: String?, extras: Map<String, String>?): XmPushActionNotification =
        XmPushActionNotification().apply {
            setType(NotificationType.CancelPushMessage.value)
            setId("clear-message-id")
            pkg?.let { setPackageName(it) }
            extras?.let { setExtra(it.toMutableMap()) }
        }

    @Test
    fun `notifyId route clears by id and reports handled when the cache reports a match`() {
        val idCalls = mutableListOf<Pair<String, Int>>()
        val handled = adapter.handleClearPushMessage(
            clearControl("com.example.app", mapOf("notifyId" to "7")),
            clearById = { pkg, id -> idCalls += pkg to id; 1 },
            clearByTitleDescription = { _, _, _ -> error("unexpected text clear") },
        )
        assertTrue(handled)
        assertEquals(listOf("com.example.app" to 7), idCalls)
    }

    @Test
    fun `notifyId plus msg id uses the id route (stock wc d approximation, documented gap)`() {
        val idCalls = mutableListOf<Pair<String, Int>>()
        val handled = adapter.handleClearPushMessage(
            clearControl(
                "com.example.app",
                mapOf("notifyId" to "3", MIPushClearPushMessageSupport.EXTRA_MSG_ID to "m1"),
            ),
            clearById = { pkg, id -> idCalls += pkg to id; 1 },
            clearByTitleDescription = { _, _, _ -> error("unexpected text clear") },
        )
        assertTrue(handled)
        assertEquals(listOf("com.example.app" to 3), idCalls)
    }

    @Test
    fun `title and description route clears via the text matcher`() {
        val textCalls = mutableListOf<Triple<String, String, String>>()
        val handled = adapter.handleClearPushMessage(
            clearControl(
                "com.example.app",
                mapOf("title" to "Hello", "description" to "World"),
            ),
            clearById = { _, _ -> error("unexpected id clear") },
            clearByTitleDescription = { pkg, title, description ->
                textCalls += Triple(pkg, title, description)
                2
            },
        )
        assertTrue(handled)
        assertEquals(listOf(Triple("com.example.app", "Hello", "World")), textCalls)
    }

    @Test
    fun `msg id only route is not handled because this tree has no msg-id registry`() {
        // Stock wc.b matches the message id embedded in the posted notification
        // (n1.h) and the u0.x/u0.g/u0.c registry; without that registry a miss is
        // reported, and the vendor acks like the stock wc.b miss path.
        var anyClear = false
        val handled = adapter.handleClearPushMessage(
            clearControl("com.example.app", mapOf(MIPushClearPushMessageSupport.EXTRA_MSG_ID to "m1")),
            clearById = { _, _ -> anyClear = true; 0 },
            clearByTitleDescription = { _, _, _ -> anyClear = true; 0 },
        )
        assertFalse(handled)
        assertFalse(anyClear)
    }

    @Test
    fun `a notifyId clear that matches nothing reports not handled`() {
        val handled = adapter.handleClearPushMessage(
            clearControl("com.example.app", mapOf("notifyId" to "7")),
            clearById = { _, _ -> 0 },
            clearByTitleDescription = { _, _, _ -> error("unexpected text clear") },
        )
        assertFalse(handled)
    }

    @Test
    fun `controls without extras or without a usable target package are not handled`() {
        assertFalse(adapter.handleClearPushMessage(clearControl("com.example.app", null)))
        assertFalse(
            adapter.handleClearPushMessage(
                clearControl("com.example.app", emptyMap()),
            ),
        )
        assertFalse(
            adapter.handleClearPushMessage(
                clearControl("", mapOf("notifyId" to "7")),
            ),
        )
        assertFalse(
            adapter.handleClearPushMessage(
                clearControl(null, mapOf("notifyId" to "7")),
            ),
        )
    }
}
