package com.xiaomi.xmsf.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpServiceTest {
    @Test
    fun `known mistat endpoints are handled locally`() {
        assertEquals(
            """{"msg":"write to xlogger success","code":"200"}""",
            HttpService.fakeUploadResponse("https://data.mistat.xiaomi.com/mistats/v3"),
        )
        assertTrue(
            HttpService.fakeUploadResponse(
                "https://data.mistat.intl.xiaomi.com/get_all_config?region=INTL",
            )!!.contains("\"uploadSwitch\":992"),
        )
    }

    @Test
    fun `unknown hosts paths and malformed urls fail closed`() {
        assertNull(HttpService.fakeUploadResponse("https://example.invalid/mistats/v3"))
        assertNull(HttpService.fakeUploadResponse("https://data.mistat.xiaomi.com/unexpected"))
        assertNull(HttpService.fakeUploadResponse("not-a-url"))
        assertNull(HttpService.fakeUploadResponse(null))
    }
}
