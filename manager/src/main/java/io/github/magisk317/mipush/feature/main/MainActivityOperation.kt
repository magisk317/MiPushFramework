package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import java.util.Locale
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_CODE
import io.github.magisk317.mipush.platform.support.LegacyComponentNames

class MainActivityOperation(private val context: Context) {
    private fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(url))
        )
    }

    fun gotoHelpActivity() {
        // HelpPage activity class was removed from sources; keep same-package component target
        // for any remaining menu wiring until a replacement surface lands.
        context.startActivity(
            Intent().setClassName(context, LegacyComponentNames.HELP_PAGE),
        )
    }

    fun showAboutDialog(onShow: (String) -> Unit) {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName?.takeIf { it.isNotBlank() }
            ?: BuildConfig.VERSION_NAME
        val versionCode = packageInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                it.versionCode.toLong()
            }
        } ?: VERSION_CODE.toLong()
        val versionInfo = String.format(
            Locale.US,
            "name: %s\ncode: %d\nbuildConfigName: %s\nbuildConfigCode: %d\ngitCommit: %s\ntype: %s",
            versionName,
            versionCode,
            BuildConfig.VERSION_NAME,
            VERSION_CODE,
            BuildConfig.GIT_COMMIT,
            BuildConfig.BUILD_TYPE
        )
        onShow(versionInfo)
    }

    fun gotoGitHubReleasePage() {
        openUrl("https://github.com/magisk317/MiPushFramework/releases")
    }

    fun gotoTelegramGroup() {
        openUrl("https://t.me/+NR2QaQ4dlEgxYmNl")
    }

    fun gotoQQGroup() {
        openUrl("https://qm.qq.com/q/PaFGVEb6so")
    }

    fun gotoGitHubProjectPage() {
        openUrl("https://github.com/magisk317/MiPushFramework")
    }
}
