pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.greenrobot.greendao") {
                useModule("org.greenrobot:greendao-gradle-plugin:${requested.version}")
            }
        }
    }
}

fun requireExistingProjectDir(path: String) {
    val dir = file(path)
    check(dir.isDirectory) {
        buildString {
            appendLine("Missing required project directory: $path")
            appendLine("This repository uses git submodules. Run:")
            appendLine("  git submodule update --init --recursive")
        }
    }
}

buildscript {
    val securityOverrides: Map<String, String> = run {
        val propsFile = file("gradle/security-overrides.properties")
        if (!propsFile.exists()) {
            emptyMap()
        } else {
            val props = java.util.Properties()
            propsFile.reader().use { reader -> props.load(reader) }
            props.stringPropertyNames().associateWith { name -> props.getProperty(name) }
        }
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val key = "${requested.group}:${requested.name}"
            val forcedVersion = securityOverrides[key]
            if (!forcedVersion.isNullOrBlank() && requested.version != forcedVersion) {
                useVersion(forcedVersion)
                because("Security override from gradle/security-overrides.properties")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MiPushFramework"
requireExistingProjectDir("uikit")
requireExistingProjectDir("legacy")
requireExistingProjectDir("protocol")
requireExistingProjectDir("pinned")
include(":xmsf", ":mipush", ":xposed", ":common", ":core", ":uikit", ":legacy", ":pinned", ":protocol")
project(":uikit").projectDir = file("uikit")
