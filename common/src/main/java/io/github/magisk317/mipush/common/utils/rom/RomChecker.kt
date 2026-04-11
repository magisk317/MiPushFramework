package io.github.magisk317.mipush.common.utils.rom

import androidx.annotation.RestrictTo

/**
 * Created by Trumeet on 2018/4/22.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
interface RomChecker {
    fun check(): Boolean
}
