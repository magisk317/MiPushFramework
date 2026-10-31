package io.github.magisk317.mipush.manager.xmsfdelivery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Server-published description of one XMSF runtime APK, fetched by the github
 * flavor before any download is trusted.
 *
 * The manifest travels next to the APK in the GitLab generic package registry
 * (`xmsf-runtime[-<variant>]/<versionName>/`). The host was built against a pinned
 * runtime version, so the manifest must describe exactly that artifact:
 * [validate] gates the contract fields against the running host and the caller
 * additionally pins [versionName] to the host build. Unknown fields are
 * ignored so the publish side can evolve without breaking old hosts -- which is
 * why [commit] was added without a schema bump.
 *
 * [commit] is diagnostic only and takes part in no gate: a checksum says the
 * bytes survived the download, and the version name says the artifact is the
 * one this build was pinned against, but neither says which source tree the
 * artifact came from. Naming the commit lets a user match a runtime to a
 * manager without unpacking either APK.
 */
@Serializable
data class XmsfRuntimeManifest(
    val schema: Int = 0,
    val variant: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val minManagerVersionCode: Long = 0L,
    val sha256: String = "",
    /** Git commit this runtime was built from; empty when the publisher did not declare it. */
    val commit: String = "",
) {

    /**
     * Contract gates against the running host. Returns null when the manifest
     * is acceptable, otherwise the reason (surfaced to the user).
     *
     * [flavor] additionally pins the payload to one runtime variant: the two
     * tiers share a versionName, so [versionCode] is the only field that can
     * tell them apart, and installing across tiers silently moves the version
     * code apps compare against.
     */
    fun validate(hostVersionCode: Long, flavor: XmsfRuntimeFlavor? = null): String? = when {
        schema != SCHEMA -> "manifest schema $schema does not match host contract $SCHEMA"
        variant != VARIANT -> "manifest variant \"$variant\" does not match host variant \"$VARIANT\""
        versionName.isBlank() -> "manifest versionName is blank"
        versionCode <= 0L -> "manifest versionCode is not positive"
        flavor != null && versionCode != flavor.versionCode ->
            "manifest versionCode $versionCode is not the ${flavor.label} variant (${flavor.versionCode})"
        minManagerVersionCode > hostVersionCode ->
            "runtime requires manager versionCode >= $minManagerVersionCode, host is $hostVersionCode"
        else -> null
    }

    /** The runtime must not be newer than what this host build knows about. */
    fun matchesPin(expectedVersionName: String): Boolean =
        versionName.isNotBlank() && versionName == expectedVersionName

    companion object {
        /** Manifest contract revision; bump on incompatible field changes. */
        const val SCHEMA = 1

        /** Only the github distribution downloads the runtime in-process. */
        const val VARIANT = "github"

        val json: Json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): XmsfRuntimeManifest = json.decodeFromString(serializer(), text)
    }
}
