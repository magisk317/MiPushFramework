package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Shared "install the runtime yourself" path, used by the play flavor as its
 * only delivery route and by the github flavor as the manual fallback when
 * the in-process install fails.
 *
 * The link deliberately points at the release *page*, never at an APK direct
 * URL: the play distribution must not be seen handing the user an APK file.
 */
object XmsfGuidance {

    const val RELEASE_PAGE_URL = "https://github.com/magisk317/MiPushFramework/releases"

    /** Open the release listing in the browser (or a chooser). */
    fun openReleasePage(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No browser available to open $RELEASE_PAGE_URL", Toast.LENGTH_LONG).show()
            false
        }
    }
}
