package io.github.magisk317.mipush.hook.util

import android.content.Context
import io.github.magisk317.mipush.xposed.callMethod

fun Context.getUserId(): Int {
    return callMethod("getUserId") as Int? ?: 0
}