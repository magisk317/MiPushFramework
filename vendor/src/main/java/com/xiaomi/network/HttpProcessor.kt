package com.xiaomi.network

import android.content.Context
import com.xiaomi.channel.commonutils.network.NameValuePair
import java.io.IOException
import kotlin.jvm.JvmSuppressWildcards

/*
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
