package com.xiaomi.channel.commonutils.android

import android.content.SharedPreferences
import android.os.Build

/*
 */
object SharedPrefsCompat {
    @JvmStatic
    fun apply(editor: SharedPreferences.Editor) {
        if (Build.VERSION.SDK_INT > 8) {
            editor.apply()
        } else {
            editor.commit()
        }
    }
}
