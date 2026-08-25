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
        assertTrue(reconnect.contains("MiPushRuntimePolicyExecutionAdapter.planShouldReconnect("))
        assertTrue(reconnect.contains("MiPushRuntimePolicyExecutionAdapter.planReconnect("))
    }

    private fun readSource(fileName: String): String {
        val candidates = listOf(
            File("src/main/java/io/github/magisk317/mipush/bridge/$fileName"),
            File("../shell/src/main/java/io/github/magisk317/mipush/bridge/$fileName"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $fileName from ${File(".").absolutePath}")
    }
}
