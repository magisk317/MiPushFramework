package io.github.magisk317.mipush.feature.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import io.github.magisk317.mipush.manager.R

/**
 * Store/GitHub fallback for the Play in-app update chain: open the Play listing when the
 * market app is present, otherwise the GitHub latest-release page.
 */
internal object UpdateFallback {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://github.com/magisk317/MiPushFramework/releases/latest"

    fun openPlayStoreOrGithub(context: Context): Boolean {
        if (isPlayStoreAvailable(context)) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return true
            } catch (_: ActivityNotFoundException) {
                // fall through to the browser
            }
        }
        return openGithubRelease(context)
    }

    private fun openGithubRelease(context: Context): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_LATEST_RELEASE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.open_browser_failed, Toast.LENGTH_SHORT).show()
        false
    }

    private fun isPlayStoreAvailable(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.android.vending", 0) != null
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
