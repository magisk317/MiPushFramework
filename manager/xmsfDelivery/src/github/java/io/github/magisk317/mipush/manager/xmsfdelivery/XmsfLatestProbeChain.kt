package io.github.magisk317.mipush.manager.xmsfdelivery

/**
 * Ordered runtime sources for the github flavor, mirroring the manager's own
 * [io.github.magisk317.uikit.update.GitLabCiArtifactUpdateSource] +
 * release-source fallback: the CI artifacts track first (freshest runtime,
 * 14-day window), the generic package registry second (release track, never
 * expires).
 *
 * A source that cannot answer (expired artifacts, cold window, network
 * failure, incompatible build) falls through to the next one; when every
 * source fails the last error propagates and the caller reports it. Keeping
 * the order in one pure class makes the fallback unit-testable without a
 * device.
 */
internal class XmsfLatestProbeChain(
    private val ciArtifacts: XmsfCiArtifactProbe?,
    private val registry: XmsfRuntimeLatestProbe,
) {

    data class Result(val manifest: XmsfRuntimeManifest, val fromCiArtifacts: Boolean)

    /**
     * Newest compatible runtime from the first source that can answer.
     *
     * @throws IllegalStateException when both tracks are unavailable.
     */
    suspend fun fetchLatestCompatible(hostVersionCode: Long): Result {
        val ciManifest = ciArtifacts?.let { probe ->
            runCatching { probe.fetchLatestCompatible(hostVersionCode) }.getOrNull()
        }
        if (ciManifest != null) return Result(ciManifest, fromCiArtifacts = true)
        return Result(
            manifest = registry.fetchLatestCompatible(hostVersionCode),
            fromCiArtifacts = false,
        )
    }
}
