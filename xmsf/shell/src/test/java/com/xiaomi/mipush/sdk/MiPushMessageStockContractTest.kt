package com.xiaomi.mipush.sdk

import android.os.Bundle
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

class MiPushMessageStockContractTest {

    private val bundleStores = ConcurrentHashMap<Int, MutableMap<String, Any?>>()

    @BeforeEach
    fun setupBundleMock() {
        mockkConstructor(Bundle::class)

        every { anyConstructed<Bundle>().putString(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<String?>()
        }
        every { anyConstructed<Bundle>().getString(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? String
        }
        every { anyConstructed<Bundle>().putBoolean(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<Boolean>()
        }
        every { anyConstructed<Bundle>().getBoolean(any()) } answers {
            (bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Boolean) ?: false
        }
        every { anyConstructed<Bundle>().getBoolean(any(), any()) } answers {
            (bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Boolean) ?: secondArg()
        }
        every { anyConstructed<Bundle>().putInt(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<Int>()
        }
        every { anyConstructed<Bundle>().getInt(any()) } answers {
            (bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Int) ?: 0
        }
        every { anyConstructed<Bundle>().getInt(any(), any()) } answers {
            (bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Int) ?: secondArg()
        }
        every { anyConstructed<Bundle>().putSerializable(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<java.io.Serializable?>()
        }
        every { anyConstructed<Bundle>().getSerializable(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? java.io.Serializable
        }
        every { anyConstructed<Bundle>().putStringArrayList(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<ArrayList<String>?>()
        }
        every { anyConstructed<Bundle>().getStringArrayList(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? ArrayList<String>
        }
        every { anyConstructed<Bundle>().putBundle(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<Bundle?>()
        }
        every { anyConstructed<Bundle>().getBundle(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Bundle
        }
        every { anyConstructed<Bundle>().putLong(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { HashMap() }[firstArg()] = secondArg<Long>()
        }
        every { anyConstructed<Bundle>().getLong(any()) } answers {
            (bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Long) ?: 0L
        }
        every { anyConstructed<Bundle>().containsKey(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.containsKey(firstArg()) ?: false
        }
        every { anyConstructed<Bundle>().keySet() } answers {
            bundleStores[System.identityHashCode(self)]?.keys ?: emptySet<String>()
        }
    }

    @AfterEach
    fun teardownBundleMock() {
        bundleStores.clear()
        unmockkConstructor(Bundle::class)
    }

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
