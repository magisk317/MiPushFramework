@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import top.trumeet.mipushframework.main.HelpPage

class MainPageOperation(private val context: Context) {
    fun gotoHelpActivity() {
        val intent = Intent()
        intent.setClass(context, HelpPage::class.java)
        context.startActivity(intent)
    }

    fun showAboutDialog() {
        val versionInfo = String.format(
            "name: %s\ncode: %d\nflavor: %s\ntype: %s",
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            BuildConfig.FLAVOR,
            BuildConfig.BUILD_TYPE
        )
        val build = AlertDialog.Builder(context)
            .setView(R.layout.dialog_about)
            .setPositiveButton("Copy") { _, _ ->
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.text = versionInfo
            }
        val content = build.show().findViewById<TextView>(R.id.text_version)
        content?.text = versionInfo
    }

    fun gotoGitHubReleasePage() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://github.com/NihilityT/MiPushFramework/releases"))
        )
    }
}
