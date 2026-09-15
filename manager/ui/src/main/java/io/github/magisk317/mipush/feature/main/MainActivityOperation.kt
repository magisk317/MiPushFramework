package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

class MainActivityOperation(private val context: Context) {
    private fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(url.toUri())
        )
    }

    fun gotoTelegramGroup() {
        openUrl("https://t.me/+NR2QaQ4dlEgxYmNl")
    }

    fun gotoGitLabProjectPage() {
        openUrl("https://gitlab.com/magisk3171/MiPushFramework")
    }
}
