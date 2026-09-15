package com.xiaomi.smack

import com.xiaomi.channel.commonutils.network.NameValuePair

/*
 * Stock 7.4.67-C has no separate qa.* counterpart; this file keeps the deobfuscated compatibility API.
 */
interface HttpRequestProxy {
    fun doHttpGet(url: String, params: List<NameValuePair>?): String?

    fun doHttpPost(url: String, params: List<NameValuePair>?): String?

    fun isWapNetwork(): Boolean
}
