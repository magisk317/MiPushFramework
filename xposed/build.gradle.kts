import com.android.build.api.variant.LibraryAndroidComponentsExtension
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure

plugins {
    id("mipush.android.library")
}

abstract class GenerateCompatProfilesTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: org.gradle.api.file.RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(inputFile.get().asFile) as? Map<String, Any?>
            ?: throw GradleException("compat profile descriptor must be a JSON object")
        @Suppress("UNCHECKED_CAST")
        val profiles = (root["profiles"] as? List<Map<String, Any?>>)
            ?.sortedBy { it["packageName"] as? String ?: "" }
            ?: throw GradleException("compat profile descriptor must contain a 'profiles' array")

        val seenPackages = LinkedHashSet<String>()
        val outFile = outputDir.file("io/github/magisk317/mipush/hook/fakedevice/compat/GeneratedCompatProfiles.kt").get().asFile
        outFile.parentFile.mkdirs()

        val content = buildString {
            appendLine("package io.github.magisk317.mipush.hook.fakedevice.compat")
            appendLine()
            appendLine("internal object GeneratedCompatProfiles {")
            appendLine("    val profiles: List<ModuleCompatProfile> = listOf(")
            profiles.forEachIndexed { index, raw ->
                val packageName = raw["packageName"] as? String
                    ?: throw GradleException("profile[$index] missing packageName")
                if (!seenPackages.add(packageName)) {
                    throw GradleException("duplicate compat profile for $packageName")
                }
                @Suppress("UNCHECKED_CAST")
                val pipelines = (raw["hookPipelines"] as? List<Any?>)
                    ?.map {
                        val value = it as? String ?: throw GradleException("hookPipelines for $packageName must be strings")
                        "HookPipelineId.$value"
                    }
                    ?.distinct()
                    ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val credentialOverride = raw["credentialOverride"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val allowedProcessSuffixes = (raw["allowedProcessSuffixes"] as? List<Any?>)?.map {
                    it as? String ?: throw GradleException("allowedProcessSuffixes for $packageName must be strings")
                }
                @Suppress("UNCHECKED_CAST")
                val deniedProcessPrefixes = (raw["deniedProcessPrefixes"] as? List<Any?>)?.map {
                    it as? String ?: throw GradleException("deniedProcessPrefixes for $packageName must be strings")
                }

                appendLine("        ModuleCompatProfile(")
                appendLine("            packageName = \"$packageName\",")
                appendLine(
                    if (pipelines.isEmpty()) {
                        "            hookPipelines = emptyList(),"
                    } else {
                        "            hookPipelines = listOf(${pipelines.joinToString()}),"
                    }
                )
                appendLine(
                    if (credentialOverride == null) {
                        "            credentialOverride = null,"
                    } else {
                        val appId = credentialOverride["appId"] as? String
                            ?: throw GradleException("credentialOverride.appId missing for $packageName")
                        val appKey = credentialOverride["appKey"] as? String
                            ?: throw GradleException("credentialOverride.appKey missing for $packageName")
                        "            credentialOverride = ModuleCredential(appId = \"$appId\", appKey = \"$appKey\"),"
                    }
                )
                appendLine(
                    when {
                        allowedProcessSuffixes == null -> "            allowedProcessSuffixes = null,"
                        allowedProcessSuffixes.isEmpty() -> "            allowedProcessSuffixes = emptySet(),"
                        else -> "            allowedProcessSuffixes = setOf(${allowedProcessSuffixes.joinToString { "\"$it\"" }}),"
                    }
                )
                appendLine(
                    when {
                        deniedProcessPrefixes == null -> "            deniedProcessPrefixes = null,"
                        deniedProcessPrefixes.isEmpty() -> "            deniedProcessPrefixes = emptyList(),"
                        else -> "            deniedProcessPrefixes = listOf(${deniedProcessPrefixes.joinToString { "\"$it\"" }}),"
                    }
                )
                append("        )")
                if (index != profiles.lastIndex) {
                    append(',')
                }
                appendLine()
            }
            appendLine("    )")
            appendLine("}")
        }

        outFile.writeText(content)
    }
}

val compatProfilesDescriptor = layout.projectDirectory.file("src/main/compat/compat-profiles.json")
val compatProfilesOutputDir = layout.buildDirectory.dir("generated/source/compatProfiles/main/kotlin")

val generateCompatProfiles = tasks.register<GenerateCompatProfilesTask>("generateCompatProfiles") {
    inputFile.set(compatProfilesDescriptor)
    outputDir.set(compatProfilesOutputDir)
}

android {
    namespace = "io.github.magisk317.mipush.xposed"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.xposed.api)
    implementation(libs.hiddenapibypass)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
}

extensions.configure<LibraryAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(generateCompatProfiles, GenerateCompatProfilesTask::outputDir)
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    dependsOn(generateCompatProfiles)
}
