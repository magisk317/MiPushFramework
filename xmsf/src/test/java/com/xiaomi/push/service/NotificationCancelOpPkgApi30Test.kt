package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Verifies that [NotificationManagerPlatformSupport.cancel] passes the host `opPkg`
 * argument on API 30+ (the five-argument cancelNotificationWithTag signature).
 *
 * Stock uses the posting/host package as opPkg. The framework resolves this from the
 * initialized app context (`appContext.packageName`), which for the shipped runtime is
 * `com.xiaomi.xmsf`. This test asserts the resolved host package is used rather than a
 * hardcoded constant.
 *
 * This class uses @Config(sdk=[30]) at class level because the JUnit5 Robolectric
 * extension does not support per-method SDK override.
 *
 * Validates: Requirements 17.2, 9.4
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [30])
class NotificationCancelOpPkgApi30Test {

    @BeforeEach
    fun setUp() {
        mockkStatic(JavaCalls::class)
        // cancel() resolves opPkg from the initialized host context.
        NotificationManagerPlatformSupport.init(RuntimeEnvironment.getApplication())
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(JavaCalls::class)
    }

    @Test
    fun `cancel on API 30 passes host packageName as opPkg in five-arg signature`() {
        // Inject a fake service into the NotificationManagerPlatformSupport singleton
        // so cancel() doesn't throw "service unavailable".
        val fakeService = Any()
        val nmsField = NotificationManagerPlatformSupport::class.java.getDeclaredField("nms")
        nmsField.isAccessible = true
        val originalNms = nmsField.get(null)
        nmsField.set(null, fakeService)

        try {
            val capturedArgs = mutableListOf<Array<out Any?>>()
            every {
                JavaCalls.callMethodOrThrow(any(), eq("cancelNotificationWithTag"), *anyVararg())
            } answers {
                val varargArray = args.last()
                if (varargArray is Array<*>) {
                    capturedArgs.add(varargArray)
                }
                null
            }
            // Mock callStaticMethod for DeviceInfo.getSpaceId → UserHandle.myUserId
            every { JavaCalls.callStaticMethod(any<String>(), any(), *anyVararg()) } returns 0

            val hostPackage = RuntimeEnvironment.getApplication().packageName
            val packageName = "com.example.target"
            NotificationManagerPlatformSupport.cancel(packageName, 99)

            assertTrue(capturedArgs.isNotEmpty(), "cancelNotificationWithTag should have been called")
            val callArgs = capturedArgs.first()
            // On API 30+: cancelNotificationWithTag(pkg, opPkg, tag, id, userId)
            assertEquals(packageName, callArgs[0], "First arg should be target package name")
            assertEquals(
                hostPackage,
                callArgs[1],
                "Second arg (opPkg) should be the resolved host packageName on API 30+",
            )
            assertEquals(null, callArgs[2], "Third arg (tag) should be null")
            assertEquals(99, callArgs[3], "Fourth arg should be notification id")
        } finally {
            nmsField.set(null, originalNms)
        }
    }
}
