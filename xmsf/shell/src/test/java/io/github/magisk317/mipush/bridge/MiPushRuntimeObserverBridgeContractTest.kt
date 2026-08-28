package io.github.magisk317.mipush.bridge

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushRuntimeObserverBridgeContractTest {
    @Test
    fun `bridge remains a vendor ABI facade`() {
        val source = readSource("MiPushRuntimeObserverBridge.kt")

        assertTrue(source.contains("class MiPushRuntimeObserverBridge(private val context: Context) : IPushRuntimeObserver"))
        assertTrue(source.contains("XMPushServiceCore.observer = this"))
        assertTrue(source.contains("compatibilityAdapter.getMIID()"))
        assertTrue(source.contains("accountRecoveryCoordinator.scheduleInvalidSignatureRefresh()"))
        assertTrue(source.contains("reconnectCoordinator.resolveShouldReconnectPlan"))
        assertTrue(source.contains("reconnectCoordinator.resolveReconnectAttemptPlan"))

        assertFalse(source.contains("service.executeJob("))
        assertFalse(source.contains("service.scheduleConnect("))
        assertFalse(source.contains("Network.hasNetwork("))
        assertFalse(source.contains("MIPushAccountUtils.getMIPushAccount("))
        assertFalse(source.contains("ReconnectDebugLog.w("))
        assertFalse(source.contains("logW("))
    }

    @Test
    fun `coordination keeps account client and policy boundaries`() {
        val bridge = readSource("MiPushRuntimeObserverBridge.kt")
        val reconnect = readSource("MiPushRuntimeReconnectCoordinator.kt")
        val recovery = readSource("MiPushRuntimeAccountRecoveryCoordinator.kt")

        assertTrue(bridge.indexOf("private val accountClientCoordinator") <
            bridge.indexOf("private val accountRecoveryCoordinator"))
        assertTrue(bridge.indexOf("private val accountClientCoordinator") <
            bridge.indexOf("private val reconnectCoordinator"))
        assertTrue(bridge.contains("observer = this"))
        assertTrue(recovery.contains("private val observer: IPushRuntimeObserver"))
        assertTrue(recovery.contains("MIPushAccountUtils.register("))
        assertTrue(reconnect.contains("import io.github.magisk317.mipush.runtime.core.PushConnectionPlanFactory"))
        assertTrue(reconnect.contains("import io.github.magisk317.mipush.runtime.core.PushReconnectPolicy"))
        assertTrue(reconnect.contains("PushConnectionPlanFactory.planShouldReconnect("))
        assertTrue(reconnect.contains("PushReconnectPolicy.planReconnect("))
        assertFalse(reconnect.contains("MiPushRuntimePolicyExecutionAdapter.planShouldReconnect("))
        assertFalse(reconnect.contains("MiPushRuntimePolicyExecutionAdapter.planReconnect("))
        assertFalse(reconnect.contains("XMPushServiceLifecycleBridge.peekService()"))
        assertFalse(recovery.contains("XMPushServiceLifecycleBridge.peekService()"))
    }

    @Test
    fun `service readers use observer-owned current service`() {
        val observerBridge = readSource("MiPushRuntimeObserverBridge.kt")
        assertTrue(observerBridge.contains("fun currentService(): XMPushServiceCore?"))
        assertTrue(observerBridge.contains("return bridge.observerState.service()"))

        listOf(
            "io/github/magisk317/mipush/runtime/data/EventRepository.kt",
            "io/github/magisk317/mipush/bridge/MiPushRuntimeMessageNotificationExecutionAdapter.kt",
            "io/github/magisk317/mipush/manager/runtime/write/ManagerRuntimePreferenceCommandSupport.kt",
            "io/github/magisk317/mipush/service/runtime/RuntimeSettingsAdapter.kt",
            "com/xiaomi/xmsf/push/service/MiPushFacadeService.kt",
        ).forEach { relativePath ->
            val source = readShellSource(relativePath)
            assertFalse(source.contains("XMPushServiceLifecycleBridge.peekService()"), relativePath)
            assertTrue(source.contains("MiPushRuntimeObserverBridge.currentService()"), relativePath)
        }
    }

    private fun readSource(fileName: String): String {
        return readShellSource("io/github/magisk317/mipush/bridge/$fileName")
    }

    private fun readShellSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("../shell/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
