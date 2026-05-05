package com.xiaomi.channel.commonutils.android

import android.content.SharedPreferences
import android.os.Build

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/SharedPrefsCompat.java
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
