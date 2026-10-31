package io.github.magisk317.mipush.hook.systemui

import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.widget.ImageView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Registry of notification-center header icons this module has taken over.
 *
 * A claimed header icon is not safe to treat as settled: the posting app re-applies its own
 * RemoteViews content asynchronously (`RemoteViews$AsyncApplyTask.onPostExecute` straight into
 * `ImageView.setImageDrawable`), which lands *after* our replacement and puts the colorful
 * launcher icon back into the very same view. That write is what the user sees as flicker.
 *
 * A claim is therefore only a promise to defend the view until the next bind: every
 * `applyAppIconAllowCustom` call starts a fresh bind and drops the previous claim, so a recycled
 * row can never be pinned to another notification's glyph. Weak keys keep a detached row from
 * being retained.
 */
internal object ShadeHeaderIconClaims {

    /** The glyph a view was given, plus the tint it was given with, so a restore is identical. */
    internal class Claim(val icon: Icon, val tint: ColorStateList)

    private val claims: MutableMap<ImageView, Claim> =
        Collections.synchronizedMap(WeakHashMap<ImageView, Claim>())

    /** Raised while this module writes, so its own write cannot be mistaken for an overwrite. */
    private val restoring = ThreadLocal<Boolean>()

    val isRestoring: Boolean get() = restoring.get() == true

    fun claim(view: ImageView, icon: Icon, tint: ColorStateList) {
        claims[view] = Claim(icon, tint)
    }

    /** Called when a new bind starts: whatever was claimed for this view is now stale. */
    fun invalidate(view: ImageView) {
        claims.remove(view)
    }

    fun claimFor(view: ImageView): Claim? = claims[view]

    /** Runs [action] with the restore guard raised. */
    fun <T> restoring(action: () -> T): T {
        val previous = restoring.get()
        restoring.set(true)
        return try {
            action()
        } finally {
            restoring.set(previous)
        }
    }
}
