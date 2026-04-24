package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushVersionInfoTest {

    @Test
    fun `stock baseline constants match pulled xmsf baseline`() {
        assertEquals("7.4.67-C", PushVersionInfo.STOCK_XMSF_APP_VERSION_NAME)
        assertEquals(70004067, PushVersionInfo.STOCK_XMSF_APP_VERSION_CODE)
        assertEquals("7_8_2-C", PushVersionInfo.PUSH_SDK_VERSION_NAME)
        assertEquals(70082, PushVersionInfo.PUSH_SDK_VERSION_CODE)
    }

    @Test
    fun `push constants expose aligned stock facing versions`() {
        assertEquals(PushVersionInfo.PUSH_SDK_VERSION_NAME, PushConstants.PUSH_VERSION_NAME)
        assertEquals(PushVersionInfo.PUSH_SDK_VERSION_CODE, PushConstants.PUSH_VERSION_CODE)
        assertEquals(PushVersionInfo.STOCK_XMSF_APP_VERSION_NAME, PushConstants.FRAMEWORK_APP_VERSION_NAME)
        assertEquals(PushVersionInfo.STOCK_XMSF_APP_VERSION_CODE, PushConstants.FRAMEWORK_APP_VERSION_CODE)
    }

    @Test
    fun `framework package reports stock compatible app version while other packages keep actual version`() {
        assertEquals(
            "7.4.67-C",
            PushVersionInfo.reportedAppVersionName(PushConstants.PUSH_SERVICE_PACKAGE_NAME, "0.3.17"),
        )
        assertEquals(
            70004067,
            PushVersionInfo.reportedAppVersionCode(PushConstants.PUSH_SERVICE_PACKAGE_NAME, 1003003000),
        )
        assertEquals(
            "1.2.3",
            PushVersionInfo.reportedAppVersionName("com.example.app", "1.2.3"),
        )
        assertEquals(
            123,
            PushVersionInfo.reportedAppVersionCode("com.example.app", 123),
        )
    }

    @Test
    fun `push sdk extras stay stock aligned across all stock facing keys`() {
        val extras = LinkedHashMap<String, String>()

        PushVersionInfo.appendPushSdkExtras(extras)

        assertEquals(
            linkedMapOf(
                "push_sdk_vn" to "7_8_2-C",
                "push_sdk_vc" to "70082",
                "cpvn" to "7_8_2-C",
                "cpvc" to "70082",
            ),
            extras,
        )
    }

    @Test
    fun `client extra helper emits aligned stock channel markers`() {
        val clientExtra = PushVersionInfo.buildClientExtraAttributes(
            runningPackages = "com.example.app",
            countryCode = "CN",
            region = "CN",
        )

        assertEquals("41", clientExtra["sdk_ver"])
        assertEquals("7_8_2-C", clientExtra["cpvn"])
        assertEquals("70082", clientExtra["cpvc"])
        assertEquals("com.example.app", clientExtra["aapn"])
        assertEquals("CN", clientExtra["country_code"])
        assertEquals("CN", clientExtra["region"])
    }

    @Test
    fun `account registration params use stock runtime versions without clobbering package name`() {
        val params = linkedMapOf("packagename" to "com.example.app")

        PushVersionInfo.appendAccountRegistrationParams(
            target = params,
            packageName = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
            actualVersionCode = 1003003000,
        )

        assertEquals("70004067", params["appversion"])
        assertEquals("70082", params["sdkversion"])
        assertEquals("com.example.app", params["packagename"])
        assertTrue(params.containsKey("sdkversion"))
    }
}
