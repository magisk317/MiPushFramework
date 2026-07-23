package io.github.magisk317.mipush.push.pipeline

import android.app.Application
import android.content.Context
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.MIPushAppAbsentManager
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.xmsf.stock.StockProfileIdStore
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ConfirmedRegistrationPersistenceTest {
    @Test
    fun `only successful server registration persists app id`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val packageName = "com.example.target"
        val request = registrationContainer(errorCode = 0L).apply { setIsRequest(true) }
        val failure = registrationContainer(errorCode = 1L)

        assertNull(MiPushRuntimeBridge.resolveConfirmedRegistrationTransition(request))
        assertNull(MiPushRuntimeBridge.resolveConfirmedRegistrationTransition(failure))
        assertFalse(MiPushRuntimeBridge.persistConfirmedRegistrationStateFromContainer(context, request))
        assertFalse(MiPushRuntimeBridge.persistConfirmedRegistrationStateFromContainer(context, failure))
        assertFalse(preferences.contains(packageName))

        MiPushRuntimeBridge.persistConfirmedRegistrationStateFromContainer(
            context,
            registrationContainer(errorCode = 0L),
        )

        assertEquals("app-id", preferences.getString(packageName, null))
    }

    @Test
    fun `successful server unregistration removes persisted app id`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, Context.MODE_PRIVATE)
        val packageName = "com.example.target"
        preferences.edit().putString(packageName, "app-id").commit()
        context.getSharedPreferences("mipush_profile_id", Context.MODE_PRIVATE)
            .edit()
            .putString(packageName, "profile-a")
            .commit()
        val container = MIPushHelper.constructResponseContainer(
            packageName,
            "app-id",
            XmPushActionUnRegistrationResult("request-id", "app-id", 0L),
            ActionType.UnRegistration,
        )

        MiPushRuntimeBridge.persistConfirmedRegistrationStateFromContainer(context, container)

        assertFalse(preferences.contains(packageName))
        assertEquals(listOf("profile-a"), StockProfileIdStore.read(context, packageName))
    }

    @Test
    fun `pending registration app id is separate from confirmed registration`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.pending"
        MIPushAppAbsentManager.forgetRegisteredPackage(context, packageName)
        MIPushAppAbsentManager.forgetPendingRegistration(context, packageName)

        MIPushAppAbsentManager.rememberPendingRegistration(context, packageName, "pending-app-id")

        assertEquals("pending-app-id", MIPushAppAbsentManager.getPendingRegistrationAppId(context, packageName))
        assertNull(MIPushAppAbsentManager.getRememberedAppId(context, packageName))

        MIPushAppAbsentManager.forgetPendingRegistration(context, packageName)
        assertNull(MIPushAppAbsentManager.getPendingRegistrationAppId(context, packageName))
    }

    private fun registrationContainer(errorCode: Long) = MIPushHelper.constructResponseContainer(
        "com.example.target",
        "app-id",
        XmPushActionRegistrationResult("request-id", "app-id", errorCode),
        ActionType.Registration,
    )
}
