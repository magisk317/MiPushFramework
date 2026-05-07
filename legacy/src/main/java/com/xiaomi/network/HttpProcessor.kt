package com.xiaomi.network

import android.content.Context
import com.xiaomi.channel.commonutils.network.NameValuePair
import java.io.IOException
import kotlin.jvm.JvmSuppressWildcards

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/network/HttpProcessor.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
abstract class HttpProcessor(
    val requestType: Int
) {
    @Throws(IOException::class)
    open fun prepare(context: Context, url: String, params: List<@JvmSuppressWildcards NameValuePair>?): Boolean = true

    @Throws(IOException::class)
    abstract fun visit(context: Context, url: String, params: List<@JvmSuppressWildcards NameValuePair>?): String?

    companion object {
        const val HTTP_GET = 1
        const val HTTP_POST = 2
    }
}
