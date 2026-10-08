import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

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
        maven {
            name = "MagiskMobilePrivate"
            url = uri(
                providers.gradleProperty("mobile.private.maven.url").orNull
                    ?: System.getenv("MOBILE_PRIVATE_MAVEN_URL")
                    ?: "https://gitlab.com/api/v4/projects/85187820/packages/maven",
            )
            val jobToken = System.getenv("CI_JOB_TOKEN")
            val deployToken = System.getenv("GITLAB_DEPLOY_TOKEN")
                ?: System.getenv("GITLAB_TOKEN")
            if (!jobToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = jobToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            } else if (!deployToken.isNullOrBlank()) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Deploy-Token"
                    value = deployToken
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
            content {
                includeGroup("com.magisk317.mobile")
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
    ":magisk-xposed-kit",
    ":magisk-xposed-kit:logging",
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
)

// Explicitly remap moved physical paths
project(":xmsf:runtime").projectDir = file("xmsf/runtime")
project(":magisk-ui-kit:billing").projectDir = file("magisk-ui-kit/billing")
project(":magisk-xposed-kit:logging").projectDir = file("magisk-xposed-kit/logging")
project(":magisk-xposed-kit:diagnostics").projectDir = file("magisk-xposed-kit/diagnostics")
project(":magisk-xposed-kit:permission").projectDir = file("magisk-xposed-kit/permission")
project(":manager:ui").projectDir = file("manager/ui")
project(":manager:port").projectDir = file("manager/port")
project(":manager:contract").projectDir = file("manager/contract")
project(":manager:client").projectDir = file("manager/client")
project(":xmsf:shell").projectDir = file("xmsf/shell")
project(":xmsf:notification").projectDir = file("xmsf/notification")
project(":xmsf:runtime:store").projectDir = file("xmsf/runtime/store")
project(":xmsf:push").projectDir = file("xmsf/push")
