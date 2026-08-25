pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MiPushFramework"
requireExistingProjectDir("magisk-ui-kit")
requireExistingProjectDir("magisk-xposed-kit")
requireExistingProjectDir("vendor")
requireExistingProjectDir("pinned")
include(":xmsf", ":xmsf-notification", ":mipush", ":xposed", ":common", ":core", ":settings", ":configuration", ":diagnostics", ":magisk-ui-kit", ":magisk-ui-kit:billing", ":magisk-xposed-kit", ":magisk-xposed-kit:logging", ":magisk-xposed-kit:diagnostics", ":vendor", ":pinned", ":manager-api", ":manager-client", ":manager", ":app", ":runtime-store-kmp")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
