package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.VERSION_CODE
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints

class MainActivityOperation(private val context: Context) {
    private fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(url))
        )
    }

    fun gotoHelpActivity() {
        context.startActivity(LegacyUiEntryPoints.helpPageIntent(context))
    }

    fun showAboutDialog(onShow: (String) -> Unit) {
        val versionInfo = String.format(
            Locale.US,
            "name: %s\ncode: %d\nchannel: %s\ntype: %s",
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            "single",
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
