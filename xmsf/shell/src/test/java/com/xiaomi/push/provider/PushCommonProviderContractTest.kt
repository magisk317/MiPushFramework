package com.xiaomi.push.provider

import android.os.Bundle
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PushCommonProviderContractTest {
    private val bundleStores = ConcurrentHashMap<Int, MutableMap<String, Any?>>()

    @BeforeEach
    fun setupBundleMock() {
        mockkConstructor(Bundle::class)

        every { anyConstructed<Bundle>().putBoolean(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { mutableMapOf() }[firstArg()] = secondArg<Boolean>()
        }
        every { anyConstructed<Bundle>().getBoolean(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Boolean ?: false
        }
        every { anyConstructed<Bundle>().putBundle(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { mutableMapOf() }[firstArg()] = secondArg<Bundle?>()
        }
        every { anyConstructed<Bundle>().getBundle(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Bundle
        }
        every { anyConstructed<Bundle>().putInt(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { mutableMapOf() }[firstArg()] = secondArg<Int>()
        }
        every { anyConstructed<Bundle>().getInt(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Int ?: 0
        }
        every { anyConstructed<Bundle>().getInt(any(), any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? Int ?: secondArg()
        }
        every { anyConstructed<Bundle>().putString(any(), any()) } answers {
            bundleStores.getOrPut(System.identityHashCode(self)) { mutableMapOf() }[firstArg()] = secondArg<String?>()
        }
        every { anyConstructed<Bundle>().getString(any()) } answers {
            bundleStores[System.identityHashCode(self)]?.get(firstArg()) as? String
        }
        every { anyConstructed<Bundle>().size() } answers {
            bundleStores[System.identityHashCode(self)]?.size ?: 0
        }
    }

    @AfterEach
    fun teardownBundleMock() {
        bundleStores.clear()
        unmockkConstructor(Bundle::class)
    }

    @Test
    fun `unsupported push common methods stay fail closed`() {
        val source = File("src/main/java/com/xiaomi/xmsf/stock/StockSurfaceSupport.kt").readText()
        val provider = File("src/main/java/com/xiaomi/push/provider/PushCommonProvider.kt").readText()

        assertTrue(source.contains("fun handlePushCommonCall(method: String?, extras: Bundle?): Bundle"))
        assertTrue(source.contains("else -> Bundle()"))
        assertTrue(provider.contains("StockSurfaceSupport.handlePushCommonCall(method, extras)"))
        assertTrue(!provider.contains("callingPackage"))
    }

    @Test
    fun `provider returns the stock capability probe without accepting unsupported flags`() {
        val provider = PushCommonProvider()

        assertTrue(provider.onCreate())
        assertTrue(
            provider.call(
                "is_push_support",
                null,
                Bundle().apply { putInt("push_support_flag", 4) },
            ).getBoolean("is_supported"),
        )
        assertFalse(
            provider.call(
                "is_push_support",
                null,
                Bundle().apply { putInt("push_support_flag", 524288) },
            ).getBoolean("is_supported"),
        )
        assertFalse(
            provider.call(
                "is_push_support",
                null,
                Bundle().apply { putInt("push_support_flag", 99) },
            ).getBoolean("is_supported"),
        )
        bundleStores.clear()
        assertEquals(0, provider.call("unknown", "ignored", null).size())
    }

    @Test
    fun `stock result helper preserves code data and optional message`() {
        val data = Bundle().apply { putString("value", "ok") }
        val result = StockSurfaceSupport.pushSupportResult(200, data, "accepted")

        assertEquals(200, result.getInt(StockSurfaceSupport.KEY_CODE))
        assertEquals("ok", result.getBundle(StockSurfaceSupport.KEY_DATA)?.getString("value"))
        assertEquals("accepted", result.getString(StockSurfaceSupport.KEY_MSG))
        assertEquals(204, StockSurfaceSupport.pushSupportResult(204).getInt(StockSurfaceSupport.KEY_CODE))
    }
}
