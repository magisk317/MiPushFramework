package io.github.magisk317.mipush.manager.runtime

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerRuntimeServiceContractTest {
    @Test
    fun `bind requires the expected action and explicit component`() {
        val controller = Robolectric.buildService(ManagerRuntimeService::class.java).create()
        val service = controller.get()
        val component = ComponentName(service, ManagerRuntimeService::class.java)

        assertNull(service.onBind(Intent(ManagerProtocol.SERVICE_ACTION)))
        assertNull(service.onBind(Intent("wrong.action").setComponent(component)))
        assertNotNull(service.onBind(Intent(ManagerProtocol.SERVICE_ACTION).setComponent(component)))

        controller.destroy()
    }

    @Test
    fun `handshake reports installed runtime version and blocks only major mismatch`() {
        val controller = Robolectric.buildService(ManagerRuntimeService::class.java).create()
        val service = controller.get()
        val component = ComponentName(service, ManagerRuntimeService::class.java)
        val binder = service.onBind(Intent(ManagerProtocol.SERVICE_ACTION).setComponent(component))
        val remote = IManagerRuntimeService.Stub.asInterface(binder)
        val packageInfo = service.packageManager.getPackageInfo(service.packageName, 0)

        val compatible = remote.handshake(ManagerProtocol.MAJOR, ManagerProtocol.MINOR)
        assertEquals(packageInfo.versionName.orEmpty(), compatible.runtimeVersionName)
        assertEquals(packageInfo.longVersionCode, compatible.runtimeVersionCode)
        assertTrue(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT in compatible.supportedCapabilities)
        assertTrue(ManagerProtocol.CAPABILITY_APPLICATION_LIST in compatible.supportedCapabilities)
        assertTrue(ManagerProtocol.CAPABILITY_APPLICATION_DETAIL in compatible.supportedCapabilities)
        assertTrue(ManagerProtocol.CAPABILITY_APPLICATION_DIAGNOSTICS in compatible.supportedCapabilities)

        val incompatible = remote.handshake(ManagerProtocol.MAJOR + 1, ManagerProtocol.MINOR)
        assertEquals("protocol_major_mismatch", incompatible.compatibilityReason)
        assertTrue(incompatible.supportedCapabilities.isEmpty())

        controller.destroy()
    }

    @Test
    fun `application endpoints reject invalid requests before runtime reads`() {
        val controller = Robolectric.buildService(ManagerRuntimeService::class.java).create()
        val service = controller.get()
        val component = ComponentName(service, ManagerRuntimeService::class.java)
        val binder = service.onBind(Intent(ManagerProtocol.SERVICE_ACTION).setComponent(component))
        val remote = IManagerRuntimeService.Stub.asInterface(binder)

        assertThrows(IllegalArgumentException::class.java) {
            remote.getApplicationPage(ManagerApplicationQueryDto(pageSize = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            remote.getApplicationDetail("not-a-package", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            remote.getApplicationDiagnostics("valid.package", 99)
        }

        controller.destroy()
    }
}
