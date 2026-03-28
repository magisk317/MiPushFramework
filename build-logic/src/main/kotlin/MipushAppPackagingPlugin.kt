import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class MipushAppPackagingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val releaseVersionName = project.providers.provider {
                val android = project.extensions.getByType<ApplicationExtension>()
                android.defaultConfig.versionName
                    ?: project.rootProject.version.toString().ifBlank { project.version("versionName") }
            }
            val debugBuildTimestamp = buildTimestamp(project)
            val isBundleTask = project.gradle.startParameter.taskNames.any {
                it.contains("bundle", ignoreCase = true)
            }

            project.extensions.configure<ApplicationExtension> {
                splits {
                    abi {
                        isEnable = project.hasProperty("buildSplits") && !isBundleTask
                        reset()
                        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                        isUniversalApk = true
                    }
                }
            }

            project.extensions.getByType<ApplicationAndroidComponentsExtension>().apply {
                onVariants(selector().all()) { variant ->
                    val isDebug = variant.buildType == "debug"
                    val resolvedVersionName = if (isDebug) {
                        "${releaseVersionName.get()}-$debugBuildTimestamp"
                    } else {
                        releaseVersionName.get()
                    }

                    variant.outputs.forEach { output ->
                        if (isDebug) {
                            output.versionName.set(resolvedVersionName)
                        }

                        val abi = output.filters.find {
                            it.filterType == FilterConfiguration.FilterType.ABI
                        }?.identifier ?: "universal"

                        try {
                            val outputFileName = output.javaClass.getMethod("getOutputFileName").invoke(output)
                            outputFileName.javaClass
                                .getMethod("set", Any::class.java)
                                .invoke(
                                    outputFileName,
                                    releaseApkName(
                                        versionName = resolvedVersionName,
                                        buildType = variant.buildType ?: "",
                                        abiSuffix = abi,
                                        project = project,
                                    ),
                                )
                        } catch (_: Exception) {
                            // Keep the build tolerant across AGP preview API changes.
                        }
                    }
                }
            }

            val renameReleaseBundleTask = "renameReleaseAab"
            project.tasks.register(renameReleaseBundleTask) {
                dependsOn("bundleRelease")
                val bundleFileProvider = project.layout.buildDirectory.file(
                    "outputs/bundle/release/push-release.aab",
                )
                val targetFileProvider = project.layout.buildDirectory.file(
                    "outputs/bundle/release/${releaseAabName(releaseVersionName.get(), project)}",
                )
                doLast {
                    val bundleFile = bundleFileProvider.get().asFile
                    if (bundleFile.exists()) {
                        bundleFile.copyTo(targetFileProvider.get().asFile, overwrite = true)
                    }
                }
            }

            project.tasks.matching { it.name == "bundleRelease" }.configureEach {
                finalizedBy(renameReleaseBundleTask)
            }
        }
    }

    private fun buildTimestampOverride(project: Project): String? = project.findProperty("buildTs")
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun buildTimestamp(project: Project): String = buildTimestampOverride(project)
        ?: SimpleDateFormat("yyyyMMdd_HHmmss").apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

    private fun releaseTime(): String = SimpleDateFormat("yyMMdd").apply {
        timeZone = TimeZone.getDefault()
    }.format(Date())

    private fun releaseBaseName(versionName: String, project: Project): String {
        val normalizedVersionName = versionName.replace("\\s+".toRegex(), "_")
        val alreadyHasBuildTimestamp = versionName.matches(Regex(".*-\\d{8}(?:_\\d{6}|\\d{6})$"))
        if (alreadyHasBuildTimestamp) {
            return "MiPushFramework_v$normalizedVersionName"
        }
        val suffix = buildTimestampOverride(project) ?: releaseTime()
        return "MiPushFramework_v${normalizedVersionName}_$suffix"
    }

    private fun releaseApkName(
        versionName: String,
        buildType: String,
        abiSuffix: String,
        project: Project,
    ): String {
        return "${abiSuffix}_${releaseBaseName(versionName, project)}_${buildType}.apk"
    }

    private fun releaseAabName(versionName: String, project: Project): String {
        return "${releaseBaseName(versionName, project)}_release.aab"
    }
}
