package io.github.magisk317.mipush.notification

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the publish-time channel mismatch: publish() resolves (and possibly
 * fallback-remaps) the channel before posting, so the final validation inside
 * notify() must reuse that channel instead of re-querying it by identity.
 */
class NotificationControllerChannelReuseTest {

    @Test
    fun `resolved channel is reused when built channel id matches`() {
        assertTrue(
            NotificationController.shouldReuseResolvedChannel(
                resolvedChannelId = "mipush_com.example_chat",
                builtChannelId = "mipush_com.example_chat",
            ),
        )
    }

    @Test
    fun `resolved channel is not reused when built channel id changed`() {
        assertFalse(
            NotificationController.shouldReuseResolvedChannel(
                resolvedChannelId = "mipush_com.example_chat",
                builtChannelId = "mipush_com.example_other",
            ),
        )
    }

    @Test
    fun `resolved channel is not reused when absent`() {
        assertFalse(
            NotificationController.shouldReuseResolvedChannel(
                resolvedChannelId = null,
                builtChannelId = "mipush_com.example_chat",
            ),
        )
    }

    @Test
    fun `final validation reuses the publish resolved channel`() {
        val source = resolveNotificationControllerSource().readText()

        assertTrue(source.contains("resolveFinalPublishChannel("))
        val finalCheck = source
            .substringAfter("val notification = NativeNotificationFeatureBuilder.buildNotification(context, notificationBuilder, nativeFeature)")
            .substringAfter("resolveFinalPublishChannel(")
        assertTrue(finalCheck.contains("resolvedChannel = resolvedChannel,"))
        val notifyCall = source.substringAfter("val notifyResult = notify(").substringBefore("\n        )")
        assertTrue(notifyCall.contains("resolvedChannel = channel,"))
    }

    private fun resolveNotificationControllerSource(): File {
        val relativePath =
            "src/main/java/io/github/magisk317/mipush/notification/NotificationController.kt"
        val workingDirectory = System.getProperty("user.dir") ?: "."
        // The controller moved into this module, so the module-local copy is the primary
        // candidate; the shell path stays for callers that still resolve from there.
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .take(6)
            .flatMap { root ->
                sequenceOf(
                    root.resolve(relativePath),
                    root.resolve("xmsf/notification/$relativePath"),
                    root.resolve("xmsf/shell/$relativePath"),
                )
            }
            .firstOrNull(File::isFile)
            ?: error("NotificationController.kt not found from $workingDirectory")
    }
}
