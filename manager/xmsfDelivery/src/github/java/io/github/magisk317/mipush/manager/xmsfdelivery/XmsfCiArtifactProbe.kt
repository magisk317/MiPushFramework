package io.github.magisk317.mipush.manager.xmsfdelivery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Probes the GitLab CI artifacts staging area (ci-dist) for the XMSF runtime
 * published by the latest successful android:build pipeline on the CI track.
 *
 * The artifacts raw endpoint exposes no per-file listing, so the version is
 * resolved from a plain-text sidecar (ci-dist/com.xiaomi.xmsf/version.txt)
 * staged in the
 * same job — the same pattern the manager's own OTA uses for
 * ci-dist/io.github.magisk317.mipush/version.txt. The manifest then goes through the exact same
 * [XmsfRuntimeManifest.validate] gate as the registry probe, so an
 * incompatible CI build is never offered.
 *
 * Artifacts expire (14 days), which is the intended lifecycle of a CI
 * artifact: an expired window simply fails here and the caller falls back to
 * the registry release track, so the UI never points at a vanished version.
 *
 * The base URLs are injectable so tests can point them at a MockWebServer,
 * mirroring [XmsfRuntimeLatestProbe].
 */
class XmsfCiArtifactProbe(
    private val versionSidecarUrl: String = VERSION_SIDECAR_URL,
    private val manifestBaseUrl: String = MANIFEST_BASE_URL,
    private val httpClient: OkHttpClient = XmsfDownloader.defaultHttpClient(),
) {

    /**
     * Newest CI-track runtime manifest for this host.
     *
     * @throws IllegalStateException when the sidecar or manifest is missing
     *   (expired artifacts, cold window), the version does not match, or the
     *   manifest is rejected by [XmsfRuntimeManifest.validate].
     */
    suspend fun fetchLatestCompatible(hostVersionCode: Long): XmsfRuntimeManifest =
        withContext(Dispatchers.IO) {
            val versionName = downloadText(versionSidecarUrl).trim()
            check(versionName.isNotBlank()) { "ci artifacts version sidecar is blank" }
            val manifest = XmsfRuntimeManifest.parse(
                downloadText("$manifestBaseUrl/$versionName/${XmsfDownloader.MANIFEST_NAME}"),
            )
            check(manifest.versionName == versionName) {
                "ci manifest version ${manifest.versionName} does not match sidecar $versionName"
            }
            manifest.validate(hostVersionCode)?.let { reason ->
                error("ci runtime manifest rejected: $reason")
            }
            manifest
        }

    private fun downloadText(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("ci artifacts probe failed: HTTP ${response.code} for $url")
            }
            return response.body.string()
        }
    }

    companion object {
        /**
         * Plain-text sidecar staged by the android:build job carrying the
         * xmsf runtime APK's embedded versionName.
         */
        const val VERSION_SIDECAR_URL =
            "https://gitlab.com/magisk3171/MiPushFramework/-/jobs/artifacts/beta/raw" +
                "/ci-dist/com.xiaomi.xmsf/version.txt?job=android:build"

        /**
         * Base URL of the staged runtime payload, keyed by the runtime's own
         * package name and versionName (com.xiaomi.xmsf/<versionName>/...),
         * the same two-level shape the generic package registry uses, so the
         * downloader only swaps this URL and keeps its whole verification
         * chain: manifest gate -> sha256 sidecar -> same-signature check.
         */
        const val MANIFEST_BASE_URL =
            "https://gitlab.com/magisk3171/MiPushFramework/-/jobs/artifacts/beta/raw" +
                "/ci-dist/com.xiaomi.xmsf"
    }
}
