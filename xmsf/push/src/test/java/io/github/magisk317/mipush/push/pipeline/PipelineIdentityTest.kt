package io.github.magisk317.mipush.push.pipeline

import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineIdentityTest {

    private fun container(
        packageName: String?,
        extra: Map<String, String>? = null,
    ): XmPushActionContainer = XmPushActionContainer().apply {
        this.packageName = packageName
        if (extra != null) {
            metaInfo = PushMetaInfo().apply { this.extra = extra }
        }
    }

    @Test
    fun `resolve prefers the miui target package override`() {
        val c = container(
            packageName = "com.xiaomi.xmsf",
            extra = mapOf(MIPushNotificationHelper.MIUI_PACKAGE_NAME to "com.target.app"),
        )
        assertEquals("com.target.app", StalePackagePushGuard.resolveTargetPackage(c))
    }

    @Test
    fun `resolve falls back to the container package when no override exists`() {
        val c = container(packageName = "com.target.app")
        assertEquals("com.target.app", StalePackagePushGuard.resolveTargetPackage(c))
    }

    @Test
    fun `resolve returns null for a packageless container`() {
        assertNull(StalePackagePushGuard.resolveTargetPackage(container(packageName = null)))
    }

    @Test
    fun `identity returns null for a null container`() {
        assertNull(MessageIdentity.fromContainer(null))
    }

    @Test
    fun `identity prefers the job id from the extra map`() {
        val c = container(
            packageName = "com.target.app",
            extra = mapOf(
                PushConstants.EXTRA_JOB_KEY to "job-1",
                MIPushNotificationHelper.MIUI_PACKAGE_NAME to "com.target.app",
            ),
        )
        assertEquals("job-1", MessageIdentity.fromContainer(c))
    }

    @Test
    fun `identity from meta rejects blank ids`() {
        assertNull(MessageIdentity.fromMeta(null))
        assertNull(MessageIdentity.fromMeta(""))
    }

    @Test
    fun `app data cleared keeps the stock wire contract`() {
        assertEquals("app_data_cleared", PackageDataClearedCoordinator.APP_DATA_CLEARED_TYPE)
    }
}
