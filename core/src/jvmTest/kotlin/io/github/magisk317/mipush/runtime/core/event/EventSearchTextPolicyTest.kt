package io.github.magisk317.mipush.runtime.core.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventSearchTextPolicyTest {

    @Test
    fun `compose preserves display order and removes duplicates`() {
        assertEquals(
            "com.example.app Example notification body",
            EventSearchTextPolicy.compose(
                packageName = "com.example.app",
                applicationName = "Example",
                title = "notification",
                summary = "body",
            ),
        )
        assertEquals(
            "com.example.app Example",
            EventSearchTextPolicy.compose(
                packageName = "com.example.app",
                applicationName = "Example",
                title = "Example",
                summary = " ",
            ),
        )
    }

    @Test
    fun `compose ignores null and blank fields`() {
        assertEquals(
            "com.example.app",
            EventSearchTextPolicy.compose(
                packageName = "com.example.app",
                applicationName = null,
                title = "",
                summary = "  ",
            ),
        )
        assertEquals("", EventSearchTextPolicy.compose(null, null, null, null))
    }
}
