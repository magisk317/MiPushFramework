package io.github.magisk317.mipush.manager.di

import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.manager.BuildConfig as ManagerBuildConfig
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.uikit.update.AppUpdateSourceSpec
import io.github.magisk317.uikit.update.AppUpdateSources
import io.github.magisk317.uikit.update.CiStagingSpec
import kotlinx.coroutines.flow.first

/**
 * Update endpoints for the manager app, expressed as a single [AppUpdateSourceSpec].
 *
 * Both the silent update checker (settings item + home version rows) and the update controller
 * (dialog-driven install UX) read the channel through ui-kit's `AppUpdateSources`, so the CI/debug
 * and release tracks can never drift apart here or in any other consumer app. What stays in this
 * file is product data only: where this app's artifacts are staged and which build flags decide the
 * default channel.
 */
object ManagerUpdateSources {

    /** GitLab project hosting CI artifacts and releases. */
    const val GITLAB_PROJECT_PATH = "magisk3171/MiPushFramework"

    /** GitHub mirror, only consulted on release builds. */
    const val GITHUB_REPOSITORY = "magisk317/MiPushFramework"

    /** CI track: latest successful pipeline on this branch. */
    const val CI_BRANCH = "beta"
    const val CI_JOB_NAME = "android:build"

    /**
     * Version-keyed staging path: the `{versionName}` segment is resolved from the sidecar below
     * (see `GitLabCiArtifactUpdateSource`), keeping the ci-dist/<package>/<versionName>/ layout
     * symmetric with the XMSF runtime.
     */
    const val CI_ARTIFACT_PATH = "ci-dist/io.github.magisk317.mipush/{versionName}/mipush-ci.apk"

    /** Plain-text sidecar naming the staged build's embedded versionName. */
    const val CI_VERSION_SIDECAR_PATH = "ci-dist/io.github.magisk317.mipush/version.txt"

    /**
     * Staged manifest sidecar whose sha256 gates the OTA download; same version-keyed layout and
     * contract fields as the XMSF runtime manifest.
     */
    const val CI_MANIFEST_PATH =
        "ci-dist/io.github.magisk317.mipush/{versionName}/mipush-ci-manifest.json"

    /** True for debug builds and CI/test version names (the CI track is live). */
    val isCiOrDebugBuild: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    /**
     * The manager's endpoint spec. [preferences] backs the tri-state "detect CI builds" switch, so
     * the channel is decided at probe time; unset falls back to how this build was produced.
     */
    fun spec(preferences: PreferenceRepository): AppUpdateSourceSpec = AppUpdateSourceSpec(
        gitlabProjectPath = GITLAB_PROJECT_PATH,
        githubRepository = GITHUB_REPOSITORY,
        ci = CiStagingSpec(
            artifactPath = CI_ARTIFACT_PATH,
            branch = CI_BRANCH,
            jobName = CI_JOB_NAME,
            versionSidecarPath = CI_VERSION_SIDECAR_PATH,
            manifestPath = CI_MANIFEST_PATH,
        ),
        isPlayFlavor = { ManagerBuildConfig.HAS_BILLING },
        buildProvenance = { isCiOrDebugBuild },
        currentCommitHash = { BuildConfig.GIT_COMMIT },
        trackCiOverride = { preferences.trackCiBuildsOverride.first() },
    )

    /**
     * Blocking channel gate shared by the manager sources and the runtime probe chain, so both
     * targets always follow one track. Update probes invoke it from background dispatchers only.
     */
    fun trackCiGate(preferences: PreferenceRepository): () -> Boolean = {
        AppUpdateSources.resolveTrackCi(spec(preferences), preferences.trackCiBuildsOverrideBlocking())
    }
}
