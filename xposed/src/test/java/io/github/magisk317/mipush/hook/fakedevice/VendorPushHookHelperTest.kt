package io.github.magisk317.mipush.hook.fakedevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VendorPushHookHelperTest {
    @Before
    fun setUp() {
        VendorPushHookHelper.resetForTest()
    }

    @Test
    fun `force rules only apply to matching return types`() {
        assertTrue(
            VendorPushHookHelper.canForce(
                java.lang.Boolean.TYPE,
                VendorForceAction.BooleanFalse,
            ),
        )
        assertFalse(
            VendorPushHookHelper.canForce(
                java.lang.Integer.TYPE,
                VendorForceAction.BooleanFalse,
            ),
        )
        assertTrue(
            VendorPushHookHelper.canForce(
                java.lang.Integer.TYPE,
                VendorForceAction.IntValue(1),
            ),
        )
        assertFalse(
            VendorPushHookHelper.canForce(
                java.lang.Boolean.TYPE,
                VendorForceAction.IntValue(1),
            ),
        )
    }

    @Test
    fun `forced values match action and return type`() {
        assertEquals(false, VendorPushHookHelper.forcedValue(Boolean::class.java, VendorForceAction.BooleanFalse))
        assertEquals(1, VendorPushHookHelper.forcedValue(Int::class.java, VendorForceAction.IntValue(1)))
        assertEquals(null, VendorPushHookHelper.forcedValue(String::class.java, VendorForceAction.IntValue(1)))
    }

    @Test
    fun `diagnostic rules match explicit names and keywords`() {
        val rule = VendorDiagnosticRule(
            methodNames = setOf("register"),
            methodKeywords = setOf("token", "regid"),
            reason = "test",
        )

        assertTrue(VendorPushHookHelper.shouldDiagnose("register", rule))
        assertTrue(VendorPushHookHelper.shouldDiagnose("getToken", rule))
        assertTrue(VendorPushHookHelper.shouldDiagnose("queryRegId", rule))
        assertFalse(VendorPushHookHelper.shouldDiagnose("isSupport", rule))
    }

    @Test
    fun `sensitive values are redacted in logs`() {
        val value = VendorPushHookHelper.sanitizeForLog(
            "token=abcdef1234567890 appId=2882303761517999999 account=user@example.com",
        )

        assertTrue(value.contains("token=abcd...7890"))
        assertTrue(value.contains("appId=2882...9999"))
        assertTrue(value.contains("account=user....com"))
    }

    @Test
    fun `hook keys are de duplicated`() {
        assertTrue(VendorPushHookHelper.markHooked("pkg#method"))
        assertFalse(VendorPushHookHelper.markHooked("pkg#method"))
    }
}
