import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure

plugins {
    id("mipush.android.library")
    id("mipush.android.room")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

abstract class GenerateMiPushPropTemplateTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: org.gradle.api.file.RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @TaskAction
    fun generate() {
        val input = inputFile.get().asFile
        if (!input.exists()) {
            throw GradleException("mipush prop template missing: ${input.absolutePath}")
        }

        val customProps = linkedMapOf<String, String>()
        var inCustomProps = false
        input.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                inCustomProps = line.endsWith(".custom_props]")
                return@forEach
            }
            if (!inCustomProps) return@forEach

            val match = Regex("^\"([^\"]+)\"\\s*=\\s*\"(.*)\"$").matchEntire(line)
                ?: throw GradleException("Unsupported mipush_prop.toml entry: $line")
            customProps[match.groupValues[1]] = match.groupValues[2]
        }

        val outFile = outputDir.file(
            "io/github/magisk317/mipush/common/fakedevice/GeneratedMiPushPropTemplate.kt"
        ).get().asFile
        outFile.parentFile.mkdirs()
        val body = customProps.entries.joinToString(",\n") { (key, value) ->
            "        \"${escape(key)}\" to \"${escape(value)}\""
        }
        outFile.writeText(
            buildString {
                appendLine("package io.github.magisk317.mipush.common.fakedevice")
                appendLine()
                appendLine("internal object GeneratedMiPushPropTemplate {")
                appendLine("    val customProps: Map<String, String> = linkedMapOf(")
                appendLine(body)
                appendLine("    )")
                appendLine("}")
            }
        )
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}

val mipushPropTemplateInput = layout.projectDirectory.file("src/main/fakedevice/mipush_prop.toml")
val mipushPropTemplateOutputDir = layout.buildDirectory.dir("generated/source/mipushPropTemplate/main/kotlin")

val generateMiPushPropTemplate = tasks.register<GenerateMiPushPropTemplateTask>("generateMiPushPropTemplate") {
    inputFile.set(mipushPropTemplateInput)
    outputDir.set(mipushPropTemplateOutputDir)
}

android {
    namespace = "io.github.magisk317.mipush.common"

    defaultConfig {
        @Suppress("UNCHECKED_CAST")
        val gitVersionName = (rootProject.extra["gitVersionName"] as Provider<String>).get()
        @Suppress("UNCHECKED_CAST")
        val gitVersionCode = (rootProject.extra["gitVersionCode"] as Provider<Int>).get()

        buildConfigField("String", "APPLICATION_ID", "\"${rootProject.extra["APPLICATION_ID"]}\"")
        buildConfigField("String", "VERSION_NAME", "\"$gitVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$gitVersionCode")
        buildConfigField("String", "PUSH_VERSION_CODE", "\"${libs.versions.pushVersionCode.get()}\"")
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
    implementation(project(":pinned"))
    compileOnly(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)
    implementation(libs.napier)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

extensions.configure<LibraryAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateMiPushPropTemplate,
            GenerateMiPushPropTemplateTask::outputDir
        )
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    dependsOn(generateMiPushPropTemplate)
}
