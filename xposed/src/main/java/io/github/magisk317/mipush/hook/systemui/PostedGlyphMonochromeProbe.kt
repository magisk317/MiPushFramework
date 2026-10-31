package io.github.magisk317.mipush.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import io.github.magisk317.xposed.callMethod

/**
 * Shared verdict on whether a posted notification glyph is already single-color.
 *
 * The status bar and the notification center must reach the same conclusion from the same
 * evidence, otherwise one surface tints the app's own glyph while the other synthesizes a
 * silhouette from its launcher icon and the two visibly disagree.
 */
internal object PostedGlyphMonochromeProbe {

    /** Lenient variant: an unknown glyph is assumed grayscale so a RESOURCE icon is never
     * force-replaced by a launcher bitmap. */
    @SuppressLint("PrivateApi")
    fun isGrayscale(context: Context, icon: Icon?): Boolean {
        if (icon == null) return true
        return detect(context, icon) ?: true
    }

    /** Strict variant: only a positive verdict justifies taking over the glyph. */
    @SuppressLint("PrivateApi")
    fun isPositivelyGrayscale(context: Context, icon: Icon?): Boolean {
        if (icon == null) return false
        return detect(context, icon) ?: false
    }

    /**
     * Reads (type, resId, resPackage) from an [Icon].
     *
     * `getType()` is public; `getResId()`/`getResPackage()` are hidden and read reflectively. A
     * null icon yields a non-RESOURCE marker so callers treat it as nothing reusable.
     */
    fun resourceFields(icon: Icon?): Triple<Int, Int, String?> {
        if (icon == null) return Triple(-1, 0, null)
        val type = runCatching { icon.type }.getOrDefault(-1)
        if (type != SystemUiNotificationPolicy.ICON_TYPE_RESOURCE) return Triple(type, 0, null)
        val resId = runCatching { icon.callMethod("getResId") as? Int }.getOrNull() ?: 0
        val resPackage = runCatching { icon.callMethod("getResPackage") as? String }.getOrNull()
        return Triple(type, resId, resPackage)
    }

    /**
     * Whether the posted glyph is unusable rather than merely colorful: a `resId=0` summary or the
     * framework's generic auto-group mark. The status bar replaces these; the shade must too, or a
     * merged bundle keeps MIUI's colored app header while its own row renders monochrome.
     */
    fun isUnusableGlyph(icon: Icon?, notificationFlags: Int): Boolean {
        val (iconType, resId, resPackage) = resourceFields(icon)
        return SystemUiNotificationPolicy.shouldReplaceBrokenResourceSmallIcon(
            iconType = iconType,
            resId = resId,
            resPackage = resPackage,
            notificationFlags = notificationFlags,
        )
    }

    /** @return the grayscale verdict, or null when detection is impossible. */
    @SuppressLint("PrivateApi")
    fun detect(context: Context, icon: Icon): Boolean? = runCatching {
        val utilClass = Class.forName("com.android.internal.util.ContrastColorUtil")
        val instance = utilClass.getMethod("getInstance", Context::class.java).invoke(null, context)
        if (icon.type == Icon.TYPE_RESOURCE) {
            // The (Context, Icon) overload resolves the bare resId against SystemUI's
            // resources, losing the owning package and misreading app icons. Load through
            // the Icon so the correct package drawable is inspected.
            val drawable = icon.loadDrawable(context) ?: return@runCatching null
            utilClass
                .getMethod("isGrayscaleIcon", Drawable::class.java)
                .invoke(instance, drawable) as? Boolean
        } else {
            utilClass
                .getMethod("isGrayscaleIcon", Context::class.java, Icon::class.java)
                .invoke(instance, context, icon) as? Boolean
        }
    }.getOrNull()
}
