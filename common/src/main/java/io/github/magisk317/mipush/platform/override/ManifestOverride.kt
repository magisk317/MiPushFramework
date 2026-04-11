package io.github.magisk317.mipush.platform.override

/**
 * Created by Trumeet on 2018/2/5.
 */
object ManifestOverride {
    object Permission {
        const val GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS"
    }

    // Java compatibility with legacy reference: ManifestOverride.permission.GET_APP_OPS_STATS
    object permission {
        const val GET_APP_OPS_STATS = Permission.GET_APP_OPS_STATS
    }
}
