package io.github.magisk317.mipush.utils

import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.testing.mockContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegSecUtilsUserScopeTest {
    @AfterEach
    fun tearDown() {
        Utils.context = null
    }

    @Test
    fun `container secret lookup stays within requested user`() {
        Utils.context = mockContext(
            "pref_registered_pkg_names_sec" to mapOf(
                "0:com.example.app" to "primary-secret",
                "999:com.example.app" to "clone-secret",
            ),
        )
        val container = XmPushActionContainer().apply {
            packageName = "com.example.app"
        }

        assertEquals("primary-secret", RegSecUtils.getRegSec(container, userId = 0))
        assertEquals("clone-secret", RegSecUtils.getRegSec(container, userId = 999))
    }
}
