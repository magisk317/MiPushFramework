package io.github.magisk317.mipush.manager.di

import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_NAME
import io.github.magisk317.uikit.update.GitHubReleaseUpdateSource
import io.github.magisk317.uikit.update.GitLabCiArtifactUpdateSource
import io.github.magisk317.uikit.update.GitLabReleaseUpdateSource
import io.github.magisk317.uikit.update.UpdateSource

/**
 * Update endpoints for the manager app, defined in exactly one place.
 *
 * Both the silent update checker (settings item + home version rows) and the
 * update controller (dialog-driven install UX) build their source list from
 * here, so the CI/debug and release tracks can never drift apart.
 */
object ManagerUpdateSources {

    /** GitLab project hosting CI artifacts and releases. */
    const val GITLAB_PROJECT_PATH = "magisk3171/MiPushFramework"

    /** GitHub mirror, only consulted on release builds. */
    const val GITHUB_REPOSITORY = "magisk317/MiPushFramework"

    /** CI track: latest successful pipeline on this branch. */
    const val CI_BRANCH = "beta"
    const val CI_JOB_NAME = "android:build"
    const val CI_ARTIFACT_PATH = "ci-dist/io.github.magisk317.mipush/mipush-ci.apk"

    /** True for debug builds and CI/test version names (the CI track is live). */
    val isCiOrDebugBuild: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    /**
     * Ordered source list for the current build type. CI/debug builds see the
     * CI artifact track first (force update), release builds see GitLab
     * releases first and the GitHub mirror second.
     */
    fun create(gitCommit: String = BuildConfig.GIT_COMMIT): List<UpdateSource> =
        if (isCiOrDebugBuild) {
            listOf(
                GitLabCiArtifactUpdateSource(
                    projectPath = GITLAB_PROJECT_PATH,
                    branch = CI_BRANCH,
                    jobName = CI_JOB_NAME,
                    artifactPath = CI_ARTIFACT_PATH,
                    versionFilePath = "ci-dist/io.github.magisk317.mipush/version.txt",
                    currentCommitHash = gitCommit,
                ),
                GitLabReleaseUpdateSource(GITLAB_PROJECT_PATH),
            )
        } else {
            listOf(
                GitLabReleaseUpdateSource(GITLAB_PROJECT_PATH),
                GitHubReleaseUpdateSource(
                    repository = GITHUB_REPOSITORY,
                    gitlabProjectPath = GITLAB_PROJECT_PATH,
                ),
            )
        }
}
