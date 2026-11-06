package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.common.Constants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiveUpdatePermissionPolicyTest {
    @Test
    fun `live update grants use the caller and target package owners`() {
        val targetPackage = "com.example.target"

        assertEquals(
            listOf(
                PermissionUtils.PermissionGrantTarget(
                    packageName = Constants.SERVICE_APP_NAME,
                    operation = PermissionUtils.LIVE_UPDATE_CALLER_PERMISSION,
                ),
                PermissionUtils.PermissionGrantTarget(
                    packageName = targetPackage,
                    operation = PermissionUtils.LIVE_UPDATE_TARGET_PERMISSION,
                ),
            ),
            PermissionUtils.liveUpdatePermissionGrantTargets(targetPackage),
        )
    }

    @Test
    fun `target identity retry only requires the caller permission`() {
        assertTrue(
            PermissionUtils.LiveUpdateGrantResult(
                callerPermissionGranted = true,
                targetAppOpAllowed = true,
            ).canRetryTargetPost,
        )
        assertTrue(
            PermissionUtils.LiveUpdateGrantResult(
                callerPermissionGranted = true,
                targetAppOpAllowed = false,
            ).canRetryTargetPost,
        )
        assertFalse(
            PermissionUtils.LiveUpdateGrantResult(
                callerPermissionGranted = false,
                targetAppOpAllowed = true,
            ).canRetryTargetPost,
        )
    }

    @Test
    fun `target promoted appop parser only accepts allow`() {
        assertTrue(
            PermissionUtils.isLiveUpdateAppOpAllowed(
                "POST_PROMOTED_NOTIFICATIONS: allow; rejectTime=+1d",
            ),
        )
        assertFalse(
            PermissionUtils.isLiveUpdateAppOpAllowed(
                "POST_PROMOTED_NOTIFICATIONS: default",
            ),
        )
        assertFalse(
            PermissionUtils.isLiveUpdateAppOpAllowed(
                "POST_NOTIFICATIONS: allow",
            ),
        )
    }
}
