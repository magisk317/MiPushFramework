package top.trumeet.common.ita

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo

object AccessMode {
    const val USAGE_STATS = 0
    const val ACCESSIBILITY = 1

    @IntDef(USAGE_STATS, ACCESSIBILITY)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode
}
