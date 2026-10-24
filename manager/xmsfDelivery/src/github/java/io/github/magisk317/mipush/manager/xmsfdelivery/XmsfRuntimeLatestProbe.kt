package io.github.magisk317.mipush.manager.xmsfdelivery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Probes the GitLab generic package registry for the newest published XMSF
 * runtime this manager host can actually install.
 *
 * The registry is anonymous-readable, so the probe is a plain GET: list the
 * `xmsf-runtime` package versions, re-order them with
 * [XmsfVersionComparator] (the API sort order is not trusted — pre-release
 * suffixes would rank wrong), then walk from the newest down and return the
 * first version whose manifest passes [XmsfRuntimeManifest.validate] against
 * this host. Versions that need a newer manager are skipped rather than
 * offered: the homepage would otherwise promise an install that cannot
 * succeed.
 *
 * The base URLs are injectable so tests can point them at a MockWebServer,
 * mirroring [XmsfDownloader].
 */
class XmsfRuntimeLatestProbe(
    private val packagesApiUrl: String = PACKAGES_API_URL,
    private val manifestBaseUrl: String = XmsfDownloader.BASE_URL,
    private val httpClient: OkHttpClient = XmsfDownloader.defaultHttpClient(),
) {

    /**
     * Newest compatible published runtime manifest.
     *
     * @throws IllegalStateException when the registry is unreachable, lists
     *   no versions, or holds none this host may install.
     */
    suspend fun fetchLatestCompatible(hostVersionCode: Long): XmsfRuntimeManifest =
        withContext(Dispatchers.IO) {
            val versions = fetchVersions()
            check(versions.isNotEmpty()) { "registry lists no xmsf-runtime packages" }
            val newestFirst = versions.sortedWith(XmsfVersionComparator).asReversed()
            for (version in newestFirst) {
                val manifest = runCatching { fetchManifest(version) }.getOrNull() ?: continue
                if (manifest.versionName != version) continue
                if (manifest.validate(hostVersionCode) == null) return@withContext manifest
            }
            error("no published runtime is compatible with manager versionCode $hostVersionCode")
        }

    /** Version strings published under the xmsf-runtime package. */
    private fun fetchVersions(): List<String> {
        val text = get(packagesApiUrl)
        return XmsfRuntimeManifest.json
            .decodeFromString<List<GitLabPackage>>(text)
            .map { it.version }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun fetchManifest(versionName: String): XmsfRuntimeManifest =
        XmsfRuntimeManifest.parse(get("$manifestBaseUrl/$versionName/${XmsfDownloader.MANIFEST_NAME}"))

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("registry probe failed: HTTP ${response.code} for $url")
            return response.body.string()
        }
    }

    @Serializable
    private data class GitLabPackage(val version: String = "")

    companion object {
        /**
         * Anonymous package listing for the public MiPushFramework project.
         * `per_page` bounds the walk; the newest-first sort is advisory only.
         */
        const val PACKAGES_API_URL =
            "https://gitlab.com/api/v4/projects/${XmsfDownloader.PROJECT_ID}/packages" +
                "?package_name=xmsf-runtime&per_page=20&order_by=created_at&sort=desc"
    }
}
