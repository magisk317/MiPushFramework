package io.github.magisk317.mipush.utils

/**
 * Default values for remote configuration source.
 * Extracted from ConfigCatalogService to make them available in :core.
 */
object ConfigDefaults {
    const val REMOTE_REPOSITORY = "gitlab:magisk3171/MiPushConfigurations"
    const val REMOTE_BRANCH = "beta"
    const val REMOTE_ACCELERATOR = ""

    /** ANIP (Android Notification Icon Project) repository used by the bundled icon engine. */
    const val ICON_REMOTE_REPOSITORY = "BetterAndroid/android-notification-icon-project"
}
