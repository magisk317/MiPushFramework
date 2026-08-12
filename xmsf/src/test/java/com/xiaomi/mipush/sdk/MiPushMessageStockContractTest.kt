package com.xiaomi.mipush.sdk

import android.os.Bundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.HashMap

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MiPushMessageStockContractTest {
    @Test
    fun `stock message bundle keeps 3_7_9 keys and serializable extra shape`() {
        val message = MiPushMessage().apply {
            messageId = "message-id"
            userAccount = "account-id"
            extra = mapOf("route_type" to "2")
        }

        val bundle = message.toBundle()

        assertEquals("account-id", bundle.getString("user_account"))
        assertFalse(bundle.containsKey("userAccount"))
        @Suppress("DEPRECATION")
        val extra = bundle.getSerializable("extra") as HashMap<*, *>
        assertEquals("2", extra["route_type"])
        assertNull(bundle.getBundle("extra"))
    }

    @Test
    fun `stock message parser accepts 7x serializable map and preserves arrived state`() {
        val bundle = Bundle().apply {
            putString("user_account", "account-id")
            putSerializable("extra", hashMapOf("miui_package_name" to "com.example.target"))
        }

        val message = MiPushMessage.fromBundle(bundle)
        assertEquals("account-id", message.userAccount)
        assertEquals("com.example.target", message.extra?.get("miui_package_name"))
        assertFalse(message.isArrivedMessage())

        message.setArrivedMessage(true)

        assertTrue(message.isArrivedMessage())
    }

    @Test
    fun `legacy project bundle remains readable during stock format migration`() {
        val bundle = Bundle().apply {
            putString("userAccount", "legacy-account")
            putBundle("extra", Bundle().apply { putString("route_type", "1") })
        }

        val message = MiPushMessage.fromBundle(bundle)

        assertEquals("legacy-account", message.userAccount)
        assertEquals("1", message.extra?.get("route_type"))
    }

    @Test
    fun `7x command bundle round trips auto mark packages`() {
        val command = MiPushCommandMessage().apply {
            command = "register"
            autoMarkPkgs = listOf("com.example.one", "com.example.two")
        }

        val restored = MiPushCommandMessage.fromBundle(command.toBundle())

        assertEquals(command.autoMarkPkgs, restored.autoMarkPkgs)
    }
}
