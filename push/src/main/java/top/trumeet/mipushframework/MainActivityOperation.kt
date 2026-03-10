package top.trumeet.mipushframework

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import top.trumeet.mipushframework.main.HelpPage

class MainActivityOperation(private val context: Context) {
    fun gotoHelpActivity() {
        val intent = Intent()
        intent.setClass(context, HelpPage::class.java)
        context.startActivity(intent)
    }

    fun showAboutDialog(onShow: (String) -> Unit) {
        val versionInfo = String.format(
            "name: %s\ncode: %d\nflavor: %s\ntype: %s",
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.FLAVOR,
            BuildConfig.BUILD_TYPE
        )
        onShow(versionInfo)
    }

    fun gotoGitHubReleasePage() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://github.com/magisk317/MiPushFramework/releases"))
        )
    }
}
