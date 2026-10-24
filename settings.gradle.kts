pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "MiPushFramework"
requireExistingProjectDir("magisk-ui-kit")
requireExistingProjectDir("magisk-xposed-kit")
requireExistingProjectDir("vendor")
requireExistingProjectDir("pinned")
// Shared submodules first, so the remap block below reads in the same order as
// xinyi-relay and XposedSmsCode: submodules, then host modules, then paths.
include(
    ":magisk-ui-kit",
    ":magisk-ui-kit:billing",
    ":magisk-ui-kit:update",
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:logging",
    ":magisk-xposed-kit:logging-core",
    ":magisk-xposed-kit:diagnostics",
    ":magisk-xposed-kit:permission",
)

include(
    ":xmsf",
    ":xmsf:platform",
    ":xmsf:shell",
    ":xmsf:notification",
    ":xmsf:push",
    ":xmsf:runtime",
    ":xmsf:runtime:store",
    ":mipush",
    ":xposed",
    ":common",
    ":core",
    ":settings",
    ":configuration",
    ":vendor",
    ":pinned",
    ":manager:contract",
    ":manager:port",
    ":manager:application",
    ":manager:client",
    ":manager:ui",
    ":manager:xmsfDelivery",
)

// Explicitly remap moved physical paths
project(":xmsf:runtime").projectDir = file("xmsf/runtime")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
project(":magisk-ui-kit:update").projectDir = file("magisk-ui-kit/update")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:logging-core").projectDir = file("magisk-xposed-kit/logging-core")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":manager:ui").projectDir = file("manager/ui")
project(":manager:xmsfDelivery").projectDir = file("manager/xmsfDelivery")
project(":manager:port").projectDir = file("manager/port")
project(":manager:contract").projectDir = file("manager/contract")
project(":manager:client").projectDir = file("manager/client")
project(":xmsf:shell").projectDir = file("xmsf/shell")
project(":xmsf:notification").projectDir = file("xmsf/notification")
project(":xmsf:runtime:store").projectDir = file("xmsf/runtime/store")
project(":xmsf:push").projectDir = file("xmsf/push")
