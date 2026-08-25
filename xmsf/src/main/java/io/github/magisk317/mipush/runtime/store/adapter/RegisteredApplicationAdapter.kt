package io.github.magisk317.mipush.runtime.store.adapter

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow

/**
 * Android-specific extension functions and constant objects for
 * [RuntimeRegisteredApplicationRow].
 *
 * These were previously inlined on the production [RegisteredApplication] Room
 * entity.  After the KMP split, the pure data fields live in
 * [RuntimeRegisteredApplicationRow] while all Android-dependent logic (Context,
 * PackageManager, Parcelable) is collected here.
 */

// ── Type constants ──────────────────────────────────────────────────────────

/**
 * Mirrors the values formerly declared inside `RegisteredApplication.Type.Companion`.
 */
object RegisteredAppType {
    const val ASK = 0
    const val ALLOW = 2
    const val DENY = 3
    const val ALLOW_ONCE = -1
}

// ── RegisteredType constants ────────────────────────────────────────────────

/**
 * Mirrors the values formerly declared inside
 * `RegisteredApplication.RegisteredType.Companion`.
 */
object RegisteredAppRegisteredType {
    const val NotRegistered = 0
    const val Registered = 1
    const val Unregistered = 2
}

// ── Context-dependent helpers ───────────────────────────────────────────────

/**
 * Loads the application icon via [PackageManagerCompatBridge].
 *
 * Falls back to the system default app icon when the package is not found or
 * its resources are unavailable.
 */
fun RuntimeRegisteredApplicationRow.getIcon(context: Context): Drawable {
    val pm = context.packageManager
    return try {
        PackageManagerCompatBridge.getApplicationInfo(
            pm,
            packageName,
            PackageManager.MATCH_UNINSTALLED_PACKAGES,
        ).loadIcon(pm)
    } catch (_: PackageManager.NameNotFoundException) {
        ContextCompat.getDrawable(context, android.R.mipmap.sym_def_app_icon)!!
    } catch (_: Resources.NotFoundException) {
        ContextCompat.getDrawable(context, android.R.mipmap.sym_def_app_icon)!!
    }
}

/**
 * Returns the UID for the application's package, or `-1` if the package is
 * not found.
 */
fun RuntimeRegisteredApplicationRow.getUid(context: Context): Int {
    return try {
        PackageManagerCompatBridge.getApplicationInfo(
            context.packageManager,
            packageName,
            PackageManager.MATCH_UNINSTALLED_PACKAGES,
        ).uid
    } catch (_: PackageManager.NameNotFoundException) {
        -1
    }
}
