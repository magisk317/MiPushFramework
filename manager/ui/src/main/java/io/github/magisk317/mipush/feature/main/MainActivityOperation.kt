package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_CODE

class MainActivityOperation(private val context: Context) {
    private fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(url))
        )
    }

    fun showAboutDialog(onShow: (String) -> Unit) {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName?.takeIf { it.isNotBlank() }
            ?: BuildConfig.VERSION_NAME
        val versionCode = packageInfo?.longVersionCode ?: VERSION_CODE.toLong()
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

    fun gotoTelegramGroup() {
        openUrl("https://t.me/+NR2QaQ4dlEgxYmNl")
    }

    fun gotoGitLabProjectPage() {
        openUrl("https://gitlab.com/magisk3171/MiPushFramework")
    }
}
