package com.xiaomi.xmsf.push.service

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the single-ingress-emitter contract: every external intent observed by
 * [MiPushFacadeService] must produce exactly one `push.receive` event, classified through the
 * shared policy gate, with the target package attached.
 */
class ExternalPushIngressEmissionTest {

    @Test
    fun `ingress result classifies policy denials as skip and real rejections as error`() {
        assertEquals("ok", ExternalPushIngress.ingressResult(null))
        // Design-intent denials: telemetry/notification-exposure intents and every action outside
        // the public surface (SDK client-report broadcasts dominate the latter).
        assertEquals("skip", ExternalPushIngress.ingressResult("telemetry_disabled"))
        assertEquals("skip", ExternalPushIngress.ingressResult("action_not_public"))
        // Genuine failures stay errors so alerting on ingress regressions keeps working.
        assertEquals("error", ExternalPushIngress.ingressResult("invalid_package"))
        assertEquals("error", ExternalPushIngress.ingressResult("package_not_installed"))
        assertEquals("error", ExternalPushIngress.ingressResult("caller_package_mismatch"))
        assertEquals("error", ExternalPushIngress.ingressResult("unsupported_message"))
    }

    @Test
    fun `validateStart no longer emits its own ingress event`() {
        val source = readShellSource("com/xiaomi/xmsf/push/service/ExternalPushIngress.kt")
        val validateStartBody = source
            .substringAfter("fun validateStart(")
            .substringBefore("fun validateBoundMessage(")
        assertTrue(validateStartBody.contains("ExternalPushIntentPolicy.validate(context, intent)"))
        assertFalse(validateStartBody.contains("emitIngress"))
    }

    @Test
    fun `validateBoundMessage keeps only transport pre-check emissions`() {
        val source = readShellSource("com/xiaomi/xmsf/push/service/ExternalPushIngress.kt")
        val validateBody = source
            .substringAfter("fun validateBoundMessage(")
            .substringBefore("fun replyRegion(")
        // The three transport-level pre-checks never reach the facade emitter, so they stay.
        assertTrue(validateBody.contains("reason = \"unsupported_message\""))
        assertTrue(validateBody.contains("reason = \"invalid_message\""))
        assertTrue(validateBody.contains("reason = \"unknown_caller\""))
        // The validation outcome itself is reported once by the facade's single emitter.
        val afterValidation = validateBody.substringAfter("ExternalPushIntentPolicy.validate(")
        assertFalse(afterValidation.contains("emitIngress"))
    }

    @Test
    fun `facade submits every external intent through one classified emitter`() {
        val source = readShellSource("com/xiaomi/xmsf/push/service/MiPushFacadeService.kt")
        assertTrue(source.contains("ExternalPushIngress.ingressResult(rejection)"))
        assertTrue(source.contains("\"source\" to source"))
        assertTrue(source.contains("source = \"bound\""))
        assertTrue(source.contains("source = \"start\""))
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
