package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
/**
 * Downloads the pinned XMSF runtime APK from the GitLab generic package
 * registry and verifies it before anything installs it.
 *
 * Security chain, in order: fetch the manifest for the pinned version, gate
 * it against the host contract, verify the sha256 sidecar (falling back to
 * the manifest hash when no sidecar is published), and leave signature
 * enforcement to [XmsfSignatureVerifier] on the downloaded file.
 *
 * The base URL is injectable so tests can point it at a MockWebServer; the
 * project id mirrors the xinyi E2EE plugin precedent (public GitLab project).
 */
class XmsfDownloader(
    context: Context,
    private val expectedVersionName: String = BuildConfig.XMSF_DELIVERY_VERSION,
    private val baseUrl: String = BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val hostVersionCodeProvider: (() -> Long)? = null,
) {

    private val appContext = context.applicationContext ?: context

    /** Result of a download attempt: the verified APK and its manifest. */
    data class Downloaded(val apk: File, val manifest: XmsfRuntimeManifest)

    /**
     * Return the pinned runtime APK, downloading it when the cache is cold.
     * A cached file is re-verified (hash + manifest) so a corrupted cache
     * never reaches the installer.
     *
     * @throws IllegalStateException on any verification failure.
     */
    suspend fun download(onProgress: (Int) -> Unit = {}): Downloaded = withContext(Dispatchers.IO) {
        val manifest = fetchManifest(expectedVersionName)
        manifest.validate(hostVersionCode())?.let { reason ->
            error("XMSF runtime manifest rejected: $reason")
        }
        check(manifest.matchesPin(expectedVersionName)) {
            "XMSF runtime manifest version ${manifest.versionName} does not match pinned $expectedVersionName"
        }
        val dir = File(appContext.filesDir, "xmsf-runtime/$expectedVersionName").apply { mkdirs() }
        val apk = dir.resolve(APK_NAME)
        val sidecar = dir.resolve("$APK_NAME.sha256")
        if (apk.isFile && apk.length() > 0L && verifyAgainstManifest(apk, manifest)) {
            onProgress(PROGRESS_COMPLETE)
            return@withContext Downloaded(apk, manifest)
        }
        apk.delete()
        sidecar.delete()
        downloadFile("$baseUrl/$expectedVersionName/$APK_NAME", apk, onProgress)
        val sidecarHash = runCatching { downloadText("$baseUrl/$expectedVersionName/$APK_NAME.sha256") }
            .getOrNull()
            ?.trim()
            ?.substringBefore(' ')
            ?.substringAfterLast(':')
        val expectedHash = sidecarHash?.takeIf { it.isNotBlank() } ?: manifest.sha256
        if (expectedHash.isBlank()) {
            apk.delete()
            error("no checksum published for runtime $expectedVersionName")
        }
        val actualHash = sha256(apk)
        if (!expectedHash.equals(actualHash, ignoreCase = true)) {
            apk.delete()
            error("runtime checksum mismatch")
        }
        Downloaded(apk, manifest)
    }

    /** Fetch and parse the manifest for [versionName]. */
    suspend fun fetchManifest(versionName: String): XmsfRuntimeManifest = withContext(Dispatchers.IO) {
        val text = downloadText("$baseUrl/$versionName/$MANIFEST_NAME")
        XmsfRuntimeManifest.parse(text)
    }

    private fun verifyAgainstManifest(apk: File, manifest: XmsfRuntimeManifest): Boolean {
        if (manifest.sha256.isBlank()) return true
        return manifest.sha256.equals(sha256(apk), ignoreCase = true)
    }

    private fun downloadText(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("manifest download failed: HTTP ${response.code} for $url")
            return response.body.string()
        }
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("runtime download failed: HTTP ${response.code} for $url")
            copyWithProgress(response.body, dest, onProgress)
        }
    }

    private fun copyWithProgress(body: ResponseBody, dest: File, onProgress: (Int) -> Unit) {
        val temp = File(dest.parentFile, "${dest.name}.part")
        val total = body.contentLength()
        var downloaded = 0L
        var lastPercent = -1
        try {
            body.byteStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, PROGRESS_COMPLETE)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            onProgress(PROGRESS_COMPLETE)
        } finally {
            temp.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun hostVersionCode(): Long {
        hostVersionCodeProvider?.let { return it() }
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Public GitLab project (MiPushFramework) hosting the generic package. */
        const val PROJECT_ID = "83955143"
        const val BASE_URL =
            "https://gitlab.com/api/v4/projects/$PROJECT_ID/packages/generic/xmsf-runtime"

        const val APK_NAME = "xmsf-runtime.apk"
        const val MANIFEST_NAME = "xmsf-runtime-manifest.json"

        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 300L
        const val PROGRESS_COMPLETE = 100

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
