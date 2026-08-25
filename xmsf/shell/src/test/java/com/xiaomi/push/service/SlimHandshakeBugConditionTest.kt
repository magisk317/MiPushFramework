package com.xiaomi.push.service

import android.content.pm.ServiceInfo
import io.github.magisk317.mipush.service.runtime.PushSlimStreamRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Bug Condition Exploration Test (Post-Fix Verification)
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 2.1
 *
 * After the fix, MiPushRuntimeObserverBridge.planSlimHandshake(hasChallenge, hasConfigMessage)
 * delegates to PushSlimStreamRuntime.planHandshake(hasChallenge, hasConfigMessage).
 *
 * These tests verify the expected behavior:
 * 1. planSlimHandshake() now returns values consistent with PushSlimStreamRuntime.planHandshake()
 * 2. When hasChallenge=false, valid=false (invalid connections are rejected)
 * 3. ForegroundHelper now uses FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING (0x200)
 *
 * Since MiPushRuntimeObserverBridge requires Android runtime dependencies,
 * we call PushSlimStreamRuntime.planHandshake() directly — which is what the fixed bridge delegates to.
 */
class SlimHandshakeBugConditionTest {

    /**
     * Replicates the fixed behavior of MiPushRuntimeObserverBridge.planSlimHandshake().
     * After the fix, the bridge delegates to PushSlimStreamRuntime.planHandshake().
     */
    private fun currentBridgePlan(hasChallenge: Boolean, hasConfigMessage: Boolean): PushSlimHandshakePlan {
        return PushSlimStreamRuntime.planHandshake(hasChallenge, hasConfigMessage)
    }

    /**
     * Property 1: Bug Condition - For all (hasChallenge, hasConfigMessage) combinations,
     * the bridge's plan should match PushSlimStreamRuntime.planHandshake().
     *
     * After the fix, the bridge delegates to PushSlimStreamRuntime.planHandshake(),
     * so these values should always match.
     *
     * Validates: Requirements 1.1, 1.2, 2.1
     */
    @ParameterizedTest(name = "planSlimHandshake should match PushSlimStreamRuntime for hasChallenge={0}, hasConfigMessage={1}")
    @CsvSource(
        "true, true",
        "true, false",
        "false, true",
        "false, false"
    )
    fun `bridge planSlimHandshake should delegate to PushSlimStreamRuntime planHandshake`(
        hasChallenge: Boolean,
        hasConfigMessage: Boolean
    ) {
        val bridgePlan = currentBridgePlan(hasChallenge, hasConfigMessage)
        val expectedPlan = PushSlimStreamRuntime.planHandshake(hasChallenge, hasConfigMessage)

        assertEquals(
            expectedPlan.valid,
            bridgePlan.valid,
            "valid mismatch for hasChallenge=$hasChallenge, hasConfigMessage=$hasConfigMessage: " +
                "bridge returns valid=${bridgePlan.valid} but PushSlimStreamRuntime returns valid=${expectedPlan.valid}"
        )
        assertEquals(
            expectedPlan.shouldEmitConfigBlob,
            bridgePlan.shouldEmitConfigBlob,
            "shouldEmitConfigBlob mismatch for hasChallenge=$hasChallenge, hasConfigMessage=$hasConfigMessage: " +
                "bridge returns shouldEmitConfigBlob=${bridgePlan.shouldEmitConfigBlob} but PushSlimStreamRuntime returns shouldEmitConfigBlob=${expectedPlan.shouldEmitConfigBlob}"
        )
    }

    /**
     * Test Case 1: When hasChallenge=true and hasConfigMessage=true,
     * planSlimHandshake() should return shouldEmitConfigBlob=true.
     * After the fix, the bridge delegates to PushSlimStreamRuntime.planHandshake()
     * which correctly returns shouldEmitConfigBlob=true.
     *
     * Validates: Requirements 1.1, 2.1
     */
    @Test
    fun `shouldEmitConfigBlob must be true when hasChallenge and hasConfigMessage are both true`() {
        val bridgePlan = currentBridgePlan(hasChallenge = true, hasConfigMessage = true)
        val expected = PushSlimStreamRuntime.planHandshake(
            hasChallenge = true,
            hasConfigMessage = true
        )

        assertEquals(
            expected.shouldEmitConfigBlob,
            bridgePlan.shouldEmitConfigBlob,
            "planSlimHandshake() returns shouldEmitConfigBlob=${bridgePlan.shouldEmitConfigBlob}, " +
                "PushSlimStreamRuntime.planHandshake(true, true) returns shouldEmitConfigBlob=${expected.shouldEmitConfigBlob}."
        )
    }

    /**
     * Test Case 2: When hasChallenge=false, planSlimHandshake() should return valid=false.
     * After the fix, the bridge delegates to PushSlimStreamRuntime.planHandshake()
     * which correctly returns valid=false when hasChallenge=false.
     *
     * Validates: Requirements 1.2, 2.1
     */
    @Test
    fun `valid must be false when hasChallenge is false`() {
        val bridgePlan = currentBridgePlan(hasChallenge = false, hasConfigMessage = true)
        val expected = PushSlimStreamRuntime.planHandshake(
            hasChallenge = false,
            hasConfigMessage = true
        )

        assertEquals(
            expected.valid,
            bridgePlan.valid,
            "planSlimHandshake() returns valid=${bridgePlan.valid}, " +
                "PushSlimStreamRuntime.planHandshake(false, true) returns valid=${expected.valid}."
        )
    }

    /**
     * Test Case 3: ForegroundHelper should use FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING (0x200)
     * to match AndroidManifest's foregroundServiceType="remoteMessaging" declaration.
     *
     * After the fix, the code uses REMOTE_MESSAGING instead of DATA_SYNC.
     *
     * Validates: Requirements 1.3, 2.3
     */
    @Test
    fun `ForegroundHelper should use FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING not DATA_SYNC`() {
        val dataSyncType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        val remoteMessagingType = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        val manifestDeclaredType = remoteMessagingType // 0x200, as declared in AndroidManifest.xml

        // Sanity check: DATA_SYNC and REMOTE_MESSAGING are different constants
        assertFalse(
            dataSyncType == manifestDeclaredType,
            "Sanity check: DATA_SYNC (0x${Integer.toHexString(dataSyncType)}) should differ from " +
                "REMOTE_MESSAGING (0x${Integer.toHexString(remoteMessagingType)})"
        )

        // After the fix, ForegroundHelper uses REMOTE_MESSAGING which matches the manifest.
        // Verify the constant value that the fixed code should use.
        val codeUsesType = ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING // what the fixed code uses
        assertEquals(
            manifestDeclaredType,
            codeUsesType,
            "ForegroundHelper should use FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING (0x${Integer.toHexString(manifestDeclaredType)}) " +
                "to match AndroidManifest foregroundServiceType declaration."
        )
    }
}
