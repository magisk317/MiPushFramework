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
include(":xmsf:shell", ":xmsf:notification", ":mipush", ":xposed", ":common", ":core", ":settings", ":configuration", ":diagnostics", ":magisk-ui-kit", ":magisk-ui-kit:billing", ":magisk-xposed-kit", ":magisk-xposed-kit:logging", ":magisk-xposed-kit:diagnostics", ":vendor", ":pinned", ":manager:contract", ":manager:client", ":manager:ui", ":app", ":xmsf:runtime", ":xmsf:runtime:store")
project(":xmsf:runtime").projectDir = file("xmsf/runtime")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":manager:ui").projectDir = file("manager/ui")
project(":manager:contract").projectDir = file("manager/contract")
project(":manager:client").projectDir = file("manager/client")
project(":xmsf:shell").projectDir = file("xmsf/shell")
project(":xmsf:notification").projectDir = file("xmsf/notification")
project(":xmsf:runtime:store").projectDir = file("xmsf/runtime/store")
